package com.domain.room.entity;

import com.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Table(name = "room_product")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomProduct extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 100)
  String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  RoomType roomType;

  @Column(nullable = false)
  Integer price;

  @Column(nullable = false)
  Integer baseCapacity;

  @Column(nullable = false)
  Integer maxCapacity;

  @Column(columnDefinition = "TEXT")
  String description;

  @Column(length = 500)
  String thumbnailUrl;

  @Column(nullable = false)
  Boolean isActive;

  @Builder
  public RoomProduct(String name, RoomType roomType, Integer price, Integer baseCapacity,
      Integer maxCapacity, String description, String thumbnailUrl) {
    this.name = name;
    this.roomType = roomType;
    this.price = price;
    this.baseCapacity = baseCapacity;
    this.maxCapacity = maxCapacity;
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
    this.isActive = true;
  }

  public void update(String name, RoomType roomType, Integer price, Integer baseCapacity,
      Integer maxCapacity, String description, String thumbnailUrl) {
    this.name = name;
    this.roomType = roomType;
    this.price = price;
    this.baseCapacity = baseCapacity;
    this.maxCapacity = maxCapacity;
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
  }

  public void deactivate() {
    this.isActive = false;
  }
}
