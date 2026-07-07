package com.domain.auth.controller;

import com.domain.auth.dto.request.LoginRequest;
import com.domain.auth.dto.request.RegistrationRequest;
import com.domain.auth.dto.response.LoginResponse;
import com.domain.auth.service.AuthService;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import com.global.response.ApiResponse;
import com.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/registration")
  public ApiResponse<Void> registration(@Valid @RequestBody RegistrationRequest request) {
    authService.registration(request);
    return ApiResponse.ok();
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
    return ApiResponse.ok(authService.login(loginRequest));
  }

  @PostMapping("/refresh")
  public ApiResponse<LoginResponse> refresh(
      @RequestHeader("Authorization") String bearerToken) {
    String refreshToken = bearerToken.replace("Bearer ", "");
    return ApiResponse.ok(authService.refresh(refreshToken));
  }

  @GetMapping("/status")
  public ApiResponse<LoginResponse> checkStatus(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated() ||
        authentication instanceof AnonymousAuthenticationToken) {
      throw new DomainException(DomainExceptionCode.UNAUTHORIZED_ACCESS);
    }
    return ApiResponse.ok(authService.getLoginInfo(authentication));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(Authentication authentication) {
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    authService.logout(userDetails.getUserId());
    return ApiResponse.ok();
  }
}
