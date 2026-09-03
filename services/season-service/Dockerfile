# 빌드 컨텍스트는 레포 루트(docker-compose.msa.yml의 context: .)다.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /build

COPY libs/common/gradlew libs/common/gradlew
COPY libs/common/gradle libs/common/gradle
COPY libs/common/build.gradle libs/common/settings.gradle libs/common/
COPY libs/common/src libs/common/src
RUN cd libs/common && chmod +x gradlew && ./gradlew publishToMavenLocal -x test --no-daemon

COPY services/season-service/gradlew services/season-service/gradlew
COPY services/season-service/gradle services/season-service/gradle
COPY services/season-service/build.gradle services/season-service/settings.gradle services/season-service/
COPY services/season-service/src services/season-service/src
RUN cd services/season-service && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/services/season-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
