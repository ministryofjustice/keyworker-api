FROM openjdk:8-jdk-alpine
MAINTAINER HMPPS Digital Studio <info@digital.justice.gov.uk>

RUN apk update \
  && apk upgrade \
  && apk add netcat-openbsd \
  && apk add --update curl \
  && rm -rf /var/cache/apk/*

# Install AWS RDS Root cert into Java truststore
RUN mkdir /root/.postgresql \
  && curl https://s3.amazonaws.com/rds-downloads/rds-ca-2015-root.pem \
    > /root/.postgresql/root.crt

WORKDIR /app

COPY build/libs/keyworker-service*.jar /app/app.jar
COPY run.sh /app

ENTRYPOINT ["/bin/sh", "/app/run.sh"]
