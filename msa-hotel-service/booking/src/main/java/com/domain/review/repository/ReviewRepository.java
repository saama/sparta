package com.domain.review.repository;

import com.domain.review.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 예약 ID로 리뷰 존재 여부 확인 (중복 작성 방지) */
    boolean existsByBookingId(Long bookingId);

    /** 특정 객실 상품의 리뷰 목록 조회 (노출된 것만, 최신순 페이징) */
    Page<Review> findByRoomProductIdAndIsVisibleTrueOrderByCreatedAtDesc(Long roomProductId, Pageable pageable);

    /** 별점 필터링 포함 조회 (최신순 페이징) */
    Page<Review> findByRoomProductIdAndRatingAndIsVisibleTrueOrderByCreatedAtDesc(Long roomProductId, Integer rating, Pageable pageable);

    /** 특정 사용자의 리뷰 목록 */
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 작성자 검증용 단건 조회 */
    Optional<Review> findByIdAndUserId(Long id, Long userId);
}
