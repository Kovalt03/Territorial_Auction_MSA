# 빌드 컨텍스트는 레포 루트(docker-compose.msa.yml의 context: .)다.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /build

COPY libs/common/gradlew libs/common/gradlew
COPY libs/common/gradle libs/common/gradle
COPY libs/common/build.gradle libs/common/settings.gradle libs/common/
COPY libs/common/src libs/common/src
RUN cd libs/common && chmod +x gradlew && ./gradlew publishToMavenLocal -x test --no-daemon

COPY services/item-service/gradlew services/item-service/gradlew
COPY services/item-service/gradle services/item-service/gradle
COPY services/item-service/build.gradle services/item-service/settings.gradle services/item-service/
COPY services/item-service/src services/item-service/src
RUN cd services/item-service && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/services/item-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
