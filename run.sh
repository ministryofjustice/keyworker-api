#!/bin/sh
echo "********************************************************"
echo "Starting Key-Worker Service                             "
echo "********************************************************"
NAME=${NAME:-keyworker-service}
JAR=$(find . -name ${NAME}*.jar|head -1)
java ${JAVA_OPTS} -Dcom.sun.management.jmxremote.local.only=false -Djava.security.egd=file:/dev/./urandom -jar "${JAR}"

