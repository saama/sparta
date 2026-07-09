# booking-service 실행 이미지
#
# 주의: 이 Dockerfile은 호스트에서 미리 빌드한 jar를 복사한다.
#   (컨테이너 내부에서 Gradle 빌드 시 프록시/TLS 환경에서 의존성 다운로드가 실패할 수 있음)
#
# 빌드 순서:
#   1. ./gradlew bootJar          (booking/ 에서 - jar 생성)
#   2. docker compose build       (msa-hotel-service/ 에서 - 이미지 생성)
FROM eclipse-temurin:21-jre
WORKDIR /app

# bootJar 산출물만 복사 (-plain.jar 제외)
COPY build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
