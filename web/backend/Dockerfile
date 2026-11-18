# Stage 1: Build the JAR with OpenJDK 21
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .

# Ensure gradlew has execute permission
RUN chmod +x ./gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build production jar (skip tests)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Expose default port (Render will override via $PORT)
ENV PORT=8080
EXPOSE 8080

# Copy executable jar from build stage
COPY --from=build /app/build/libs/backend-0.0.1-SNAPSHOT.jar app.jar

# Run with container-aware memory limits and dynamic port
CMD ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-8080} -jar app.jar"]
