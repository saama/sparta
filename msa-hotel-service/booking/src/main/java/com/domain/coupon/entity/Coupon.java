package com.domain.coupon.entity;

import com.global.entity.BaseEntity;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 쿠폰 마스터 엔티티
 *
 * <p>선착순 쿠폰 발급 시 동시성 문제를 방지하기 위해 {@code @Version} 기반 낙관적 락을 적용한다.
 * 두 트랜잭션이 동시에 issuedCount를 증가시키려 할 때 하나는 ObjectOptimisticLockingFailureException이 발생하며
 * 상위 레이어에서 재시도 또는 소진 에러로 처리한다.
 *
 * <p>할인 유형:
 * <ul>
 *   <li>FIXED  - 정액 할인 (discountValue 원 차감)</li>
 *   <li>PERCENT - 정률 할인 (discountValue %, maxDiscount 한도 적용)</li>
 * </ul>
 */
@Table(name = "coupon")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** 외부 노출용 쿠폰 코드 (유니크) */
    @Column(nullable = false, unique = true, length = 50)
    String code;

    @Column(nullable = false, length = 100)
    String name;

    /** 할인 유형: FIXED(정액) | PERCENT(정률) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    DiscountType discountType;

    /** 정액: 원 단위 / 정률: % 단위 */
    @Column(nullable = false)
    Integer discountValue;

    /** 쿠폰 사용 가능 최소 주문 금액 */
    @Column(nullable = false)
    Integer minPrice;

    /** 정률 쿠폰의 최대 할인 한도 (null = 무제한) */
    Integer maxDiscount;

    /** 총 발급 가능 수량 (null = 무제한) */
    Integer issueLimit;

    /** 현재까지 발급된 수량 - 낙관적 락으로 동시성 제어 */
    @Column(nullable = false)
    Integer issuedCount;

    @Column(nullable = false)
    LocalDate validFrom;

    @Column(nullable = false)
    LocalDate validUntil;

    /** 낙관적 락 버전 필드 - JPA가 UPDATE 시 자동으로 버전 비교 */
    @Version
    Long version;

    @Builder
    public Coupon(String code, String name, DiscountType discountType, Integer discountValue,
        Integer minPrice, Integer maxDiscount, Integer issueLimit, LocalDate validFrom,
        LocalDate validUntil) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minPrice = minPrice;
        this.maxDiscount = maxDiscount;
        this.issueLimit = issueLimit;
        this.issuedCount = 0;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /**
     * 쿠폰 발급 처리
     *
     * <p>issueLimit 초과 시 예외를 던진다.
     * 낙관적 락에 의해 동시 발급 시 하나의 트랜잭션만 성공한다.
     */
    public void issue() {
        if (issueLimit != null && issuedCount >= issueLimit) {
            throw new DomainException(DomainExceptionCode.COUPON_SOLD_OUT);
        }
        this.issuedCount++;
    }

    /**
     * 할인 금액 계산
     *
     * @param originalPrice 원래 결제 금액
     * @return 할인 금액 (originalPrice를 초과하지 않음)
     */
    public int calculateDiscount(int originalPrice) {
        if (originalPrice < minPrice) {
            throw new DomainException(DomainExceptionCode.NOT_MET_MIN_PRICE);
        }
        if (discountType == DiscountType.FIXED) {
            return Math.min(discountValue, originalPrice);
        }
        // PERCENT: discountValue% 할인, maxDiscount 한도 적용
        int discount = originalPrice * discountValue / 100;
        if (maxDiscount != null) {
            discount = Math.min(discount, maxDiscount);
        }
        return discount;
    }

    /**
     * 쿠폰 사용 가능 기간 검증
     *
     * @param today 오늘 날짜
     */
    public void validateUsable(LocalDate today) {
        if (today.isBefore(validFrom) || today.isAfter(validUntil)) {
            throw new DomainException(DomainExceptionCode.EXPIRED_COUPON);
        }
    }
}
