package com.domain.coupon.dto.request;

import com.domain.coupon.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.Getter;

@Getter
public class CouponCreateRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @Positive
    private Integer discountValue;

    @NotNull
    private Integer minPrice;

    private Integer maxDiscount;

    private Integer issueLimit;

    @NotNull
    private LocalDate validFrom;

    @NotNull
    private LocalDate validUntil;
}
