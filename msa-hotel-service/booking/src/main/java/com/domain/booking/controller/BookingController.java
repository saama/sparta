package com.domain.booking.controller;

import com.domain.booking.dto.request.BookingCancelRequest;
import com.domain.booking.dto.request.BookingCreateRequest;
import com.domain.booking.dto.response.BookingResponse;
import com.domain.booking.service.BookingService;
import com.global.response.ApiResponse;
import com.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings")
public class BookingController {

  private final BookingService bookingService;

  @PostMapping
  public ApiResponse<BookingResponse> create(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody BookingCreateRequest request) {
    return ApiResponse.ok(bookingService.create(userDetails.getUserId(), request));
  }

  @GetMapping
  public ApiResponse<List<BookingResponse>> getMyBookings(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ApiResponse.ok(bookingService.getMyBookings(userDetails.getUserId()));
  }

  @GetMapping("/{id}")
  public ApiResponse<BookingResponse> getBooking(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long id) {
    return ApiResponse.ok(bookingService.getBooking(userDetails.getUserId(), id));
  }

  @PostMapping("/{id}/cancel")
  public ApiResponse<BookingResponse> cancel(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long id,
      @RequestBody BookingCancelRequest request) {
    return ApiResponse.ok(bookingService.cancel(userDetails.getUserId(), id, request));
  }
}
