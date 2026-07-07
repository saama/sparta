package com.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.domain.coupon.dto.request.CouponCreateRequest;
import com.domain.coupon.dto.response.CouponResponse;
import com.domain.coupon.dto.response.UserCouponResponse;
import com.domain.coupon.entity.Coupon;
import com.domain.coupon.entity.DiscountType;
import com.domain.coupon.entity.UserCoupon;
import com.domain.coupon.repository.CouponRepository;
import com.domain.coupon.repository.UserCouponRepository;
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
class CouponServiceTest {

  @InjectMocks
  private CouponService couponService;

  @Mock
  private CouponRepository couponRepository;
  @Mock
  private UserCouponRepository userCouponRepository;

  // ─────────────────────────────────────────────────────────────
  // 픽스처
  // ─────────────────────────────────────────────────────────────

  /** 유효한 FIXED 쿠폰 (10,000원 할인, 최소 50,000원, 한도 100장) */
  private Coupon createValidFixedCoupon(int issueLimit) {
    Coupon coupon = Coupon.builder()
        .code("FIXED10000")
        .name("10,000원 할인")
        .discountType(DiscountType.FIXED)
        .discountValue(10000)
        .minPrice(50000)
        .issueLimit(issueLimit)
        .validFrom(LocalDate.now().minusDays(1))
        .validUntil(LocalDate.now().plusDays(30))
        .build();
    setField(coupon, "id", 1L);
    return coupon;
  }

  /** 유효기간 만료된 쿠폰 */
  private Coupon createExpiredCoupon() {
    Coupon coupon = Coupon.builder()
        .code("EXPIRED")
        .name("만료 쿠폰")
        .discountType(DiscountType.FIXED)
        .discountValue(5000)
        .minPrice(0)
        .validFrom(LocalDate.now().minusDays(10))
        .validUntil(LocalDate.now().minusDays(1)) // 어제 만료
        .build();
    setField(coupon, "id", 2L);
    return coupon;
  }

  // ─────────────────────────────────────────────────────────────
  // 쿠폰 생성 테스트 (어드민)
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("쿠폰 생성 성공")
  void createCoupon_success() {
    CouponCreateRequest request = new CouponCreateRequest();
    setField(request, "code", "SUMMER2026");
    setField(request, "name", "여름 할인");
    setField(request, "discountType", DiscountType.PERCENT);
    setField(request, "discountValue", 10);
    setField(request, "minPrice", 100000);
    setField(request, "issueLimit", 50);
    setField(request, "validFrom", LocalDate.now());
    setField(request, "validUntil", LocalDate.now().plusDays(30));

    Coupon saved = createValidFixedCoupon(50);
    given(couponRepository.save(any(Coupon.class))).willReturn(saved);

    CouponResponse response = couponService.createCoupon(request);

    assertThat(response).isNotNull();
    verify(couponRepository).save(any(Coupon.class));
  }

  // ─────────────────────────────────────────────────────────────
  // 쿠폰 발급 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("쿠폰 발급 성공")
  void issueCoupon_success() {
    Coupon coupon = createValidFixedCoupon(100);
    UserCoupon userCoupon = UserCoupon.builder().userId(10L).coupon(coupon).build();

    given(userCouponRepository.existsByUserIdAndCouponId(10L, 1L)).willReturn(false);
    given(couponRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(coupon));
    given(userCouponRepository.save(any(UserCoupon.class))).willReturn(userCoupon);

    UserCouponResponse response = couponService.issueCoupon(10L, 1L);

    assertThat(response).isNotNull();
    // 발급 후 issuedCount가 1 증가했는지 검증
    assertThat(coupon.getIssuedCount()).isEqualTo(1);
    verify(userCouponRepository).save(any(UserCoupon.class));
  }

