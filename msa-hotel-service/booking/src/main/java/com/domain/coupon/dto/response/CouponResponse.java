package com.domain.coupon.dto.response;

import com.domain.coupon.entity.Coupon;
import com.domain.coupon.entity.DiscountType;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CouponResponse {

    private Long id;
    private String code;
    private String name;
    private DiscountType discountType;
    private Integer discountValue;
    private Integer minPrice;
    private Integer maxDiscount;
    private Integer issueLimit;
    private Integer issuedCount;
    private LocalDate validFrom;
    private LocalDate validUntil;

    public static CouponResponse from(Coupon coupon) {
        return CouponResponse.builder()
            .id(coupon.getId())
            .code(coupon.getCode())
            .name(coupon.getName())
            .discountType(coupon.getDiscountType())
            .discountValue(coupon.getDiscountValue())
            .minPrice(coupon.getMinPrice())
            .maxDiscount(coupon.getMaxDiscount())
            .issueLimit(coupon.getIssueLimit())
            .issuedCount(coupon.getIssuedCount())
            .validFrom(coupon.getValidFrom())
            .validUntil(coupon.getValidUntil())
            .build();
    }
}
