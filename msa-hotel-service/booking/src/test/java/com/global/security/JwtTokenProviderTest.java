package com.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.global.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  private static final String SECRET = "test-secret-key-for-junit-test-must-be-long-enough-256bits";
  private static final long ACCESS_EXPIRATION = 1800000L;
  private static final long REFRESH_EXPIRATION = 604800000L;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
  }

  @Test
  @DisplayName("Access Token 생성 및 검증 성공")
  void generateAndValidateAccessToken() {
    String token = jwtTokenProvider.generateAccessToken(1L, "test@email.com", "USER");

    assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);
    assertThat(jwtTokenProvider.getEmail(token)).isEqualTo("test@email.com");
    assertThat(jwtTokenProvider.isAccessToken(token)).isTrue();
  }

  @Test
  @DisplayName("Refresh Token 생성 및 검증 성공")
  void generateAndValidateRefreshToken() {
    String token = jwtTokenProvider.generateRefreshToken(1L, "test@email.com", "USER");

    assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);
    assertThat(jwtTokenProvider.isAccessToken(token)).isFalse();
  }

  @Test
  @DisplayName("만료된 토큰 검증 시 예외 발생")
  void expiredTokenThrowsException() {
    JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L, REFRESH_EXPIRATION);
    String token = shortLivedProvider.generateAccessToken(1L, "test@email.com", "USER");

    try {
      Thread.sleep(10);
    } catch (InterruptedException ignored) {}

    assertThatThrownBy(() -> shortLivedProvider.parseClaims(token))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("만료된 토큰");
  }

  @Test
  @DisplayName("잘못된 토큰 검증 시 false 반환")
  void invalidTokenReturnsFalse() {
    assertThat(jwtTokenProvider.validateToken("invalid.token.value")).isFalse();
  }
}
