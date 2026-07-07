package com.domain.review.controller;

import com.domain.review.dto.request.ReviewCreateRequest;
import com.domain.review.dto.request.ReviewUpdateRequest;
import com.domain.review.dto.response.ReviewResponse;
import com.domain.review.service.ReviewService;
import com.global.response.ApiResponse;
import com.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(reviewService.create(userDetails.getUserId(), request)));
    }

    @GetMapping("/api/rooms/{roomProductId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getByRoom(
        @PathVariable Long roomProductId,
        @RequestParam(required = false) Integer rating) {
        return ResponseEntity.ok(
            ApiResponse.ok(reviewService.getByRoom(roomProductId, rating)));
    }

    @GetMapping("/api/reviews/me")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews(
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(reviewService.getMyReviews(userDetails.getUserId())));
    }

    @PutMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
        @PathVariable Long reviewId,
        @Valid @RequestBody ReviewUpdateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(reviewService.update(userDetails.getUserId(), reviewId, request)));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long reviewId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        reviewService.delete(userDetails.getUserId(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
