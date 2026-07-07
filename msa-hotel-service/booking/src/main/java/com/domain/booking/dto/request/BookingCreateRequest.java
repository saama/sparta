package com.domain.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingCreateRequest {

  @NotNull
  Long roomProductId;

  @NotNull
  LocalDate arrDate;

  @NotNull
  LocalDate depDate;

  @NotNull
  @Min(1)
  Integer adultCount;

  Integer childCount;

  String guestName;

  String guestPhone;

  String requestMemo;

  Long userCouponId;
}
