####
# This Dockerfile is used for building the Village Calendar service container image
# It supports both JVM and native builds
#
# Build JVM image:
#   mvn clean package -Dquarkus.container-image.build=true
#
# Build native image:
#   mvn clean package -Pnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true
#
# The Jib extension handles the actual image building with optimized layers
####

###
# Stage 1: Build the application
###
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Install Node.js and npm for Quinoa (frontend build)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

# Copy the Maven wrapper and pom.xml first for better layer caching
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./

# Download dependencies (cached layer if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application with Quinoa (includes frontend build)
RUN mvn clean package -DskipTests -Dquarkus.quinoa.build=true

###
# Stage 2: Create the runtime image
###
FROM eclipse-temurin:21-jre-alpine

WORKDIR /deployments

# Install fonts required for PDF generation with emojis
# - font-noto-emoji: Noto Emoji (monochrome) for emoji support in PDFs
# - ttf-dejavu: DejaVu fonts with wide Unicode coverage
# - fontconfig: Font configuration and cache
RUN apk add --no-cache \
    fontconfig \
    ttf-dejavu \
    font-noto-emoji \
    && fc-cache -f

# Create a non-root user to run the application
RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

# Copy the application from build stage
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/quarkus/ /deployments/quarkus/

# Switch to non-root user
USER quarkus

# Expose the application port
EXPOSE 8030

# Set environment variables for optimal JVM performance in containers
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8030/q/health/ready || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
