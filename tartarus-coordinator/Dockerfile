FROM amazoncorretto:23

ARG VERSION=latest

EXPOSE 5001
EXPOSE 4447

WORKDIR /app

COPY build/libs/tartarus-coordinator-${VERSION}.jar /app

CMD ["java", "-jar", "/app/tartarus-coordinator-${VERSION}.jar"]