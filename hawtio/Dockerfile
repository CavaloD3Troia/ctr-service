# Hawtio for `docker compose up`: a web console for the JMX of ctr-service (its meters, ctr.kafka.message.bytes among
# them, and the Kafka producer's metrics), read over Jolokia at http://ctr-service:8080/actuator/jolokia (the local
# profile only). Hawtio publishes no standalone image, only its war: this is that war, pinned and checked, on a pinned
# Tomcat. Local use only: authentication is off.
FROM tomcat:11.0.27-jre21-temurin-noble

ARG HAWTIO_VERSION=5.3.0
ARG HAWTIO_SHA256=c03696ed3e4d62bb48e071704e587571c8e5b3c3a41baf5046ecf2b27a855d5b

RUN curl -fsSL -o /tmp/hawtio.war \
        "https://repo1.maven.org/maven2/io/hawt/hawtio-war/${HAWTIO_VERSION}/hawtio-war-${HAWTIO_VERSION}.war" \
    && echo "${HAWTIO_SHA256}  /tmp/hawtio.war" | sha256sum -c - \
    && mv /tmp/hawtio.war "${CATALINA_HOME}/webapps/hawtio.war"

# No login, and Hawtio may only connect to the service (in compose or on the host)
ENV CATALINA_OPTS="-Dhawtio.authenticationEnabled=false -Dhawtio.proxyAllowlist=ctr-service,host.docker.internal,localhost,127.0.0.1"
