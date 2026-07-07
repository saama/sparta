package com.domain.review.entity;

import com.domain.booking.entity.Booking;
import com.domain.room.entity.RoomProduct;
import com.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 리뷰 엔티티
 *
 * <p>COMPLETED 상태의 예약 1건당 리뷰를 1개만 작성할 수 있다.
 * booking_id 에 UNIQUE 제약이 있어 중복 작성을 DB 레벨에서 방지한다.
 */
@Table(name = "review")
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** 리뷰 작성자 (user.id FK) */
    @Column(name = "user_id", nullable = false)
    Long userId;

    /** 리뷰 대상 예약 (1예약 1리뷰) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    Booking booking;

    /** 객실 상품 (조회 편의용 역정규화) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_product_id", nullable = false)
    RoomProduct roomProduct;

    /** 별점 1~5 */
    @Column(nullable = false)
    Integer rating;

    /** 리뷰 본문 */
    @Column(columnDefinition = "TEXT")
    String content;

    /** 노출 여부 (관리자 숨김 처리 가능) */
    @Column(nullable = false)
    Boolean isVisible;

    @Builder
    public Review(Long userId, Booking booking, RoomProduct roomProduct, Integer rating, String content) {
        this.userId = userId;
        this.booking = booking;
        this.roomProduct = roomProduct;
        this.rating = rating;
        this.content = content;
        this.isVisible = true;
    }

    /** 리뷰 내용/별점 수정 */
    public void update(Integer rating, String content) {
        if (rating != null) {
            this.rating = rating;
        }
        if (content != null) {
            this.content = content;
        }
    }

    /** 관리자 숨김 처리 */
    public void hide() {
        this.isVisible = false;
    }
}
