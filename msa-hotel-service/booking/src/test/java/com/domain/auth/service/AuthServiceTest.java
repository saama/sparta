package com.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks
  private AuthService authService;

  @Mock
  private UserRepository userRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private AuthenticationManager authenticationManager;
  @Mock
  private JwtTokenProvider jwtTokenProvider;
  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Test
  @DisplayName("회원가입 성공")
  void registration_success() {
    RegistrationRequest request = new RegistrationRequest();
    setField(request, "username", "testuser");
    setField(request, "email", "test@email.com");
    setField(request, "password", "Password1!");
    setField(request, "name", "홍길동");
    setField(request, "phone", "010-1234-5678");

    given(userRepository.existsByUsername("testuser")).willReturn(false);
    given(userRepository.existsByEmail("test@email.com")).willReturn(false);
    given(passwordEncoder.encode(any())).willReturn("encodedPassword");

    authService.registration(request);

    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("회원가입 실패 - 중복 username")
  void registration_fail_duplicate_username() {
    RegistrationRequest request = new RegistrationRequest();
    setField(request, "username", "testuser");
    setField(request, "email", "test@email.com");

    given(userRepository.existsByUsername("testuser")).willReturn(true);

    assertThatThrownBy(() -> authService.registration(request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("이미 사용 중인 아이디");
  }

  @Test
  @DisplayName("회원가입 실패 - 중복 email")
  void registration_fail_duplicate_email() {
    RegistrationRequest request = new RegistrationRequest();
    setField(request, "username", "newuser");
    setField(request, "email", "dup@email.com");

    given(userRepository.existsByUsername("newuser")).willReturn(false);
    given(userRepository.existsByEmail("dup@email.com")).willReturn(true);

    assertThatThrownBy(() -> authService.registration(request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("이미 사용 중인 이메일");
  }

  @Test
  @DisplayName("로그인 성공 - 토큰 반환")
  void login_success() {
    LoginRequest request = new LoginRequest();
    setField(request, "email", "test@email.com");
    setField(request, "password", "Password1!");

    CustomUserDetails userDetails = new CustomUserDetails(1L, "test@email.com", "encoded", "USER");
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null,
        userDetails.getAuthorities());

    given(authenticationManager.authenticate(any())).willReturn(auth);
    given(jwtTokenProvider.generateAccessToken(1L, "test@email.com", "USER")).willReturn("accessToken");
    given(jwtTokenProvider.generateRefreshToken(1L, "test@email.com", "USER")).willReturn("refreshToken");

    LoginResponse response = authService.login(request);

    assertThat(response.getAccessToken()).isEqualTo("accessToken");
    assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
    assertThat(response.getUserId()).isEqualTo(1L);
    verify(refreshTokenRepository).save(1L, "refreshToken");
  }

  @Test
  @DisplayName("토큰 재발급 성공")
  void refresh_success() {
    String refreshToken = "validRefreshToken";

    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.isAccessToken(refreshToken)).willReturn(false);
    given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
    given(jwtTokenProvider.getEmail(refreshToken)).willReturn("test@email.com");
    given(refreshTokenRepository.find(1L)).willReturn(Optional.of(refreshToken));
    given(jwtTokenProvider.generateAccessToken(eq(1L), eq("test@email.com"), any())).willReturn("newAccess");
    given(jwtTokenProvider.generateRefreshToken(eq(1L), eq("test@email.com"), any())).willReturn("newRefresh");

    LoginResponse response = authService.refresh(refreshToken);

    assertThat(response.getAccessToken()).isEqualTo("newAccess");
    assertThat(response.getRefreshToken()).isEqualTo("newRefresh");
  }

  @Test
  @DisplayName("토큰 재발급 실패 - 저장된 토큰 불일치")
  void refresh_fail_token_mismatch() {
    String refreshToken = "validRefreshToken";

    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.isAccessToken(refreshToken)).willReturn(false);
    given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
    given(jwtTokenProvider.getEmail(refreshToken)).willReturn("test@email.com");
    given(refreshTokenRepository.find(1L)).willReturn(Optional.of("differentToken"));

    assertThatThrownBy(() -> authService.refresh(refreshToken))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("로그아웃 - Redis 토큰 삭제")
  void logout_success() {
    authService.logout(1L);
    verify(refreshTokenRepository).delete(1L);
  }

  private void setField(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
