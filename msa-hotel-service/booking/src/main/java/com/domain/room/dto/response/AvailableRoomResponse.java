package com.domain.room.dto.response;

import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailableRoomResponse {

  Long id;
  String name;
  RoomType roomType;
  Integer price;
  Integer baseCapacity;
  Integer maxCapacity;
  String description;
  String thumbnailUrl;
  Integer minStock;

  public static AvailableRoomResponse of(RoomProduct room, Integer minStock) {
    return AvailableRoomResponse.builder()
        .id(room.getId())
        .name(room.getName())
        .roomType(room.getRoomType())
        .price(room.getPrice())
        .baseCapacity(room.getBaseCapacity())
        .maxCapacity(room.getMaxCapacity())
        .description(room.getDescription())
        .thumbnailUrl(room.getThumbnailUrl())
        .minStock(minStock)
        .build();
  }
}
