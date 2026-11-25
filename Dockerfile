# ---- Build: biên dịch bằng Maven + JDK 25 ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
# Tải dependency trước để Docker cache lại khi chỉ sửa code.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar /app/app.jar

# ---- Run: chỉ cần JRE 25 ----
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home /app nhaminh
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER nhaminh

# Trong container phải nghe ở mọi địa chỉ (mặc định của app là 127.0.0.1).
ENV SERVER_ADDRESS=0.0.0.0 \
    TZ=Asia/Ho_Chi_Minh \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Ho_Chi_Minh"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
