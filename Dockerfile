FROM openjdk:11-slim
MAINTAINER HMPPS Digital Studio <info@digital.justice.gov.uk>

RUN apt-get update && apt-get install -y curl

# Install AWS RDS Root cert into Java truststore
RUN mkdir /root/.postgresql \
  && curl https://s3.amazonaws.com/rds-downloads/rds-ca-2015-root.pem \
    > /root/.postgresql/root.crt

WORKDIR /app

COPY build/libs/keyworker-api*.jar /app/app.jar
COPY run.sh /app

ENTRYPOINT ["/bin/sh", "/app/run.sh"]
