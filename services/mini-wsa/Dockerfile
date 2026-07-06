# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY services/mini-wsa/pom.xml services/mini-wsa/pom.xml
COPY services/mini-wsa/src services/mini-wsa/src
RUN mvn -q -pl services/mini-wsa -am clean package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/services/mini-wsa/target/mini-wsa-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
