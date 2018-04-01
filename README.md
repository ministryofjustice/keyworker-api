# keyworker-service

OMIC Keyworker Service


#### Env Variables:

```properties
      SERVER_PORT=8081
      SPRING_PROFILES_ACTIVE=dev
      API_GATEWAY_TOKEN=***
      API_GATEWAY_PRIVATE_KEY=***
      USE_API_GATEWAY_AUTH=false
      JWT_PUBLIC_KEY=secret
      ELITE2_API_URI_ROOT=http://localhost:8080/api
```

### To build:

```bash
./gradlew build
```

### To Run:
```bash
docker-compose up
```

#### Running against local postgres docker:
Run the postgres docker image:
```bash
docker run --name=keyworker-postgres -e POSTGRES_PASSWORD=password -p5432:5432 -d postgres
```
Run spring boot with the the postgres spring profile


