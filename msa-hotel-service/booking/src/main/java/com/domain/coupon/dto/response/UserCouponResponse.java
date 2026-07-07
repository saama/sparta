package com.domain.coupon.dto.response;

import com.domain.coupon.entity.DiscountType;
import com.domain.coupon.entity.UserCoupon;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserCouponResponse {

    private Long id;
    private Long couponId;
    private String couponName;
    private DiscountType discountType;
    private Integer discountValue;
    private Integer minPrice;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private Boolean isUsed;
    private LocalDateTime usedAt;

    public static UserCouponResponse from(UserCoupon uc) {
        return UserCouponResponse.builder()
            .id(uc.getId())
            .couponId(uc.getCoupon().getId())
            .couponName(uc.getCoupon().getName())
            .discountType(uc.getCoupon().getDiscountType())
            .discountValue(uc.getCoupon().getDiscountValue())
            .minPrice(uc.getCoupon().getMinPrice())
            .validFrom(uc.getCoupon().getValidFrom())
            .validUntil(uc.getCoupon().getValidUntil())
            .isUsed(uc.getIsUsed())
            .usedAt(uc.getUsedAt())
            .build();
    }
}
