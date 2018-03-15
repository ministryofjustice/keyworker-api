# keyworker-service
OMIC Keyworker Service


Env Variables:

      - SERVER_PORT=8081
      - SPRING_PROFILES_ACTIVE=dev
      - API_GATEWAY_TOKEN=***
      - API_GATEWAY_PRIVATE_KEY=***
      - USE_API_GATEWAY_AUTH=false
      - JWT_SIGNING_KEY=secret
      - ELITE2_API_URI_ROOT=http://localhost:8080/api
To build:

```bash
./gradlew build
```

To Run:
```bash
docker-compose up
```

####Running against local **postgres** docker:
Run the postgres docker image:
```bash
docker pull postgres
docker run --name=keyworker-postgres -p 5432:5432 postgres
```
Run spring boot with the the postgres spring profile


