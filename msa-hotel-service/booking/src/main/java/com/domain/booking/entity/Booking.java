package com.domain.booking.entity;

import com.domain.room.entity.RoomProduct;
import com.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Table(name = "booking")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "user_id")
  Long userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_product_id", nullable = false)
  RoomProduct roomProduct;

  @Column(name = "user_coupon_id")
  Long userCouponId;

  @Column(nullable = false, unique = true, length = 30)
  String bookingNumber;

  @Column(nullable = false)
  LocalDateTime bookingDate;

  @Column(nullable = false)
  LocalDate arrDate;

  @Column(nullable = false)
  LocalDate depDate;

  @Column(nullable = false)
  Integer adultCount;

  @Column(nullable = false)
  Integer childCount;

  @Column(length = 50)
  String guestName;

  @Column(length = 20)
  String guestPhone;

  @Column(length = 500)
  String requestMemo;

  @Column(nullable = false)
  Integer totPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  BookingStatus status;

  LocalDateTime cancelDate;

  @Column(length = 200)
  String cancelReason;

  @Builder
  public Booking(Long userId, RoomProduct roomProduct, Long userCouponId, String bookingNumber,
      LocalDate arrDate, LocalDate depDate, Integer adultCount, Integer childCount,
      String guestName, String guestPhone, String requestMemo, Integer totPrice) {
    this.userId = userId;
    this.roomProduct = roomProduct;
    this.userCouponId = userCouponId;
    this.bookingNumber = bookingNumber;
    this.bookingDate = LocalDateTime.now();
    this.arrDate = arrDate;
    this.depDate = depDate;
    this.adultCount = adultCount;
    this.childCount = childCount;
    this.guestName = guestName;
    this.guestPhone = guestPhone;
    this.requestMemo = requestMemo;
    this.totPrice = totPrice;
    this.status = BookingStatus.PENDING;
  }

  public void cancel(String reason) {
    if (this.status != BookingStatus.PENDING && this.status != BookingStatus.CONFIRMED) {
      throw new IllegalStateException("취소할 수 없는 상태입니다.");
    }
    this.status = BookingStatus.CANCELLED;
    this.cancelDate = LocalDateTime.now();
    this.cancelReason = reason;
  }

  public void confirm() {
    this.status = BookingStatus.CONFIRMED;
  }

  public void complete() {
    this.status = BookingStatus.COMPLETED;
  }
}
