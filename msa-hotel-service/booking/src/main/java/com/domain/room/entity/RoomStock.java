package com.domain.room.entity;

import com.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Table(name = "room_stock")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomStock extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_product_id", nullable = false)
  RoomProduct roomProduct;

  @Column(nullable = false)
  LocalDate date;

  @Column(nullable = false)
  Integer stock;

  @Builder
  public RoomStock(RoomProduct roomProduct, LocalDate date, Integer stock) {
    this.roomProduct = roomProduct;
    this.date = date;
    this.stock = stock;
  }

  public void decrease() {
    if (this.stock <= 0) {
      throw new IllegalStateException("재고가 없습니다.");
    }
    this.stock--;
  }

  public void increase() {
    this.stock++;
  }
}
