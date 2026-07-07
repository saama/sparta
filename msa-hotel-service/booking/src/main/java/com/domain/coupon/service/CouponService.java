package com.domain.coupon.service;

import com.domain.coupon.dto.request.CouponCreateRequest;
import com.domain.coupon.dto.response.CouponResponse;
import com.domain.coupon.dto.response.UserCouponResponse;
import com.domain.coupon.entity.Coupon;
import com.domain.coupon.entity.UserCoupon;
import com.domain.coupon.repository.CouponRepository;
import com.domain.coupon.repository.UserCouponRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 서비스
 *
 * <p>쿠폰 생성(어드민), 발급, 보유 목록 조회를 담당한다.
 *
 * <p>선착순 쿠폰 발급 동시성 처리:
 * {@link CouponRepository#findByIdWithOptimisticLock}으로 낙관적 락을 획득한 뒤
 * {@link Coupon#issue()}에서 issuedCount를 증가시킨다.
 * 동시 요청 충돌 시 ObjectOptimisticLockingFailureException이 발생하며
 * 호출부에서 재시도 처리가 필요하다.
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 생성 (어드민 전용)
     *
     * @param request 쿠폰 생성 요청 (코드, 할인 유형, 발급 한도 등)
     * @return 생성된 쿠폰 응답
     */
    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        Coupon coupon = Coupon.builder()
            .code(request.getCode())
            .name(request.getName())
            .discountType(request.getDiscountType())
            .discountValue(request.getDiscountValue())
            .minPrice(request.getMinPrice())
            .maxDiscount(request.getMaxDiscount())
            .issueLimit(request.getIssueLimit())
            .validFrom(request.getValidFrom())
            .validUntil(request.getValidUntil())
            .build();
        return CouponResponse.from(couponRepository.save(coupon));
    }

    /**
     * 쿠폰 발급 (선착순 낙관적 락 적용)
     *
     * <p>1인 1쿠폰 중복 발급 방지 → 유효기간 검증 → 재고 차감 순으로 처리한다.
     *
     * @param userId   발급 대상 유저 ID
     * @param couponId 발급할 쿠폰 ID
     * @return 발급된 유저 쿠폰 응답
     */
    @Transactional
    public UserCouponResponse issueCoupon(Long userId, Long couponId) {
        // 동일 유저의 중복 발급 방지 (DB UNIQUE 제약 + 소프트 체크)
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new DomainException(DomainExceptionCode.ALREADY_ISSUED_COUPON);
        }

        // 낙관적 락으로 쿠폰 조회 → 동시 발급 충돌 시 버전 불일치로 롤백
        Coupon coupon = couponRepository.findByIdWithOptimisticLock(couponId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_COUPON));

        coupon.validateUsable(LocalDate.now());
        coupon.issue(); // issuedCount 증가 + 재고 초과 검증

        UserCoupon userCoupon = UserCoupon.builder()
            .userId(userId)
            .coupon(coupon)
            .build();
        return UserCouponResponse.from(userCouponRepository.save(userCoupon));
    }

    /**
     * 내 쿠폰 목록 조회
     *
     * @param userId 조회할 유저 ID
     * @return 보유 쿠폰 목록 (사용 여부 포함)
     */
    @Transactional(readOnly = true)
    public List<UserCouponResponse> getMyCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
            .map(UserCouponResponse::from)
            .toList();
    }
}
