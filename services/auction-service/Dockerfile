# 빌드 컨텍스트 = 레포 루트 (docker-compose.msa.yml에서 context: . 로 지정).
# common 라이브러리를 이미지 안에서 mavenLocal에 발행해 PAT(GitHub Packages) 없이 self-contained 빌드.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /build

# 1) 공유 플랫폼 라이브러리 common → mavenLocal 발행
COPY libs/common/gradlew libs/common/gradlew
COPY libs/common/gradle libs/common/gradle
COPY libs/common/build.gradle libs/common/settings.gradle libs/common/
COPY libs/common/src libs/common/src
RUN cd libs/common && chmod +x gradlew && ./gradlew publishToMavenLocal -x test --no-daemon

# 2) auction-service 빌드 (common은 mavenLocal()에서 해결 → GitHub Packages 미조회)
COPY services/auction-service/gradlew services/auction-service/gradlew
COPY services/auction-service/gradle services/auction-service/gradle
COPY services/auction-service/build.gradle services/auction-service/settings.gradle services/auction-service/
COPY services/auction-service/src services/auction-service/src
RUN cd services/auction-service && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# 실행 스테이지
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/services/auction-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