  @Test
  @DisplayName("쿠폰 발급 실패 - 중복 발급")
  void issueCoupon_fail_duplicate() {
    // 이미 발급 이력이 존재하는 경우
    given(userCouponRepository.existsByUserIdAndCouponId(10L, 1L)).willReturn(true);

    assertThatThrownBy(() -> couponService.issueCoupon(10L, 1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("이미 발급받은 쿠폰");
  }

  @Test
  @DisplayName("쿠폰 발급 실패 - 존재하지 않는 쿠폰")
  void issueCoupon_fail_notFound() {
    given(userCouponRepository.existsByUserIdAndCouponId(10L, 99L)).willReturn(false);
    given(couponRepository.findByIdWithOptimisticLock(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> couponService.issueCoupon(10L, 99L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("쿠폰을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("쿠폰 발급 실패 - 유효기간 만료")
  void issueCoupon_fail_expired() {
    Coupon expired = createExpiredCoupon();

    given(userCouponRepository.existsByUserIdAndCouponId(10L, 2L)).willReturn(false);
    given(couponRepository.findByIdWithOptimisticLock(2L)).willReturn(Optional.of(expired));

    assertThatThrownBy(() -> couponService.issueCoupon(10L, 2L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("유효기간이 만료된 쿠폰");
  }

  @Test
  @DisplayName("쿠폰 발급 실패 - 수량 소진")
  void issueCoupon_fail_soldOut() {
    // 발급 한도 1장짜리 쿠폰을 이미 1장 발급한 상태로 세팅
    Coupon coupon = createValidFixedCoupon(1);
    setField(coupon, "issuedCount", 1); // 이미 1장 발급됨

    given(userCouponRepository.existsByUserIdAndCouponId(10L, 1L)).willReturn(false);
    given(couponRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(coupon));

    assertThatThrownBy(() -> couponService.issueCoupon(10L, 1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("쿠폰이 모두 소진");
  }

  // ─────────────────────────────────────────────────────────────
  // 내 쿠폰 목록 조회 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("내 쿠폰 목록 조회 - 보유 쿠폰 반환")
  void getMyCoupons_success() {
    Coupon coupon = createValidFixedCoupon(100);
    UserCoupon uc = UserCoupon.builder().userId(10L).coupon(coupon).build();

    given(userCouponRepository.findByUserId(10L)).willReturn(List.of(uc));

    List<UserCouponResponse> result = couponService.getMyCoupons(10L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIsUsed()).isFalse();
  }

  @Test
  @DisplayName("내 쿠폰 목록 조회 - 보유 쿠폰 없음")
  void getMyCoupons_empty() {
    given(userCouponRepository.findByUserId(10L)).willReturn(List.of());

    List<UserCouponResponse> result = couponService.getMyCoupons(10L);

    assertThat(result).isEmpty();
  }

  // ─────────────────────────────────────────────────────────────
  // Coupon 엔티티 도메인 로직 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("FIXED 할인 계산 - 정상")
  void coupon_calculateDiscount_fixed() {
    Coupon coupon = createValidFixedCoupon(100);
    int discount = coupon.calculateDiscount(150000);
    assertThat(discount).isEqualTo(10000);
  }

  @Test
  @DisplayName("PERCENT 할인 계산 - maxDiscount 한도 적용")
  void coupon_calculateDiscount_percent_withMaxDiscount() {
    Coupon coupon = Coupon.builder()
        .code("P10")
        .name("10% 최대 20,000원 할인")
        .discountType(DiscountType.PERCENT)
        .discountValue(10)
        .minPrice(0)
        .maxDiscount(20000)
        .validFrom(LocalDate.now())
        .validUntil(LocalDate.now().plusDays(10))
        .build();

    // 300,000 * 10% = 30,000 → maxDiscount 20,000 적용
    assertThat(coupon.calculateDiscount(300000)).isEqualTo(20000);
    // 100,000 * 10% = 10,000 → maxDiscount 미적용
    assertThat(coupon.calculateDiscount(100000)).isEqualTo(10000);
  }

  @Test
  @DisplayName("할인 계산 실패 - 최소 주문 금액 미충족")
  void coupon_calculateDiscount_fail_minPrice() {
    Coupon coupon = createValidFixedCoupon(100); // minPrice = 50,000

    assertThatThrownBy(() -> coupon.calculateDiscount(30000))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("최소 주문 금액");
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
