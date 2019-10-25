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

#### Health

- `/ping`: will respond `pong` to all requests.  This should be used by dependent systems to check connectivity to keyworker,
rather than calling the `/health` endpoint.
- `/health`: provides information about the application health and its dependencies.  This should only be used
by keyworker health monitoring (e.g. pager duty) and not other systems who wish to find out the state of keyworker.
- `/info`: provides information about the version of deployed application.

## Running localstack and database
```bash
TMPDIR=/private$TMPDIR docker-compose up localstack keyworker-api-db
```

## Creating the Topic and Queue
Simpliest way is running the following script
```bash
./setup-queue.bash
```

Or you can run the scripts individually as shown below.

## Creating a topic and queue on localstack

```bash
aws --endpoint-url=http://localhost:4575 sns create-topic --name offender_events
```

Results in:
```json
{
    "TopicArn": "arn:aws:sns:eu-west-2:000000000000:offender_events"
}

```

## Creating a queue
```bash
aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name keyworker_api_queue
```

Results in:
```json
{
   "QueueUrl": "http://localhost:4576/queue/keyworker_api_queue"
}
```

## Creating a subscription
```bash
aws --endpoint-url=http://localhost:4575 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4576/queue/keyworker_api_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[\"EXTERNAL_MOVEMENT_RECORD-INSERTED\", \"BOOKING_NUMBER-CHANGED\"]}"}'
```

Results in:
```json
{
    "SubscriptionArn": "arn:aws:sns:eu-west-2:000000000000:offender_events:074545bd-393c-4a43-ad62-95b1809534f0"
}
```

## Read off the queue
```bash
aws --endpoint-url=http://localhost:4576 sqs receive-message --queue-url http://localhost:4576/queue/keyworker_api_queue
```