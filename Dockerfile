# Use official OpenJDK 21 base image
FROM eclipse-temurin:21-jdk-jammy as builder

# Set working directory
WORKDIR /app

# Copy Maven project files
COPY pom.xml mvnw ./
COPY .mvn .mvn

# ✅ Give executable permission to Maven Wrapper (important on Linux)
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Copy ffmpeg files to the server folder
COPY ffmpeg/ /app/ffmpeg/

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests

# -----------------------------------------------
# Final lightweight runtime image
# -----------------------------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the Spring Boot port
EXPOSE 8081

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
