package com.domain.auth.service;

import com.domain.auth.dto.request.LoginRequest;
import com.domain.auth.dto.request.RegistrationRequest;
import com.domain.auth.dto.response.LoginResponse;
import com.domain.user.entity.User;
import com.domain.user.repository.UserRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import com.global.security.CustomUserDetails;
import com.global.security.JwtTokenProvider;
import com.global.security.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;

  @Transactional
  public void registration(RegistrationRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new DomainException(DomainExceptionCode.DUPLICATE_USERNAME);
    }
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DomainException(DomainExceptionCode.DUPLICATE_EMAIL);
    }
    userRepository.save(User.builder()
        .username(request.getUsername())
        .name(request.getName())
        .phone(request.getPhone())
        .email(request.getEmail())
        .password(passwordEncoder.encode(request.getPassword()))
        .build());
  }

  @Transactional
  public LoginResponse login(LoginRequest loginRequest) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            loginRequest.getEmail(),
            loginRequest.getPassword()
        )
    );

    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    String role = userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    String accessToken = jwtTokenProvider.generateAccessToken(userDetails.getUserId(),
        userDetails.getEmail(), role);
    String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getUserId(),
        userDetails.getEmail(), role);

    refreshTokenRepository.save(userDetails.getUserId(), refreshToken);

    return LoginResponse.builder()
        .userId(userDetails.getUserId())
        .email(userDetails.getEmail())
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();
  }

  public LoginResponse refresh(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new DomainException(DomainExceptionCode.INVALID_TOKEN);
    }
    if (jwtTokenProvider.isAccessToken(refreshToken)) {
      throw new DomainException(DomainExceptionCode.INVALID_TOKEN);
    }

    Long userId = jwtTokenProvider.getUserId(refreshToken);
    String email = jwtTokenProvider.getEmail(refreshToken);
    String role = jwtTokenProvider.getRole(refreshToken);

    String storedToken = refreshTokenRepository.find(userId)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.INVALID_TOKEN));

    if (!storedToken.equals(refreshToken)) {
      throw new DomainException(DomainExceptionCode.INVALID_TOKEN);
    }

    String newAccessToken = jwtTokenProvider.generateAccessToken(userId, email, role != null ? role : "USER");
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, email, role != null ? role : "USER");
    refreshTokenRepository.save(userId, newRefreshToken);

    return LoginResponse.builder()
        .userId(userId)
        .email(email)
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .build();
  }

  public void logout(Long userId) {
    refreshTokenRepository.delete(userId);
  }

  public LoginResponse getLoginInfo(Authentication authentication) {
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    return LoginResponse.builder()
        .userId(userDetails.getUserId())
        .email(userDetails.getEmail())
        .build();
  }
}
