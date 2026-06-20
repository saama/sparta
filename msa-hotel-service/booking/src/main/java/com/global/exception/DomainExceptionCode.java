package com.global.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public enum DomainExceptionCode {

  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "잘못된 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
  MISSING_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 누락되었습니다."),
  UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "인증되지 않은 접근입니다."),
  JSON_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Json 데이터 처리 중 에러가 발생하였습니다."),

  NOT_FOUND_CATEGORY(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
  CATEGORY_HAS_CHILDREN(HttpStatus.BAD_REQUEST, "하위 카테고리가 존재하여 삭제할 수 없습니다."),

  NOT_FOUND_PRODUCT(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
  OUT_OF_STOCK_PRODUCT(HttpStatus.BAD_REQUEST, "재고 수량이 없습니다."),

  NOT_FOUND_USER(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

  NOT_FOUND_TASK(HttpStatus.NOT_FOUND, "작업 로그를 찾을 수 없습니다."),

  NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),
  CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "취소 불가능한 상태입니다."),

  ;

  final HttpStatus status;
  final String message;
}
