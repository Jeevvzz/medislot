FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
RUN mvn -B -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
RUN groupadd --system medislot && useradd --system --gid medislot --home-dir /app --shell /usr/sbin/nologin medislot
COPY --from=build --chown=medislot:medislot /src/backend/target/medislot-1.0.0.jar /app/medislot.jar
COPY --chown=medislot:medislot frontend/ /app/frontend/
ENV PORT=3000
EXPOSE 3000
USER medislot
ENTRYPOINT ["java", "-jar", "/app/medislot.jar"]
