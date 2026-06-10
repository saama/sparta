-- ============================================================
-- Flyway Migration: V1__create_booking_schema.sql
-- Description : 객실 예약 시스템 전체 스키마 초기 생성
-- Author      : pilwon
-- Created     : 2026-06-10
-- ============================================================


-- ============================================================
-- 1. USER (유저 테이블)
--    서비스를 이용하는 회원 정보를 관리한다.
-- ============================================================
CREATE TABLE `user` (
                        `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '유저 PK',
                        `username`   VARCHAR(50)  NOT NULL                 COMMENT '로그인 아이디 (유니크)',
                        `password`   VARCHAR(255) NOT NULL                 COMMENT '비밀번호 (BCrypt 등 해시 저장)',
                        `email`      VARCHAR(100) NOT NULL                 COMMENT '이메일 (유니크)',
                        `name`       VARCHAR(50)  NOT NULL                 COMMENT '실명 (예약 확인서 발급용)',
                        `phone`      VARCHAR(20)      NULL                 COMMENT '연락처 (체크인 안내 SMS 등)',
                        `is_active`  TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성 여부 : 1=활성, 0=탈퇴/휴면',
                        `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                        `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uq_user_username` (`username`),
                        UNIQUE KEY `uq_user_email`    (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='유저 테이블';


-- ============================================================
-- 2. ROOM_PRODUCT (객실 상품 테이블)
--    판매 가능한 객실 상품 정보를 관리한다.
--    실제 재고는 ROOM_STOCK 테이블에서 날짜별로 관리한다.
-- ============================================================
CREATE TABLE `room_product` (
                                `id`            BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '객실 상품 PK',
                                `name`          VARCHAR(100)  NOT NULL                 COMMENT '객실 상품명 (예: 디럭스 더블)',
                                `room_type`     VARCHAR(20)   NOT NULL                 COMMENT '객실 유형 : STANDARD | DELUXE | SUITE',
                                `price`         INT           NOT NULL                 COMMENT '1박 기준 기본 가격 (원)',
                                `base_capacity` INT           NOT NULL DEFAULT 2       COMMENT '기준 인원 수',
                                `max_capacity`  INT           NOT NULL DEFAULT 4       COMMENT '최대 수용 인원 수',
                                `description`   TEXT              NULL                 COMMENT '객실 상세 설명',
                                `thumbnail_url` VARCHAR(500)      NULL                 COMMENT '대표 이미지 URL',
                                `is_active`     TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '판매 여부 : 1=판매중, 0=판매중단',
                                `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                                `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='객실 상품 테이블';


-- ============================================================
-- 3. ROOM_STOCK (객실 재고 테이블)
--    날짜별 객실 잔여 재고를 관리한다.
--    예약 생성/취소 시 stock 값을 증감한다.
-- ============================================================
CREATE TABLE `room_stock` (
                              `id`              BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '재고 PK',
                              `room_product_id` BIGINT   NOT NULL                 COMMENT 'room_product.id FK',
                              `date`            DATE     NOT NULL                 COMMENT '재고 기준 날짜',
                              `stock`           INT      NOT NULL DEFAULT 0       COMMENT '잔여 재고 수량',
                              `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                              `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                              PRIMARY KEY (`id`),
    -- (상품+날짜) 조합으로 재고를 단건 조회하는 패턴이 핵심 쿼리이므로 유니크 인덱스로 중복 방지 + 조회 성능 확보
                              UNIQUE KEY `uq_room_stock_product_date` (`room_product_id`, `date`) COMMENT '상품+날짜 조합 유니크',
    -- 특정 날짜 범위의 재고를 전체 상품에 걸쳐 조회할 때 date 단독 인덱스가 필요
                              KEY `idx_room_stock_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='객실 재고 테이블';


-- ============================================================
-- 4. COUPON (쿠폰 테이블)
--    할인 쿠폰 마스터 정보를 관리한다.
--    discount_type 에 따라 정액(FIXED) 또는 정률(PERCENT) 할인을 적용한다.
-- ============================================================
CREATE TABLE `coupon` (
                          `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '쿠폰 PK',
                          `code`           VARCHAR(50)  NOT NULL                 COMMENT '쿠폰 코드 (유니크)',
                          `name`           VARCHAR(100) NOT NULL                 COMMENT '쿠폰명 (예: 신규가입 10% 할인)',
                          `discount_type`  VARCHAR(10)  NOT NULL                 COMMENT '할인 유형 : FIXED=정액 | PERCENT=정률',
                          `discount_value` INT          NOT NULL                 COMMENT '할인 값 (정액: 원, 정률: %)',
                          `min_price`      INT          NOT NULL DEFAULT 0       COMMENT '최소 주문 금액 (원)',
                          `max_discount`   INT              NULL                 COMMENT '최대 할인 한도 (정률 쿠폰 전용, 원)',
                          `issue_limit`    INT              NULL                 COMMENT '총 발급 가능 수량 (NULL=무제한)',
                          `valid_from`     DATE         NOT NULL                 COMMENT '사용 가능 시작일',
                          `valid_until`    DATE         NOT NULL                 COMMENT '사용 가능 종료일',
                          `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                          `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uq_coupon_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='쿠폰 마스터 테이블';


-- ============================================================
-- 5. USER_COUPON (유저 쿠폰 발급 테이블)
--    유저에게 발급된 쿠폰을 관리한다.
--    사용 여부와 사용 일시를 추적한다.
-- ============================================================
CREATE TABLE `user_coupon` (
                               `id`         BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '발급 쿠폰 PK',
                               `user_id`    BIGINT   NOT NULL                 COMMENT 'user.id FK',
                               `coupon_id`  BIGINT   NOT NULL                 COMMENT 'coupon.id FK',
                               `is_used`    TINYINT(1) NOT NULL DEFAULT 0     COMMENT '사용 여부 : 1=사용완료',
                               `used_at`    DATETIME     NULL                 COMMENT '쿠폰 사용 일시',
                               `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                               `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 일시',
                               PRIMARY KEY (`id`),
    -- 1인 1쿠폰 중복 발급을 DB 레벨에서 방지
                               UNIQUE KEY `uq_user_coupon` (`user_id`, `coupon_id`) COMMENT '1인 1쿠폰 중복 발급 방지',
    -- 특정 유저의 보유 쿠폰 목록 조회 시 풀스캔 방지
                               KEY `idx_user_coupon_user`   (`user_id`),
    -- 특정 쿠폰의 발급 현황(발급 수량 집계 등) 조회 시 풀스캔 방지
                               KEY `idx_user_coupon_coupon` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='유저 쿠폰 발급 테이블';


-- ============================================================
-- 6. BOOKING (예약 테이블)
--    예약 핵심 정보를 관리한다.
--    실제 투숙객이 예약자와 다를 수 있으므로 guest 컬럼을 분리한다.
--    쿠폰 적용 이력 추적을 위해 user_coupon_id 를 보관한다.
-- ============================================================
CREATE TABLE `booking` (
                           `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '예약 PK',
                           `user_id`         BIGINT       NOT NULL                 COMMENT 'user.id FK (예약자)',
                           `room_product_id` BIGINT       NOT NULL                 COMMENT 'room_product.id FK',
                           `user_coupon_id`  BIGINT           NULL                 COMMENT 'user_coupon.id FK (쿠폰 사용 시)',
                           `booking_number`  VARCHAR(30)  NOT NULL                 COMMENT '예약 번호 (외부 노출용, 유니크)',
                           `booking_date`    DATETIME     NOT NULL                 COMMENT '예약 접수 일시',
                           `arr_date`        DATE         NOT NULL                 COMMENT '체크인 날짜',
                           `dep_date`        DATE         NOT NULL                 COMMENT '체크아웃 날짜',
                           `adult_count`     INT          NOT NULL DEFAULT 1       COMMENT '성인 인원 수',
                           `child_count`     INT          NOT NULL DEFAULT 0       COMMENT '아동 인원 수',
                           `guest_name`      VARCHAR(50)      NULL                 COMMENT '실제 투숙객 이름 (예약자와 다를 경우)',
                           `guest_phone`     VARCHAR(20)      NULL                 COMMENT '실제 투숙객 연락처',
                           `request_memo`    VARCHAR(500)     NULL                 COMMENT '고객 요청사항 (특별 요청, 메모 등)',
                           `tot_price`       INT          NOT NULL                 COMMENT '최종 결제 금액 (쿠폰 할인 후, 원)',
                           `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '예약 상태 : PENDING=결제대기 | CONFIRMED=예약확정 | CANCELLED=취소 | COMPLETED=이용완료',
                           `cancel_date`     DATETIME         NULL                 COMMENT '취소 일시',
                           `cancel_reason`   VARCHAR(200)     NULL                 COMMENT '취소 사유',
                           `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                           `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                           PRIMARY KEY (`id`),
    -- 예약 번호는 외부에 노출되는 식별자이므로 중복 불가 + 단건 조회 성능 확보
                           UNIQUE KEY `uq_booking_number` (`booking_number`),
    -- 마이페이지 등 특정 유저의 예약 목록 조회 시 풀스캔 방지
                           KEY `idx_booking_user`         (`user_id`),
    -- 특정 객실 상품의 예약 현황 조회 시 풀스캔 방지
                           KEY `idx_booking_room_product` (`room_product_id`),
    -- 체크인/체크아웃 날짜 범위로 예약 조회(재고 검증 등)하는 쿼리가 빈번하므로 복합 인덱스 추가
                           KEY `idx_booking_arr_dep`      (`arr_date`, `dep_date`),
    -- 예약 상태별 필터링(CONFIRMED 목록 조회 등) 시 풀스캔 방지
                           KEY `idx_booking_status`       (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='예약 테이블';


-- ============================================================
-- 7. PAYMENT (결제 테이블)
--    예약별 결제 및 환불 이력을 관리한다.
--    예약과 분리하여 부분 환불, 재결제 등을 유연하게 처리한다.
-- ============================================================
CREATE TABLE `payment` (
                           `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '결제 PK',
                           `booking_id`     BIGINT       NOT NULL                 COMMENT 'booking.id FK',
                           `payment_method` VARCHAR(20)  NOT NULL                 COMMENT '결제 수단 : CARD | BANK_TRANSFER | KAKAO_PAY | NAVER_PAY | TOSS',
                           `payment_status` VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '결제 상태 : PENDING=결제대기 | PAID=결제완료 | REFUNDED=전액환불 | PARTIAL_REFUNDED=부분환불 | FAILED=실패',
                           `paid_amount`    INT          NOT NULL DEFAULT 0       COMMENT '실제 결제 금액 (원)',
                           `refund_amount`  INT          NOT NULL DEFAULT 0       COMMENT '환불 금액 (원)',
                           `tid`            VARCHAR(100)     NULL                 COMMENT 'PG사 거래 고유 번호',
                           `paid_at`        DATETIME         NULL                 COMMENT '결제 완료 일시',
                           `refunded_at`    DATETIME         NULL                 COMMENT '환불 완료 일시',
                           `fail_reason`    VARCHAR(200)     NULL                 COMMENT '결제 실패 사유',
                           `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                           PRIMARY KEY (`id`),
    -- 특정 예약의 결제 이력 조회 시 풀스캔 방지 (예약 상세 페이지, 환불 처리 등)
                           KEY `idx_payment_booking` (`booking_id`),
    -- 결제 상태별 정산/관리 쿼리(PAID 건 집계 등) 시 풀스캔 방지
                           KEY `idx_payment_status`  (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='결제 테이블';


-- ============================================================
-- 8. REVIEW (리뷰 테이블)
--     실제 투숙 완료된 예약에 대해서만 리뷰를 작성할 수 있다.
--     booking_id 기반으로 작성 권한을 검증한다.
-- ============================================================
CREATE TABLE `review` (
                          `id`              BIGINT    NOT NULL AUTO_INCREMENT  COMMENT '리뷰 PK',
                          `booking_id`      BIGINT    NOT NULL                 COMMENT 'booking.id FK (실투숙 확인용)',
                          `user_id`         BIGINT    NOT NULL                 COMMENT 'user.id FK (작성자)',
                          `room_product_id` BIGINT    NOT NULL                 COMMENT 'room_product.id FK (조회 편의용 역정규화)',
                          `rating`          TINYINT   NOT NULL                 COMMENT '별점 : 1~5',
                          `content`         TEXT          NULL                 COMMENT '리뷰 본문',
                          `is_visible`      TINYINT(1) NOT NULL DEFAULT 1      COMMENT '노출 여부 : 1=노출, 0=관리자 숨김',
                          `updated_at`      DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                          `created_at`      DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                          PRIMARY KEY (`id`),
    -- 예약 1건당 리뷰 1개로 제한 (중복 작성 방지)
                          UNIQUE KEY `uq_review_booking`    (`booking_id`) COMMENT '예약 1건당 리뷰 1개',
    -- 특정 유저가 작성한 리뷰 목록 조회 시 풀스캔 방지
                          KEY `idx_review_user`             (`user_id`),
    -- 특정 객실 상품의 리뷰 목록 조회 시 풀스캔 방지 (상품 상세 페이지)
                          KEY `idx_review_room_product`     (`room_product_id`),
    -- 별점 필터링(4점 이상 리뷰만 표시 등) 또는 평균 별점 집계 쿼리 시 성능 확보
                          KEY `idx_review_rating`           (`rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='리뷰 테이블';
