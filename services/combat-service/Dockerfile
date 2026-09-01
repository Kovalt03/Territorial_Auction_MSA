# 빌드 컨텍스트는 레포 루트(docker-compose.msa.yml의 context: .)다.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /build

COPY libs/common/gradlew libs/common/gradlew
COPY libs/common/gradle libs/common/gradle
COPY libs/common/build.gradle libs/common/settings.gradle libs/common/
COPY libs/common/src libs/common/src
RUN cd libs/common && chmod +x gradlew && ./gradlew publishToMavenLocal -x test --no-daemon

COPY services/combat-service/gradlew services/combat-service/gradlew
COPY services/combat-service/gradle services/combat-service/gradle
COPY services/combat-service/build.gradle services/combat-service/settings.gradle services/combat-service/
COPY services/combat-service/src services/combat-service/src
RUN cd services/combat-service && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/services/combat-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
