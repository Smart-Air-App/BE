# 1단계: 빌드 단계
FROM gradle:8.4-jdk17 AS build
WORKDIR /home/app
COPY . .
RUN gradle clean build -x test

# 2단계: 실행 단계
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /home/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
