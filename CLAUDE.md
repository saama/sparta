# CLAUDE.md

이 파일은 이 저장소에서 작업하는 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 저장소 구조

이 저장소는 궁극적으로 멀티 서비스 MSA 프로젝트를 목표로 하지만, 현재는 단일 서비스만 구현되어 있습니다.

- `msa-hotel-service/booking/` — 호텔 플랫폼 전체(인증, 회원, 객실, 예약, 쿠폰, 결제, 리뷰 도메인)를 구현한 Spring Boot 앱(Gradle 프로젝트명 `booking-service`)입니다. `docker-compose.yml`에서는 이 디렉토리를 빌드해 `user-service` 컨테이너로 실행합니다 — 디렉토리 이름이 "booking"이라고 해서 예약 로직만 있다고 오해하지 말 것.
- `msa-hotel-service/payment/` — 현재 비어 있는 placeholder 디렉토리입니다. 실제 결제 로직은 `booking/src/main/java/com/domain/payment`에 있습니다.
- `msa-hotel-service/monitoring/` — 루트 `docker-compose.yml`이 사용하는 Grafana/Prometheus/Logstash 설정입니다.
- `msa-hotel-service/docker-compose.yml` — 전체 스택: `booking/docker-compose.local.yml`을 `include`로 가져온 뒤(공용 인프라), 전체 스택 전용 서비스(user-service 앱 컨테이너, 바운디드 컨텍스트별 추가 DB: bookingdb/stockdb/paymentdb, ELK, Prometheus, Grafana)를 정의한다. 공용 인프라 서비스를 이 파일에 중복 정의하지 말 것.
- `msa-hotel-service/booking/docker-compose.local.yml` — 공용 핵심 인프라(user-db MySQL, Redis, Kafka KRaft). 로컬 개발 시 이 파일만 기동하고 앱은 IDE/bootRun으로 실행한다. Kafka는 리스너가 분리되어 있다: 호스트 앱은 `localhost:9092`(EXTERNAL), 컨테이너 앱은 `kafka:29092`(INTERNAL).
- `msa-hotel-service/booking/PROJECT_GUIDE.md` — 살아있는 프로젝트 계획 문서: 기능 체크리스트, DB 스키마, API 엔드포인트 표, 주차별 작업 플랜, "Known Issues" 표. 어떤 기능이 실제 구현됐는지(`[x]`) 계획 중인지(`[ ]`)는 이 문서를 먼저 확인할 것.

모든 애플리케이션 작업은 `msa-hotel-service/booking/` 안에서 이루어집니다.

## 커맨드

`msa-hotel-service/booking/`에서 실행 (Windows 환경이므로 `gradlew.bat` 사용):

```
.\gradlew.bat build                 # 전체 빌드
.\gradlew.bat test                  # 전체 테스트 실행 (JUnit5)
.\gradlew.bat test --tests "com.domain.booking.service.BookingServiceTest"   # 단일 테스트 클래스
.\gradlew.bat test --tests "com.domain.booking.service.BookingServiceTest.methodName"  # 단일 테스트 메서드
.\gradlew.bat bootRun               # 로컬 실행 (아래 인프라 먼저 기동 필요)
```

`bootRun` 전에 로컬 인프라(MySQL/Redis/Kafka) 기동:
```
docker compose -f docker-compose.local.yml up -d
```

ELK/Prometheus/Grafana 포함 전체 스택 (`msa-hotel-service/`에서 실행):
```
docker compose up -d
```

앱은 기본적으로 `8081` 포트를 사용합니다. Swagger UI는 `/swagger-ui.html`. Actuator는 `/actuator` 하위에 `health,info,prometheus,metrics`를 노출합니다.

DB 스키마 변경은 `ddl-auto`(현재 `validate`로 설정됨)가 아니라 Flyway를 통해서만 합니다. `src/main/resources/db/migration/`에 새 버전 파일(`V{n}__description.sql`)을 추가하세요 — 이미 적용된 마이그레이션 파일은 직접 수정하지 말 것.

## 아키텍처

