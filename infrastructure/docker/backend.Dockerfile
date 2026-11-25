# ST Atlas backend — build with JDK 21, run on a JRE 21 as a non-root user.
FROM eclipse-temurin:21-jdk-alpine@sha256:c07bb82e004a166b5d3d63b84ad70e2e822f228505e2e0c0b8dfdc2890d22f13 AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/st-atlas-backend-*.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:21-jre-alpine@sha256:5d7689f47d670aca766b9503a70bccc2d73b4eb9221ae8f714ae958313c4ce70
RUN addgroup -S -g 10001 atlas && adduser -S -u 10001 -G atlas -h /app atlas \
 && mkdir -p /data && chown atlas:atlas /data
WORKDIR /app
COPY --from=build --chown=atlas:atlas /app/dependencies/ ./
COPY --from=build --chown=atlas:atlas /app/spring-boot-loader/ ./
COPY --from=build --chown=atlas:atlas /app/snapshot-dependencies/ ./
COPY --from=build --chown=atlas:atlas /app/application/ ./
USER 10001:10001
ENV ATLAS_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
