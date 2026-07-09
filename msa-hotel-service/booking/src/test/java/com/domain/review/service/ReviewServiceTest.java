package com.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.domain.booking.entity.Booking;
import com.domain.booking.repository.BookingRepository;
import com.domain.review.dto.response.ReviewResponse;
import com.domain.review.entity.Review;
import com.domain.review.repository.ReviewRepository;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomType;
import com.global.response.PageResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @InjectMocks
  private ReviewService reviewService;

  @Mock
  private ReviewRepository reviewRepository;
  @Mock
  private BookingRepository bookingRepository;

  /** 테스트용 리뷰 생성 (booking/roomProduct 연관 포함) */
  private Review createReview(Long id, Integer rating) {
    RoomProduct room = RoomProduct.builder()
        .name("디럭스 더블")
        .roomType(RoomType.DELUXE)
        .price(150000)
        .baseCapacity(2)
        .maxCapacity(4)
        .build();
    setField(room, "id", 1L);

    Booking booking = Booking.builder()
        .userId(10L)
        .roomProduct(room)
        .bookingNumber("BK-" + id)
        .arrDate(LocalDate.of(2026, 7, 1))
        .depDate(LocalDate.of(2026, 7, 3))
        .adultCount(2)
        .guestName("전필원")
        .guestPhone("010-0000-0000")
        .totPrice(300000)
        .build();
    setField(booking, "id", id);

    Review review = Review.builder()
        .userId(10L)
        .booking(booking)
        .roomProduct(room)
        .rating(rating)
        .content("좋았습니다")
        .build();
    setField(review, "id", id);
    return review;
  }

  @Test
  @DisplayName("객실 리뷰 목록 조회 - 별점 필터 없이 페이징 결과 반환")
  void getByRoom_withoutRating_returnsPagedResult() {
    Pageable pageable = PageRequest.of(0, 10);
    List<Review> reviews = List.of(createReview(1L, 5), createReview(2L, 4));
    // 전체 12건 중 첫 페이지 2건이 조회된 상황
    Page<Review> page = new PageImpl<>(reviews, pageable, 12);

    given(reviewRepository.findByRoomProductIdAndIsVisibleTrueOrderByCreatedAtDesc(1L, pageable))
        .willReturn(page);

    PageResult<ReviewResponse> result = reviewService.getByRoom(1L, null, pageable);

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(12);
    assertThat(result.getTotalPages()).isEqualTo(2);
    assertThat(result.getPageNumber()).isEqualTo(0);
    assertThat(result.getIsFirst()).isTrue();
    assertThat(result.getIsLast()).isFalse();
    assertThat(result.getContent().get(0).getRating()).isEqualTo(5);
  }

  @Test
  @DisplayName("객실 리뷰 목록 조회 - 별점 필터 지정 시 필터링 쿼리 호출")
  void getByRoom_withRating_usesRatingFilterQuery() {
    Pageable pageable = PageRequest.of(0, 10);
    List<Review> reviews = List.of(createReview(1L, 5));
    Page<Review> page = new PageImpl<>(reviews, pageable, 1);

    given(reviewRepository.findByRoomProductIdAndRatingAndIsVisibleTrueOrderByCreatedAtDesc(1L, 5, pageable))
        .willReturn(page);

    PageResult<ReviewResponse> result = reviewService.getByRoom(1L, 5, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getRating()).isEqualTo(5);
    assertThat(result.getIsLast()).isTrue();
  }

  @Test
  @DisplayName("객실 리뷰 목록 조회 - 결과 없으면 빈 페이지 반환")
  void getByRoom_noReviews_returnsEmptyPage() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Review> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    given(reviewRepository.findByRoomProductIdAndIsVisibleTrueOrderByCreatedAtDesc(1L, pageable))
        .willReturn(emptyPage);

    PageResult<ReviewResponse> result = reviewService.getByRoom(1L, null, pageable);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  /** 리플렉션으로 private 필드 값 설정 (id 등 빌더로 못 넣는 값) */
  private void setField(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = obj.getClass().getSuperclass();
      try {
        var field = superClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}