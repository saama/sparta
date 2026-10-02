package com.domain.booking.service;

import com.domain.booking.dto.request.BookingCancelRequest;
import com.domain.booking.dto.request.BookingCreateRequest;
import com.domain.booking.dto.response.BookingResponse;
import com.domain.booking.entity.Booking;
import com.domain.booking.event.BookingCreatedEvent;
import com.domain.booking.repository.BookingRepository;
import com.domain.coupon.entity.UserCoupon;
import com.domain.coupon.repository.UserCouponRepository;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomStock;
import com.domain.room.repository.RoomProductRepository;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

  private final BookingRepository bookingRepository;
  private final RoomProductRepository roomProductRepository;
  private final RoomStockRepository roomStockRepository;
  private final UserCouponRepository userCouponRepository;
  // Kafka로 직접 보내지 않고 Spring 내부 이벤트로 발행 → 커밋 이후 BookingEventProducer가 Kafka 전송
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public BookingResponse create(Long userId, BookingCreateRequest request) {
    RoomProduct room = roomProductRepository.findById(request.getRoomProductId())
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_ROOM));

    List<RoomStock> stocks = roomStockRepository.findByRoomProductIdAndDateBetweenWithLock(
        room.getId(), request.getArrDate(), request.getDepDate().minusDays(1));

    int nights = (int) request.getArrDate().until(request.getDepDate(),
        java.time.temporal.ChronoUnit.DAYS);

    if (stocks.size() < nights) {
      throw new DomainException(DomainExceptionCode.OUT_OF_STOCK);
    }

    for (RoomStock stock : stocks) {
      if (stock.getStock() <= 0) {
        throw new DomainException(DomainExceptionCode.OUT_OF_STOCK);
      }
      stock.decrease();
    }

    String bookingNumber = generateBookingNumber();
    int totPrice = room.getPrice() * nights;

    if (request.getUserCouponId() != null) {
      UserCoupon userCoupon = userCouponRepository.findByIdAndUserIdWithCoupon(
              request.getUserCouponId(), userId)
          .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_COUPON));

      if (userCoupon.getIsUsed()) {
        throw new DomainException(DomainExceptionCode.ALREADY_USED_COUPON);
      }
      userCoupon.getCoupon().validateUsable(LocalDate.now());

      int discount = userCoupon.getCoupon().calculateDiscount(totPrice);
      totPrice -= discount;
      userCoupon.use();
    }

    Booking booking = Booking.builder()
        .userId(userId)
        .roomProduct(room)
        .userCouponId(request.getUserCouponId())
        .bookingNumber(bookingNumber)
        .arrDate(request.getArrDate())
        .depDate(request.getDepDate())
        .adultCount(request.getAdultCount())
        .childCount(request.getChildCount() != null ? request.getChildCount() : 0)
        .guestName(request.getGuestName())
        .guestPhone(request.getGuestPhone())
        .requestMemo(request.getRequestMemo())
        .totPrice(totPrice)
        .build();

    Booking saved = bookingRepository.save(booking);

    // 커밋 성공 시에만 BookingEventProducer(@TransactionalEventListener)가 Kafka로 전송한다.
    eventPublisher.publishEvent(BookingCreatedEvent.builder()
        .bookingId(saved.getId())
        .bookingNumber(saved.getBookingNumber())
        .userId(userId)
        .roomProductId(room.getId())
        .arrDate(saved.getArrDate())
        .depDate(saved.getDepDate())
        .totPrice(saved.getTotPrice())
        .build());

    return BookingResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<BookingResponse> getMyBookings(Long userId) {
    return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(BookingResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public BookingResponse getBooking(Long userId, Long bookingId) {
    Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));
    return BookingResponse.from(booking);
  }

  @Transactional
  public BookingResponse cancel(Long userId, Long bookingId, BookingCancelRequest request) {
    Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));

    try {
      booking.cancel(request.getCancelReason());
    } catch (IllegalStateException e) {
      throw new DomainException(DomainExceptionCode.CANNOT_CANCEL);
    }

    List<RoomStock> stocks = roomStockRepository.findByRoomProductIdAndDateBetween(
        booking.getRoomProduct().getId(), booking.getArrDate(), booking.getDepDate().minusDays(1));
    stocks.forEach(RoomStock::increase);

    if (booking.getUserCouponId() != null) {
      userCouponRepository.findById(booking.getUserCouponId())
          .ifPresent(UserCoupon::restore);
    }

    return BookingResponse.from(booking);
  }

  /**
   * 결제 완료에 따른 예약 확정 (PaymentEventConsumer에서 호출)
   *
   * <p>Kafka는 at-least-once 전달이므로 같은 이벤트가 여러 번 오거나, 취소 이후에 늦게 도착할 수 있다.
   * 따라서 현재 예약 상태를 보고 멱등하게 처리한다.
   * <ul>
   *   <li>PENDING: CONFIRMED로 확정</li>
   *   <li>CONFIRMED: 이미 처리된 중복 이벤트 → 아무것도 하지 않음</li>
   *   <li>CANCELLED/COMPLETED: 확정 대상이 아님 → 상태를 되돌리지 않고 경고만 남김</li>
   * </ul>
   * 어떤 경우든 예외 없이 끝나면 Consumer가 ack하여 같은 메시지가 무한 재처리되지 않게 한다.
   *
   * @param bookingId 확정할 예약 ID
   * @param paymentId 이벤트를 발생시킨 결제 ID (로그 추적용)
   */
  @Transactional
  public void confirmByPayment(Long bookingId, Long paymentId) {
    Booking booking = bookingRepository.findByIdWithLock(bookingId)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));

    switch (booking.getStatus()) {
      case PENDING -> {
        booking.confirm();
        log.info("예약 확정 완료 - bookingId: {}, paymentId: {}", bookingId, paymentId);
      }
      case CONFIRMED -> log.info("이미 확정된 예약, 중복 이벤트 무시 - bookingId: {}, paymentId: {}",
          bookingId, paymentId);
      default -> log.warn("확정할 수 없는 상태의 예약, 이벤트 무시 - bookingId: {}, status: {}, paymentId: {}",
          bookingId, booking.getStatus(), paymentId);
    }
  }

  private String generateBookingNumber() {
    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return "BK" + date + uuid;
  }
}
