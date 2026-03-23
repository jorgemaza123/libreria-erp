FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

VOLUME /tmp

ARG JAR_FILE=*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Duser.timezone=America/Lima","-jar","app.jar"]
