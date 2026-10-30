# Build stage: compiles the jar with JDK 25, so no local Java install is needed.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Download dependencies first so they are cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q package -DskipTests \
	&& cp target/event-tickets-*.jar app.jar

# Runtime stage: JRE only.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
USER app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
