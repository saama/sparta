# 🏨 호텔 객실 예약 서비스 - 프로젝트 가이드

> MSA 4기 최종 프로젝트 | 개인 프로젝트 | Spring Boot 3.2.11 / Java 21

---

## 📖 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [DB 스키마](#3-db-스키마)
4. [기능 목록](#4-기능-목록)
5. [API 엔드포인트 설계](#5-api-엔드포인트-설계)
6. [주차별 작업 플랜](#6-주차별-작업-플랜)
7. [제출 방법](#7-제출-방법)

---

## 1. 프로젝트 개요

### 서비스 소개
로그인한 사용자가 호텔 객실을 검색하고 예약할 수 있는 서비스입니다.
쿠폰 할인, 결제, 리뷰 작성 기능을 포함하며, 동시 다발적인 예약 요청에도 오버부킹 없이 안전하게 처리하는 것을 핵심 목표로 합니다.

### MVP 정의
> 사용자가 회원가입/로그인 후, 날짜와 인원을 선택하여 객실을 조회하고, 쿠폰을 적용하여 예약 및 결제까지 완료할 수 있다.

### 프로젝트 목표
- JWT 기반 인증/인가 구현
- 동시성 이슈(오버부킹) 해결 - DB Lock 메커니즘 적용
- Redis 캐싱으로 객실 조회 성능 최적화
- Kafka 이벤트 기반 예약 처리 흐름 구현
- Docker로 전체 인프라 컨테이너화
- ELK Stack으로 로그 수집 및 모니터링

---

## 2. 기술 스택

> 체크박스로 도입 여부를 관리합니다. `[x]` = 도입, `[ ]` = 미도입/보류

### 언어 & 프레임워크
- [x] Java 21
- [x] Spring Boot 3.2.11
- [x] Spring Data JPA + Hibernate
- [x] QueryDSL (예약 목록 필터링/검색)
- [x] MapStruct (Entity ↔ DTO 변환)
- [x] Spring Validation (입력값 검증)

### 인증 / 보안
- [ ] ~~Spring Session + Redis~~ → **JWT 방식으로 교체**
- [x] JWT (jjwt 0.11.5) - Access Token + Refresh Token
- [x] Spring Security
- [x] BCrypt 비밀번호 암호화

### 데이터베이스
- [x] MySQL 8.0
- [x] Flyway (DB 마이그레이션)
- [x] 인덱스 설계 (booking, room_stock 핵심 쿼리 최적화)

### 캐싱
- [ ] Redis 캐싱 - 객실 상품 목록 캐싱 (`@Cacheable`)
- [ ] Redis 분산 락 - 선착순 쿠폰 발급 동시성 처리

### 동시성 처리 (6~7주차 학습 적용)
- [x] 비관적 락 (`@Lock(PESSIMISTIC_WRITE)`) - 객실 재고 차감
- [x] 낙관적 락 (`@Version`) - 쿠폰 수량 차감
- [x] 트랜잭션 격리 수준 설정 - 예약 생성 시 REPEATABLE_READ
- [x] 트랜잭션 전파 옵션 - 결제 실패 시 롤백 전략

### 외부 연동
- [x] OpenFeign - 가상 PG사 결제 API 연동 (Mock PgClient)
- [ ] Spring Retry - PG API 실패 시 재시도

### 메시징 (11주차 학습 적용)
- [x] Kafka Producer - 예약 생성 이벤트 발행 (`booking-events`)
- [x] Kafka Consumer - 결제 완료 이벤트 수신 → 예약 확정 처리

### 모니터링 & 로깅 (10주차 학습 적용)
- [ ] Spring AOP - API 요청/응답 로깅, 실행 시간 측정
- [ ] ELK Stack (Elasticsearch + Logstash + Kibana)
- [x] Prometheus + Grafana - 서버 메트릭 시각화
- [x] Spring Actuator (health, prometheus, metrics 노출)

### 배포 (8주차 학습 적용)
- [x] Dockerfile - 앱 컨테이너화
- [x] docker-compose - MySQL, Redis, Kafka 통합
- [ ] docker-compose 확장 - ELK Stack 추가

### 테스트 (12주차 학습 적용)
- [x] 단위 테스트 - Service 레이어 (JUnit5 + Mockito) - 38개 케이스 통과
- [ ] E2E 시나리오 테스트 - 회원가입 → 예약 → 결제 흐름

### 검색 (Could-Have)
- [ ] Elasticsearch - 객실 상품 통합 검색

### 기타 (Could-Have)
- [ ] Kubernetes 배포 (K8s Deployment, Service, Ingress)
- [ ] Spring AI - 객실 추천 챗봇

---

## 3. DB 스키마

### 현재 구성 (Flyway V1 ~ V5 적용 완료)

```
user            - 회원 정보 (role 컬럼 추가: USER | ADMIN)
room_product    - 객실 상품 (STANDARD / DELUXE / SUITE)
room_stock      - 날짜별 객실 재고
coupon          - 쿠폰 마스터 (FIXED / PERCENT)
user_coupon     - 유저 쿠폰 발급 이력
booking         - 예약 (PENDING → CONFIRMED → COMPLETED / CANCELLED)
payment         - 결제 (PENDING → PAID → REFUNDED)
review          - 리뷰 (투숙 완료 예약에 한해 작성 가능)
```

### 인덱스 설계 현황
| 테이블 | 인덱스 | 목적 |
|---|---|---|
| `room_stock` | `(room_product_id, date)` UNIQUE | 날짜별 재고 단건 조회 |
| `room_stock` | `date` | 날짜 범위 재고 조회 |
| `booking` | `booking_number` UNIQUE | 예약 번호 단건 조회 |
| `booking` | `user_id` | 마이페이지 예약 목록 조회 |
| `booking` | `(arr_date, dep_date)` | 체크인/아웃 날짜 범위 조회 |
| `booking` | `status` | 예약 상태별 필터링 |
| `user_coupon` | `(user_id, coupon_id)` UNIQUE | 중복 발급 방지 |

### 추가 고려 스키마 (필요 시)
- [ ] `refresh_token` 테이블 - JWT Refresh Token 저장 (또는 Redis 저장)
- [ ] `booking_status_history` 테이블 - 예약 상태 변경 이력 관리

---

## 4. 기능 목록

### Must-Have (핵심 기능)

#### 회원 기능
- [x] 회원가입 (username, email, password, name, phone)
- [x] 이메일/비밀번호 유효성 검증 (특수문자, 숫자 포함)
- [x] JWT 로그인 (Access Token + Refresh Token 발급)
- [x] 토큰 재발급 (Refresh Token)
- [x] 로그아웃 (Refresh Token 무효화)
- [ ] 내 정보 조회/수정
- [ ] 회원 탈퇴 (is_active = false)

#### 객실 상품 기능
- [x] 객실 상품 목록 조회 (체크인/아웃 날짜, 인원수 기준 필터링)
- [x] 객실 상품 상세 조회
- [x] 객실 상품 등록/수정/삭제 (어드민)
- [x] 날짜별 재고 초기화 (어드민)

#### 예약 기능
- [x] 예약 생성 (객실 재고 확인 → 쿠폰 적용)
- [x] 예약 목록 조회 (마이페이지)
- [x] 예약 상세 조회
- [x] 예약 취소 (PENDING/CONFIRMED 상태에서 가능)
- [x] 재고 동시성 처리 (비관적 락으로 오버부킹 방지)

#### 결제 기능
- [x] 결제 생성 (가상 PG API 연동)
- [x] 결제 완료 → 예약 상태 CONFIRMED 변경
- [x] 결제 취소 → 환불 처리

### Should-Have (주요 기능)

#### 쿠폰 기능
- [x] 쿠폰 발급 (어드민)
- [x] 보유 쿠폰 목록 조회
- [x] 쿠폰 적용 (FIXED / PERCENT)
- [x] 쿠폰 유효성 검증 (유효기간, 최소 주문 금액)
- [ ] 선착순 쿠폰 (동시성 처리 - Redis INCR 또는 낙관적 락)

#### 리뷰 기능
- [x] 리뷰 작성 (COMPLETED 상태 예약에 한해 1회)
- [x] 리뷰 수정/삭제
- [x] 예약 별 리뷰 목록 조회 (별점 필터링)

#### 모니터링
- [x] Spring AOP 로깅 (API 요청/응답 + 실행 시간)
- [ ] ELK Stack 로그 파이프라인 구성
- [x] Prometheus + Grafana 메트릭 대시보드

### Could-Have (부가 기능)
- [ ] 객실 상품 검색 (Elasticsearch)
- [x] Kubernetes 배포
- [x] 객실 추천 챗봇 (Spring AI)
- [ ] 예약 확정 이메일/SMS 알림

---

## 5. API 엔드포인트 설계

### Auth `/api/auth`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/auth/registration` | 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | 불필요 |
| POST | `/api/auth/refresh` | Access Token 재발급 | Refresh Token |
| GET | `/api/auth/logout` | 로그아웃 | 필요 |
| GET | `/api/auth/status` | 로그인 상태 확인 | 필요 |

### User `/api/users`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/users/me` | 내 정보 조회 | 필요 |
| PUT | `/api/users/me` | 내 정보 수정 | 필요 |
| PUT | `/api/users/me/password` | 비밀번호 변경 | 필요 |
| DELETE | `/api/users/me` | 회원 탈퇴 | 필요 |

### Room Product `/api/rooms`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/rooms` | 객실 목록 조회 (날짜/인원 필터) | 불필요 |
| GET | `/api/rooms/{id}` | 객실 상세 조회 | 불필요 |
| POST | `/api/admin/rooms` | 객실 등록 | 어드민 |
| PUT | `/api/admin/rooms/{id}` | 객실 수정 | 어드민 |
| DELETE | `/api/admin/rooms/{id}` | 객실 삭제 | 어드민 |
| POST | `/api/admin/rooms/{id}/stock` | 날짜별 재고 초기화 | 어드민 |

### Booking `/api/bookings`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/bookings` | 예약 생성 | 필요 |
| GET | `/api/bookings` | 내 예약 목록 조회 | 필요 |
| GET | `/api/bookings/{id}` | 예약 상세 조회 | 필요 |
| POST | `/api/bookings/{id}/cancel` | 예약 취소 | 필요 |

### Payment `/api/payments`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/payments` | 결제 요청 | 필요 |
| POST | `/api/payments/{id}/cancel` | 결제 취소/환불 | 필요 |

### Coupon `/api/coupons`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/coupons/me` | 보유 쿠폰 목록 | 필요 |
| POST | `/api/coupons/{couponId}/issue` | 쿠폰 발급 | 필요 |
| POST | `/api/admin/coupons` | 쿠폰 생성 | 어드민 |

### Review `/api/reviews`
| Method | URL | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/reviews` | 리뷰 작성 | 필요 |
| GET | `/api/rooms/{id}/reviews` | 객실 리뷰 목록 | 불필요 |
| PUT | `/api/reviews/{id}` | 리뷰 수정 | 필요 |
| DELETE | `/api/reviews/{id}` | 리뷰 삭제 | 필요 |

---

## 6. 주차별 작업 플랜

> 1주차 완료 ✅ | 2주차 완료 ✅ | 현재 3주차 진행 중 (리뷰/캐싱/AOP 로깅/ELK)

### 1주차 - 인프라 정비 & 핵심 API 뼈대 ✅

**목표:** 인증 방식을 JWT로 교체하고, 예약 Happy Path 흐름 완성

#### 인증 교체
- [x] `spring-session-data-redis` 제거 → JWT 방식으로 교체
- [x] `JwtTokenProvider` 작성 (Access Token 생성/검증, role 클레임 포함)
- [x] `JwtAuthenticationFilter` 작성 (요청마다 토큰 검증)
- [x] `SecurityConfig` 수정 (Stateless, JWT 필터 등록, ADMIN 인가 처리)
- [x] Refresh Token 처리 (Redis 저장)
- [x] `AuthController` - `/refresh`, `/logout` 엔드포인트 추가
- [x] User 테이블 `role` 컬럼 추가 (Flyway V3 마이그레이션)

#### 객실 상품 API
- [x] `RoomProduct` 엔티티 작성
- [x] `RoomStock` 엔티티 작성
- [x] `RoomProductRepository`, `RoomStockRepository` 작성
- [x] 객실 목록/상세 조회 API (날짜+인원 필터링)
- [x] 어드민 객실 등록/수정/삭제 API
- [x] 날짜별 재고 초기화 API

#### 예약 기본 흐름
- [x] `Booking` 엔티티 작성
- [x] `BookingService` - 예약 생성 (재고 확인 → 예약 저장)
- [x] 예약 목록/상세 조회 API
- [x] 예약 취소 API (PENDING/CONFIRMED 상태)

---

### 2주차 - 핵심 비즈니스 로직 & 동시성 처리 ✅

**목표:** 쿠폰/결제/취소 로직 완성 + 동시성 이슈 해결

#### 쿠폰
- [x] `Coupon`, `UserCoupon` 엔티티 작성
- [x] 쿠폰 발급 API (선착순 - 낙관적 락 `@Version`)
- [x] 쿠폰 적용 로직 (FIXED / PERCENT 할인 계산)
- [x] 쿠폰 유효성 검증 (유효기간, 최소 주문 금액)

#### 결제
- [x] `Payment` 엔티티 작성
- [x] 가상 PG 클라이언트 구현 (`PgClient` - Mock)
- [x] 결제 생성 → 예약 상태 CONFIRMED 변경 (트랜잭션)
- [x] 결제 취소 → 환불 + 재고 원복 + 쿠폰 복구

#### 동시성 처리
- [x] 객실 재고 차감 - **비관적 락** (`@Lock(PESSIMISTIC_WRITE)`)
- [x] 선착순 쿠폰 발급 - **낙관적 락** (`@Version`)
- [x] 트랜잭션 격리 수준 설정 (`REPEATABLE_READ`)

#### Kafka 이벤트
- [x] `BookingCreatedEvent` Producer (예약 생성 시 발행)
- [x] `PaymentCompletedEvent` Consumer (결제 완료 → 예약 확정)

---

### 3주차 - 고도화 & 모니터링

**목표:** 리뷰, 캐싱, AOP 로깅, ELK/Prometheus 구축

#### 리뷰
- [ ] `Review` 엔티티 작성
- [ ] 리뷰 작성/수정/삭제 API
- [ ] 리뷰 목록 조회 (별점 필터링, 페이징)

#### Redis 캐싱
- [ ] 객실 상품 목록 캐싱 (`@Cacheable`)
- [ ] 캐시 무효화 (객실 정보 수정 시 `@CacheEvict`)

#### AOP 로깅 (12주차 학습 적용)
- [ ] `@Around` - API 요청/응답 + 실행 시간 로깅
- [ ] 예외 발생 시 로그 레벨 구분

#### 모니터링 인프라
- [ ] `docker-compose` 확장 - Logstash, Elasticsearch, Kibana 추가
- [ ] Logstash 파이프라인 설정 (앱 로그 → ES 인덱싱)
- [ ] Kibana 대시보드 구성 (예약/결제/에러 로그)
- [ ] Prometheus + Grafana 연동 (CPU, 메모리, API 응답시간)

---

### 4주차 - 마무리 & QA

**목표:** 배포 환경 완성, 테스트, 문서화

#### 배포
- [ ] `docker-compose` 최종 통합 (전체 서비스 네트워크 연결)
- [ ] 환경 변수 분리 (`.env` 파일)
- [ ] 헬스체크 설정 확인

#### 테스트
- [ ] Service 레이어 단위 테스트 (JUnit5 + Mockito)
- [ ] Postman E2E 시나리오: 회원가입 → 로그인 → 객실 조회 → 쿠폰 발급 → 예약 → 결제 → 취소

#### Could-Have (시간 여유 시)
- [ ] Elasticsearch 객실 검색 기능
- [ ] Kubernetes 배포 (Minikube)
- [ ] `booking_status_history` 테이블 추가 (상태 변경 이력)
- [ ] Spring AI 객실 추천 챗봇

#### 코드 정리
- [ ] `DomainExceptionCode` 불필요 코드 정리 (이전 프로젝트 잔재 제거)
- [ ] `User.name` unique 제약 제거 검토 (이름은 유니크하면 안됨)
- [ ] README.md 최종 작성

---

## 7. 제출 방법

### Git 브랜치 전략
```
작업 브랜치  : work/{팀번호}-{영문이름}   (예: work/1-pilwon-jeon)
제출 브랜치  : project/{팀번호}-{영문이름} (예: project/1-pilwon-jeon)
```

### 제출 순서
1. 작업 브랜치에서 개발 후 `commit` + `push`
2. 작업 브랜치 → 제출 브랜치로 PR 생성
3. PR 링크를 구글폼으로 제출: https://forms.gle/DPt4zH7evNcSszkw9
4. 리뷰 완료 후 PR 병합

---

## 📌 현재 코드 Known Issues

| 위치 | 내용 | 우선순위 |
|---|---|---|
| `User.java:42` | `name` 컬럼에 `unique=true` - 이름은 유니크하면 안됨 | 높음 |
| `DomainExceptionCode.java` | `NOT_FOUND_CATEGORY`, `NOT_FOUND_PRODUCT` 등 이전 프로젝트 잔재 | 중간 |
