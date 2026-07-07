package com.global.security;

import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  public JwtTokenProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
      @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  public String generateAccessToken(Long userId, String email, String role) {
    return buildToken(userId, email, role, accessTokenExpiration, "ACCESS");
  }

  public String generateRefreshToken(Long userId, String email, String role) {
    return buildToken(userId, email, role, refreshTokenExpiration, "REFRESH");
  }

  private String buildToken(Long userId, String email, String role, long expiration, String tokenType) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expiration);

    return Jwts.builder()
        .setSubject(String.valueOf(userId))
        .claim("email", email)
        .claim("role", role)
        .claim("type", tokenType)
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .signWith(secretKey, SignatureAlgorithm.HS256)
        .compact();
  }

  public Claims parseClaims(String token) {
    try {
      return Jwts.parserBuilder()
          .setSigningKey(secretKey)
          .build()
          .parseClaimsJws(token)
          .getBody();
    } catch (ExpiredJwtException e) {
      throw new DomainException(DomainExceptionCode.EXPIRED_TOKEN);
    } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
      throw new DomainException(DomainExceptionCode.INVALID_TOKEN);
    } catch (JwtException e) {
      throw new DomainException(DomainExceptionCode.INVALID_TOKEN);
    }
  }

  public Long getUserId(String token) {
    return Long.parseLong(parseClaims(token).getSubject());
  }

  public String getEmail(String token) {
    return parseClaims(token).get("email", String.class);
  }

  public String getRole(String token) {
    return parseClaims(token).get("role", String.class);
  }

  public boolean isAccessToken(String token) {
    return "ACCESS".equals(parseClaims(token).get("type", String.class));
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (DomainException e) {
      return false;
    }
  }
}
