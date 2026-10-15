# Build stage: Maven Wrapper + Java 25
FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw .
COPY services/pom.xml services/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/api-gateway/src services/api-gateway/src
RUN ./mvnw -f services/pom.xml -pl :api-gateway -am package -DskipTests

# Runtime stage: JRE 25, usuario no root
FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61
RUN addgroup -S hotel && adduser -S hotel -G hotel
WORKDIR /app
COPY --from=build /build/services/api-gateway/target/api-gateway-*.jar app.jar
USER hotel
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
