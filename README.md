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
