FROM openjdk:8-jdk-alpine
MAINTAINER HMPPS Digital Studio <info@digital.justice.gov.uk>

RUN apk update \
  && apk upgrade \
  && apk add netcat-openbsd \
  && apk add --update curl \
  && rm -rf /var/cache/apk/*

WORKDIR /app

COPY build/libs/keyworker-service*.jar /app/app.jar
COPY run.sh /app

ENTRYPOINT ["/bin/sh", "/app/run.sh"]
