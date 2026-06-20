package com.domain.booking.controller;

import com.domain.auth.dto.request.RegistrationRequest;
import com.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/booking")
public class BookingController {

  @PostMapping("/")
  public ApiResponse<Void> booking() {
    return ApiResponse.ok();
  }

}
