# The jar is built on the host first (`make package`), so building the image needs no Maven
# and no GitHub Packages credentials.
FROM eclipse-temurin:25-jre

RUN groupadd --system ctr && useradd --system --gid ctr ctr

WORKDIR /app
COPY application/target/ctr-service-exec.jar app.jar

USER ctr
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
