package com.domain.auth.controller;

import com.domain.auth.dto.request.LoginRequest;
import com.domain.auth.dto.request.RegistrationRequest;
import com.domain.auth.dto.response.LoginResponse;
import com.domain.auth.service.AuthService;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import com.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  @PostMapping("registration")
  public ApiResponse<Void> registration(@Valid @RequestBody RegistrationRequest request) {
    authService.registration(request);
    return ApiResponse.ok();
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest,
      HttpServletRequest request, HttpServletResponse response) {
    LoginResponse loginResponse = authService.login(loginRequest, request, response);
    return ApiResponse.ok(loginResponse);
  }

  @GetMapping("/status")
  public ApiResponse<LoginResponse> checkStatus(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated() ||
        authentication instanceof AnonymousAuthenticationToken) {
      throw new DomainException(DomainExceptionCode.NOT_FOUND_USER);
    }

    return ApiResponse.ok(authService.getLoginInfo(authentication));
  }

  @GetMapping("/logout")
  public ApiResponse<Void> logout(HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    return ApiResponse.ok();
  }

}
