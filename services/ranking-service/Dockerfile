# 빌드 컨텍스트는 레포 루트(docker-compose.msa.yml의 context: .)다.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /build

COPY libs/common/gradlew libs/common/gradlew
COPY libs/common/gradle libs/common/gradle
COPY libs/common/build.gradle libs/common/settings.gradle libs/common/
COPY libs/common/src libs/common/src
RUN cd libs/common && chmod +x gradlew && ./gradlew publishToMavenLocal -x test --no-daemon

COPY services/ranking-service/gradlew services/ranking-service/gradlew
COPY services/ranking-service/gradle services/ranking-service/gradle
COPY services/ranking-service/build.gradle services/ranking-service/settings.gradle services/ranking-service/
COPY services/ranking-service/src services/ranking-service/src
RUN cd services/ranking-service && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/services/ranking-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
