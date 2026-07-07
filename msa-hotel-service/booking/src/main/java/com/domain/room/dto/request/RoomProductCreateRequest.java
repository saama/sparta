package com.domain.room.dto.request;

import com.domain.room.entity.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomProductCreateRequest {

  @NotBlank
  String name;

  @NotNull
  RoomType roomType;

  @NotNull
  @Min(0)
  Integer price;

  @NotNull
  @Min(1)
  Integer baseCapacity;

  @NotNull
  @Min(1)
  Integer maxCapacity;

  String description;

  String thumbnailUrl;
}
