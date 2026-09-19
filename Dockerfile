FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/mini-redis-java-1.0.0.jar /app/mini-redis.jar
EXPOSE 6380
ENV REDIS_PORT=6380
ENV REDIS_AOF_ENABLED=true
CMD ["java", "-cp", "/app/mini-redis.jar", "com.jaimin.redis.server.RedisServer"]