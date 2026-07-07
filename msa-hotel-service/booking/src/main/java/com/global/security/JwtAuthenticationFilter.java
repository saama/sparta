package com.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.exception.DomainException;
import com.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String token = resolveToken(request);

    if (StringUtils.hasText(token)) {
      try {
        if (!jwtTokenProvider.isAccessToken(token)) {
          writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN",
              "Access Token이 아닙니다.");
          return;
        }
        Long userId = jwtTokenProvider.getUserId(token);
        String email = jwtTokenProvider.getEmail(token);
        String role = jwtTokenProvider.getRole(token);

        CustomUserDetails userDetails = new CustomUserDetails(userId, email, "", role != null ? role : "USER");

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (DomainException e) {
        writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, e.getCode(),
            e.getMessage());
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
      return bearerToken.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private void writeErrorResponse(HttpServletResponse response, int status, String code,
      String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    ApiResponse<Void> errorResponse = ApiResponse.<Void>builder()
        .error(ApiResponse.Error.of(code, message))
        .build();
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
