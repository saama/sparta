package com.domain.payment.entity;

import com.domain.booking.entity.Booking;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Table(name = "payment")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaymentStatus paymentStatus;

    @Column(nullable = false)
    Integer paidAmount;

    @Column(nullable = false)
    Integer refundAmount;

    @Column(length = 100)
    String tid;

    LocalDateTime paidAt;
    LocalDateTime refundedAt;

    @Column(length = 200)
    String failReason;

    @Builder
    public Payment(Booking booking, PaymentMethod paymentMethod, Integer paidAmount, String tid) {
        this.booking = booking;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAmount = paidAmount;
        this.refundAmount = 0;
        this.tid = tid;
        this.paidAt = LocalDateTime.now();
    }

    public void refund() {
        this.paymentStatus = PaymentStatus.REFUNDED;
        this.refundAmount = this.paidAmount;
        this.refundedAt = LocalDateTime.now();
    }
}