### 패키지 구조
`com.domain.{auth,user,room,booking,coupon,payment,review}` 하위에 도메인 기준 패키지가 있고, 각 도메인은 동일한 내부 구조를 따릅니다: `controller/`, `dto/{request,response}/`, `entity/`, `repository/`, `service/`, 그리고 해당 도메인이 Kafka 이벤트를 발행/구독하는 경우 `event/`. 공통 로직은 `com.global`에 있습니다: `security`(JWT), `exception`(`DomainException` + `DomainExceptionCode` enum + `GlobalExceptionHandler`), `config`(`SecurityConfig`, `CacheConfig`, `SwaggerConfig`), `aop`(`LoggingAspect`), `response`(`ApiResponse`/`PageResult` 응답 래퍼), `entity`(`BaseEntity`).

### 인증/인가
Stateless JWT(jjwt) 방식 — `JwtTokenProvider`가 액세스/리프레시 토큰을 발급하고, `JwtAuthenticationFilter`가 매 요청마다 검증합니다. 리프레시 토큰은 Redis에 저장됩니다(`RefreshTokenRepository`). `SecurityConfig.SECURITY_EXCLUDE_PATHS`에 공개 엔드포인트(인증 회원가입/로그인/재발급/상태, 객실 목록 조회, swagger, actuator)가 정의되어 있고, `/api/admin/**` 하위는 `ROLE_ADMIN` 권한이 필요하며, 그 외 모든 요청은 인증이 필요합니다.

### 동시성 제어
이 프로젝트의 핵심 학습 포인트이므로, 관련 코드를 수정할 때 아래 패턴을 유지하세요:
- 예약 생성 시 객실 재고 차감: 비관적 락(`@Lock(PESSIMISTIC_WRITE)`)으로 오버부킹 방지.
- 쿠폰 발급/수량 차감: 낙관적 락(`@Version`).
- 예약 생성 트랜잭션: `REPEATABLE_READ` 격리 수준.

### Kafka 이벤트 흐름
`booking-events` 토픽: 예약 생성 시 `BookingEventProducer`가 `BookingCreatedEvent`를 발행합니다. `payment-completed-events` 토픽: 결제 완료 이벤트를 구독해 예약 상태를 `PENDING` → `CONFIRMED`로 변경합니다. 컨슈머는 수동 ack(`listener.ack-mode: manual_immediate`)와 JSON 직렬화를 사용하며, 각 도메인의 `spring.json.trusted.packages` 설정은 해당 도메인의 `event` 패키지와 일치해야 합니다.

### 결제
`com.domain.payment.client.PgClient`는 OpenFeign 기반 Mock PG 연동입니다(실제 외부 PG 없음). 결제 성공이 위의 예약 확정 흐름을 트리거합니다.

### 응답/에러 규칙
컨트롤러는 `ApiResponse<T>`(성공/실패 래퍼)와 페이징 목록에는 `PageResult<T>`를 반환합니다. 도메인 레벨 실패는 `DomainExceptionCode` enum 상수(HTTP 상태 + 메시지 포함)를 사용해 `DomainException`을 던져야 합니다. `GlobalExceptionHandler`가 이를(그리고 validation/bind 예외, 처리되지 않은 예외를) `ApiResponse` 에러 형태로 변환합니다. `DomainExceptionCode`로 표현 가능한 경우 서비스에서 raw exception을 던지지 말 것.

### 로깅/모니터링
`LoggingAspect`(`com.domain..controller..*`에 `@Around` 적용)가 모든 컨트롤러 호출의 메서드, 인자, 소요 시간을 로깅하고 예외 발생 시 ERROR 레벨로 기록합니다. 구조화된 로그는 `logstash-logback-encoder`(`logback-spring.xml`)를 통해 루트 `docker-compose.yml`에 정의된 ELK 스택으로 전송되며, 메트릭은 `msa-hotel-service/monitoring/` 설정에 따라 Prometheus/Grafana가 수집합니다.

### 테스트
서비스 레이어 단위 테스트(JUnit5 + Mockito)는 `src/test/java/com/domain/{domain}/service/`에 위치하며 main 패키지 구조를 그대로 따릅니다. 새 테스트를 추가할 때도 이 구조를 따를 것.

## 프로젝트 컨벤션 (저장소 지침)

- 커밋 메시지: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:` 접두사 사용.
- 기능 구현 시 동일 변경에 테스트 코드도 함께 작성.
- 기능 구현 시 코드에 설명 주석을 작성 (프로젝트 필수 규칙).
- 브랜치 전략: 작업 브랜치 `work/{팀번호}-{이름}` → 제출 브랜치 `project/{팀번호}-{이름}`로 PR (루트 `README.md` 참고).
