package com.domain.review.dto.response;

import com.domain.review.entity.Review;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewResponse {

    Long id;
    Long userId;
    Long bookingId;
    Long roomProductId;
    String roomProductName;
    Integer rating;
    String content;
    Boolean isVisible;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static ReviewResponse from(Review review) {
        return ReviewResponse.builder()
            .id(review.getId())
            .userId(review.getUserId())
            .bookingId(review.getBooking().getId())
            .roomProductId(review.getRoomProduct().getId())
            .roomProductName(review.getRoomProduct().getName())
            .rating(review.getRating())
            .content(review.getContent())
            .isVisible(review.getIsVisible())
            .createdAt(review.getCreatedAt())
            .updatedAt(review.getUpdatedAt())
            .build();
    }
}
