package com.domain.auth.service;

import com.domain.auth.dto.request.LoginRequest;
import com.domain.auth.dto.request.RegistrationRequest;
import com.domain.auth.dto.response.LoginResponse;
import com.domain.user.entity.User;
import com.domain.user.repository.UserRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import com.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;


  @Transactional
  public void registration(RegistrationRequest request) {
    userRepository.save(User.builder()
        .username(request.getUsername())
        .name(request.getName())
        .phone(request.getPhone())
        .email(request.getEmail())
        .password(passwordEncoder.encode(request.getPassword()))
        .build());
  }

  @Transactional
  public LoginResponse login(LoginRequest loginRequest, HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            loginRequest.getEmail(),
            loginRequest.getPassword()
        )
    );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    securityContextRepository.saveContext(context, request, response);

    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

    return LoginResponse.builder()
        .userId(userDetails.getUserId())
        .email(userDetails.getEmail())
        .build();
  }

  public LoginResponse getLoginInfo(Authentication authentication) {
    try {
      Object principal = authentication.getPrincipal();
      Long userId = (Long) principal.getClass().getMethod("getUserId").invoke(principal);
      String email = (String) principal.getClass().getMethod("getEmail").invoke(principal);
      return LoginResponse.builder()
          .userId(userId)
          .email(email)
          .build();
    } catch (Exception e) {
      throw new DomainException(DomainExceptionCode.NOT_FOUND_USER);
    }
  }

}
