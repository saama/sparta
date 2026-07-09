package com.domain.review.service;

import com.domain.booking.entity.Booking;
import com.domain.booking.entity.BookingStatus;
import com.domain.booking.repository.BookingRepository;
import com.domain.review.dto.request.ReviewCreateRequest;
import com.domain.review.dto.request.ReviewUpdateRequest;
import com.domain.review.dto.response.ReviewResponse;
import com.domain.review.entity.Review;
import com.domain.review.repository.ReviewRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import com.global.response.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 서비스
 *
 * <p>COMPLETED 상태 예약에 한해 리뷰 1건 작성을 허용한다.
 * 수정/삭제는 작성자 본인만 가능하며, 목록은 별점 필터링을 지원한다.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    /**
     * 리뷰 작성
     *
     * <p>예약이 COMPLETED 상태여야 하며, 예약 1건당 리뷰 1개만 허용한다.
     *
     * @param userId  작성자 ID
     * @param request 별점, 내용, 예약 ID
     * @return 작성된 리뷰
     */
    @Transactional
    public ReviewResponse create(Long userId, ReviewCreateRequest request) {
        Booking booking = bookingRepository.findByIdAndUserId(request.getBookingId(), userId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));

        // COMPLETED 상태 예약만 리뷰 작성 가능
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new DomainException(DomainExceptionCode.UNAUTHORIZED_REVIEW);
        }

        // 예약 1건당 리뷰 1개 제한
        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new DomainException(DomainExceptionCode.DUPLICATE_REVIEW);
        }

        Review review = Review.builder()
            .userId(userId)
            .booking(booking)
            .roomProduct(booking.getRoomProduct())
            .rating(request.getRating())
            .content(request.getContent())
            .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    /**
     * 객실 상품별 리뷰 목록 조회
     *
     * @param roomProductId 객실 상품 ID
     * @param rating        별점 필터 (null이면 전체)
     * @param pageable      페이징 정보 (페이지 번호/크기)
     * @return 리뷰 페이징 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public PageResult<ReviewResponse> getByRoom(Long roomProductId, Integer rating, Pageable pageable) {
        Page<Review> reviews = (rating != null)
            ? reviewRepository.findByRoomProductIdAndRatingAndIsVisibleTrueOrderByCreatedAtDesc(roomProductId, rating, pageable)
            : reviewRepository.findByRoomProductIdAndIsVisibleTrueOrderByCreatedAtDesc(roomProductId, pageable);

        return new PageResult<>(reviews.map(ReviewResponse::from));
    }

    /**
     * 내 리뷰 목록 조회
     *
     * @param userId 조회할 사용자 ID
     * @return 내가 작성한 리뷰 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(Long userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(ReviewResponse::from).toList();
    }

    /**
     * 리뷰 수정
     *
     * <p>작성자 본인만 수정 가능하다.
     *
     * @param userId   수정 요청자 ID
     * @param reviewId 수정할 리뷰 ID
     * @param request  변경할 별점/내용
     * @return 수정된 리뷰
     */
    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findByIdAndUserId(reviewId, userId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_REVIEW));

        review.update(request.getRating(), request.getContent());
        return ReviewResponse.from(review);
    }

    /**
     * 리뷰 삭제
     *
     * <p>작성자 본인만 삭제 가능하다.
     *
     * @param userId   삭제 요청자 ID
     * @param reviewId 삭제할 리뷰 ID
     */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = reviewRepository.findByIdAndUserId(reviewId, userId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_REVIEW));

        reviewRepository.delete(review);
    }
}
