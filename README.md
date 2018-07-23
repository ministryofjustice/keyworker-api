# keyworker-api

OMIC Keyworker API


#### Env Variables:

```properties
      SERVER_PORT=8081
      SPRING_PROFILES_ACTIVE=dev
      JWT_PUBLIC_KEY=secret
      ELITE2_URI_ROOT=http://localhost:8080
      QUARTZ_ENABLED=false
      DEALLOCATION_JOB_CRON=0 15 09 ? * *
      API_KEYWORKER_INITIAL_DEALLOCATE_THRESHOLD=2018-04-01T12:00
      ELITE2API_CLIENT_CLIENTSECRET=**
```

### Setting secrets

`JWT_PUBLIC_KEY` is generated from:-

```bash
keytool -list -rfc --keystore elite2api.jks | openssl x509 -inform pem -pubkey -noout | base64
```

`elite2api.jks` is the pub/private key pair that elite2-api holds.

### Other Variables
####QUARTZ_ENABLED
Switch running batch processes on or off
####DEALLOCATION_JOB_CRON
Defines when the deallocation job runs. Can be as often as desired provided there isnt a significant load on NOMIS.
####API_KEYWORKER_INITIAL_DEALLOCATE_THRESHOLD
The earliest date the deallocate batch job needs to go back to, e.g. migration time of the earliest prison. This is used when there is no previous batch run timestamp available.
####ELITE2API_CLIENT_CLIENTSECRET
The secret for the "omicadmin" oauth2 client id.

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

### Connecting to Dev / Stage keyworker RDS DB instances

The RDS DB required SSL mode to connect therefore add `sslmode=verify-full` to the end of the JDBC URL

In addition you will need to add the root Amazon CA certificate

```bash
mkdir ~/.postgresql
curl https://s3.amazonaws.com/rds-downloads/rds-ca-2015-root.pem > ~/.postgresql/root.crt
```

 
