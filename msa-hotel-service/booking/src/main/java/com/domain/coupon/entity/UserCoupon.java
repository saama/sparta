package com.domain.coupon.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Table(name = "user_coupon")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    Coupon coupon;

    @Column(nullable = false)
    Boolean isUsed;

    LocalDateTime usedAt;

    @Builder
    public UserCoupon(Long userId, Coupon coupon) {
        this.userId = userId;
        this.coupon = coupon;
        this.isUsed = false;
    }

    public void use() {
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isUsed = false;
        this.usedAt = null;
    }
}
