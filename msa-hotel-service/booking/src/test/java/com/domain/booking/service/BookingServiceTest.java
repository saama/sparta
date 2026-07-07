package com.domain.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.domain.booking.dto.request.BookingCancelRequest;
import com.domain.booking.dto.request.BookingCreateRequest;
import com.domain.booking.dto.response.BookingResponse;
import com.domain.booking.entity.Booking;
import com.domain.booking.entity.BookingStatus;
import com.domain.booking.event.BookingEventProducer;
import com.domain.booking.repository.BookingRepository;
import com.domain.coupon.entity.Coupon;
import com.domain.coupon.entity.DiscountType;
import com.domain.coupon.entity.UserCoupon;
import com.domain.coupon.repository.UserCouponRepository;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomStock;
import com.domain.room.entity.RoomType;
import com.domain.room.repository.RoomProductRepository;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

  @InjectMocks
  private BookingService bookingService;

  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private RoomProductRepository roomProductRepository;
  @Mock
  private RoomStockRepository roomStockRepository;
  @Mock
  private UserCouponRepository userCouponRepository;
  @Mock
  private BookingEventProducer bookingEventProducer;

  // ─────────────────────────────────────────────────────────────
  // 픽스처
  // ─────────────────────────────────────────────────────────────

  private RoomProduct createRoom() {
    RoomProduct room = RoomProduct.builder()
        .name("디럭스 더블")
        .roomType(RoomType.DELUXE)
        .price(150000)
        .baseCapacity(2)
        .maxCapacity(4)
        .build();
    setField(room, "id", 1L);
    setField(room, "isActive", true);
    return room;
  }

  private RoomStock createStock(RoomProduct room, LocalDate date, int stock) {
    return RoomStock.builder().roomProduct(room).date(date).stock(stock).build();
  }

  /** FIXED 정액 쿠폰 (10,000원 할인, 최소 주문 50,000원) */
  private Coupon createFixedCoupon(int discountValue, int minPrice) {
    Coupon coupon = Coupon.builder()
        .code("FIXED10000")
        .name("10,000원 할인 쿠폰")
        .discountType(DiscountType.FIXED)
        .discountValue(discountValue)
        .minPrice(minPrice)
        .validFrom(LocalDate.now().minusDays(1))
        .validUntil(LocalDate.now().plusDays(30))
        .build();
    setField(coupon, "id", 100L);
    return coupon;
  }

  private UserCoupon createUserCoupon(Long userCouponId, Long userId, Coupon coupon) {
    UserCoupon uc = UserCoupon.builder().userId(userId).coupon(coupon).build();
    setField(uc, "id", userCouponId);
    return uc;
  }

  // ─────────────────────────────────────────────────────────────
  // 예약 생성 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("예약 생성 성공 - 재고 차감 및 예약 저장")
  void createBooking_success() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 3);

    BookingCreateRequest request = new BookingCreateRequest();
    setField(request, "roomProductId", 1L);
    setField(request, "arrDate", arrDate);
    setField(request, "depDate", depDate);
    setField(request, "adultCount", 2);
    setField(request, "childCount", 0);

    RoomProduct room = createRoom();
    RoomStock stock1 = createStock(room, arrDate, 3);
    RoomStock stock2 = createStock(room, arrDate.plusDays(1), 2);

    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));
    given(roomStockRepository.findByRoomProductIdAndDateBetweenWithLock(
        eq(1L), eq(arrDate), eq(depDate.minusDays(1))))
        .willReturn(List.of(stock1, stock2));

    Booking savedBooking = Booking.builder()
        .userId(10L).roomProduct(room).bookingNumber("BK20260701ABCD1234")
        .arrDate(arrDate).depDate(depDate).adultCount(2).childCount(0).totPrice(300000)
        .build();
    setField(savedBooking, "id", 1L);
    given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

    BookingResponse response = bookingService.create(10L, request);

    assertThat(response.getTotPrice()).isEqualTo(300000);
    // 재고가 각각 1씩 감소했는지 검증
    assertThat(stock1.getStock()).isEqualTo(2);
    assertThat(stock2.getStock()).isEqualTo(1);
    verify(bookingRepository).save(any(Booking.class));
    verify(bookingEventProducer).publishBookingCreated(any());
  }

  @Test
  @DisplayName("예약 생성 성공 - FIXED 쿠폰 할인 적용")
  void createBooking_success_withFixedCoupon() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 2); // 1박 = 150,000원

    BookingCreateRequest request = new BookingCreateRequest();
    setField(request, "roomProductId", 1L);
    setField(request, "arrDate", arrDate);
    setField(request, "depDate", depDate);
    setField(request, "adultCount", 2);
    setField(request, "userCouponId", 50L);

    RoomProduct room = createRoom();
    RoomStock stock = createStock(room, arrDate, 3);

    Coupon coupon = createFixedCoupon(10000, 50000); // 10,000원 할인
    UserCoupon userCoupon = createUserCoupon(50L, 10L, coupon);

    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));
    given(roomStockRepository.findByRoomProductIdAndDateBetweenWithLock(
        eq(1L), eq(arrDate), eq(arrDate)))
        .willReturn(List.of(stock));
    given(userCouponRepository.findByIdAndUserIdWithCoupon(50L, 10L))
        .willReturn(Optional.of(userCoupon));

    // 할인 후 금액(140,000원)으로 저장된 예약을 반환하도록 mock
    Booking savedBooking = Booking.builder()
        .userId(10L).roomProduct(room).bookingNumber("BK001")
        .arrDate(arrDate).depDate(depDate).adultCount(2).childCount(0).totPrice(140000)
        .build();
    setField(savedBooking, "id", 1L);
    given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

    BookingResponse response = bookingService.create(10L, request);

    // 150,000 - 10,000 = 140,000 검증
    assertThat(response.getTotPrice()).isEqualTo(140000);
    // 쿠폰이 사용됨으로 표시되었는지 검증
    assertThat(userCoupon.getIsUsed()).isTrue();
  }

  @Test
  @DisplayName("예약 생성 실패 - 이미 사용된 쿠폰")
  void createBooking_fail_alreadyUsedCoupon() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 2);

    BookingCreateRequest request = new BookingCreateRequest();
    setField(request, "roomProductId", 1L);
    setField(request, "arrDate", arrDate);
    setField(request, "depDate", depDate);
    setField(request, "adultCount", 2);
    setField(request, "userCouponId", 50L);

    RoomProduct room = createRoom();
    RoomStock stock = createStock(room, arrDate, 3);

    Coupon coupon = createFixedCoupon(10000, 50000);
    UserCoupon userCoupon = createUserCoupon(50L, 10L, coupon);
    userCoupon.use(); // 이미 사용 처리

    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));
    given(roomStockRepository.findByRoomProductIdAndDateBetweenWithLock(any(), any(), any()))
        .willReturn(List.of(stock));
    given(userCouponRepository.findByIdAndUserIdWithCoupon(50L, 10L))
        .willReturn(Optional.of(userCoupon));

    assertThatThrownBy(() -> bookingService.create(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("이미 사용된 쿠폰");
  }

  @Test
  @DisplayName("예약 생성 실패 - 재고 없음")
  void createBooking_fail_outOfStock() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 2);

    BookingCreateRequest request = new BookingCreateRequest();
    setField(request, "roomProductId", 1L);
    setField(request, "arrDate", arrDate);
    setField(request, "depDate", depDate);
    setField(request, "adultCount", 2);

    RoomProduct room = createRoom();
    RoomStock stock = createStock(room, arrDate, 0); // 재고 0

    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));
    given(roomStockRepository.findByRoomProductIdAndDateBetweenWithLock(
        eq(1L), eq(arrDate), eq(arrDate)))
        .willReturn(List.of(stock));

    assertThatThrownBy(() -> bookingService.create(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("예약 가능한 객실이 없습니다");
  }

  @Test
  @DisplayName("예약 생성 실패 - 존재하지 않는 객실")
  void createBooking_fail_roomNotFound() {
    BookingCreateRequest request = new BookingCreateRequest();
    setField(request, "roomProductId", 99L);
    setField(request, "arrDate", LocalDate.of(2026, 7, 1));
    setField(request, "depDate", LocalDate.of(2026, 7, 2));
    setField(request, "adultCount", 1);

    given(roomProductRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> bookingService.create(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("객실 상품을 찾을 수 없습니다");
  }

  // ─────────────────────────────────────────────────────────────
  // 예약 조회 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("내 예약 목록 조회")
  void getMyBookings_success() {
    RoomProduct room = createRoom();
    Booking booking = Booking.builder()
        .userId(10L).roomProduct(room).bookingNumber("BK001")
        .arrDate(LocalDate.of(2026, 7, 1)).depDate(LocalDate.of(2026, 7, 3))
        .adultCount(2).childCount(0).totPrice(300000)
        .build();

    given(bookingRepository.findByUserIdOrderByCreatedAtDesc(10L)).willReturn(List.of(booking));

    List<BookingResponse> result = bookingService.getMyBookings(10L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getTotPrice()).isEqualTo(300000);
  }

  // ─────────────────────────────────────────────────────────────
  // 예약 취소 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("예약 취소 성공 - 재고 복구")
  void cancelBooking_success() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 3);
    RoomProduct room = createRoom();

    Booking booking = Booking.builder()
        .userId(10L).roomProduct(room).bookingNumber("BK001")
        .arrDate(arrDate).depDate(depDate).adultCount(2).childCount(0).totPrice(300000)
        .build();
    setField(booking, "id", 1L);

    RoomStock stock = createStock(room, arrDate, 1);

    given(bookingRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.of(booking));
    given(roomStockRepository.findByRoomProductIdAndDateBetween(
        eq(1L), eq(arrDate), eq(depDate.minusDays(1))))
        .willReturn(List.of(stock));

    BookingCancelRequest cancelRequest = new BookingCancelRequest();
    setField(cancelRequest, "cancelReason", "일정 변경");

    BookingResponse response = bookingService.cancel(10L, 1L, cancelRequest);

    assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    // 재고가 1 복구되었는지 검증
    assertThat(stock.getStock()).isEqualTo(2);
  }

  @Test
  @DisplayName("예약 취소 성공 - 쿠폰 복구")
  void cancelBooking_success_restoresCoupon() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 2);
    RoomProduct room = createRoom();

    Booking booking = Booking.builder()
        .userId(10L).roomProduct(room).bookingNumber("BK002")
        .arrDate(arrDate).depDate(depDate).adultCount(2).childCount(0).totPrice(140000)
        .build();
    setField(booking, "id", 2L);
    setField(booking, "userCouponId", 50L); // 쿠폰 사용 이력

    Coupon coupon = createFixedCoupon(10000, 50000);
    UserCoupon userCoupon = createUserCoupon(50L, 10L, coupon);
    userCoupon.use(); // 사용 상태

    given(bookingRepository.findByIdAndUserId(2L, 10L)).willReturn(Optional.of(booking));
    given(roomStockRepository.findByRoomProductIdAndDateBetween(any(), any(), any()))
        .willReturn(List.of());
    given(userCouponRepository.findById(50L)).willReturn(Optional.of(userCoupon));

    BookingCancelRequest cancelRequest = new BookingCancelRequest();
    setField(cancelRequest, "cancelReason", "취소");

    bookingService.cancel(10L, 2L, cancelRequest);

    // 쿠폰 사용 취소 검증
    assertThat(userCoupon.getIsUsed()).isFalse();
  }

  @Test
  @DisplayName("예약 취소 실패 - 다른 유저의 예약")
  void cancelBooking_fail_notOwner() {
    given(bookingRepository.findByIdAndUserId(1L, 99L)).willReturn(Optional.empty());

    BookingCancelRequest cancelRequest = new BookingCancelRequest();

    assertThatThrownBy(() -> bookingService.cancel(99L, 1L, cancelRequest))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("예약 정보를 찾을 수 없습니다");
  }

  // ─────────────────────────────────────────────────────────────
  // 테스트 유틸
  // ─────────────────────────────────────────────────────────────

  private void setField(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = obj.getClass().getSuperclass();
      try {
        var field = superClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
