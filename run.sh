#!/bin/sh
exec java ${JAVA_OPTS} \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Djava.security.egd=file:/dev/./urandom \
  -javaagent:/app/agent.jar \
  -jar /app/app.jar
