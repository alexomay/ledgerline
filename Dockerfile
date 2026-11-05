# 1. Aplicación web (Angular)
FROM node:24-alpine AS web
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build

# 2. API (Spring Boot) con la aplicación web incluida como archivos estáticos
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
COPY --from=web /app/src/main/resources/static/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package && cp target/ledgerline-*.jar app.jar

# 3. Imagen final: solo el JRE y el jar, con usuario sin privilegios
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 ledgerline
USER ledgerline
WORKDIR /app
COPY --from=build /app/app.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
