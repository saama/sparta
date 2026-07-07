package com.domain.booking.service;

import com.domain.booking.dto.request.BookingCancelRequest;
import com.domain.booking.dto.request.BookingCreateRequest;
import com.domain.booking.dto.response.BookingResponse;
import com.domain.booking.entity.Booking;
import com.domain.booking.event.BookingCreatedEvent;
import com.domain.booking.event.BookingEventProducer;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

  private final BookingRepository bookingRepository;
  private final RoomProductRepository roomProductRepository;
  private final RoomStockRepository roomStockRepository;
  private final UserCouponRepository userCouponRepository;
  private final BookingEventProducer bookingEventProducer;

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

    bookingEventProducer.publishBookingCreated(BookingCreatedEvent.builder()
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

  private String generateBookingNumber() {
    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return "BK" + date + uuid;
  }
}
