FROM eclipse-temurin:17-jre
WORKDIR /app
ARG APP_VERSION=7.9.0
COPY target/orders-api-*.jar app.jar
ENV APP_ENV=PRODUCTION
ENV APP_VERSION=${APP_VERSION}
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
