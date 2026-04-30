# Stage 1: Build
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

COPY docker/maven-settings.xml /root/.m2/settings.xml

COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src src
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM amazoncorretto:21-al2023-headless
WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
