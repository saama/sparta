package com.domain.room.dto.request;

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
public class RoomStockInitRequest {

  @NotNull
  LocalDate startDate;

  @NotNull
  LocalDate endDate;

  @NotNull
  @Min(0)
  Integer stock;
}
