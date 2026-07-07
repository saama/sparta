package com.global.security;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

  private static final String KEY_PREFIX = "refresh:";

  private final StringRedisTemplate redisTemplate;

  @Value("${app.jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  public void save(Long userId, String refreshToken) {
    redisTemplate.opsForValue()
        .set(KEY_PREFIX + userId, refreshToken, refreshTokenExpiration, TimeUnit.MILLISECONDS);
  }

  public Optional<String> find(Long userId) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
  }

  public void delete(Long userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }
}
