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

  NOT_FOUND_USER(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
  DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

  NOT_FOUND_ROOM(HttpStatus.NOT_FOUND, "객실 상품을 찾을 수 없습니다."),
  OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "선택한 날짜에 예약 가능한 객실이 없습니다."),

  NOT_FOUND_BOOKING(HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."),
  DUPLICATE_BOOKING(HttpStatus.CONFLICT, "이미 동일한 날짜에 예약이 존재합니다."),
  INVALID_BOOKING_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
  CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "취소 불가능한 상태입니다."),

  NOT_FOUND_COUPON(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
  ALREADY_USED_COUPON(HttpStatus.BAD_REQUEST, "이미 사용된 쿠폰입니다."),
  EXPIRED_COUPON(HttpStatus.BAD_REQUEST, "유효기간이 만료된 쿠폰입니다."),
  NOT_MET_MIN_PRICE(HttpStatus.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다."),

  NOT_FOUND_PAYMENT(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
  PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제 처리에 실패하였습니다."),

  NOT_FOUND_REVIEW(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
  DUPLICATE_REVIEW(HttpStatus.CONFLICT, "이미 리뷰를 작성하였습니다."),
  UNAUTHORIZED_REVIEW(HttpStatus.FORBIDDEN, "리뷰 작성 권한이 없습니다."),

  ;

  final HttpStatus status;
  final String message;
}
