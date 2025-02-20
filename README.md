# keyworker-api

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/ministryofjustice/keyworker-api/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/ministryofjustice/keyworker-api/tree/main)
[![codecov](https://codecov.io/github/ministryofjustice/keyworker-api/graph/badge.svg?token=Dl9EO7vO5Y)](https://codecov.io/github/ministryofjustice/keyworker-api)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/keyworker-api/status)](https://quay.io/repository/hmpps/keyworker-api)
[![API docs](https://img.shields.io/badge/API_docs-view-85EA2D.svg?logo=swagger)](https://keyworker-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html)

Datebase Schema diagram: https://ministryofjustice.github.io/keyworker-api/schema-spy-report/

A Spring Boot JSON API to manage the keyworkers of prisoners for the Digital Prison Services.  Backend services for https://github.com/ministryofjustice/manage-key-workers

### To build:

```bash
./gradlew build
```

### Running locally

When running locally there will be no Auth server to supply the JWT public key, and you won't need localstack.

Use spring profiles `local` to pick up the public key defined in src/main/resources.

Use spring profile `noqueue` to ignore the localstack config.

In order to get the `/info` endpoint to work you will need to add in
```
-add-opens java.base/java.lang=ALL-UNNAMED
```
to your run configuration.  This is because the current version of ehcache needs to calculate the size of the objects in the cache, which the latest version of openjdk disallows.

### Health

- `/health/ping`: will respond `{"status":"UP"}` to all requests.  This should be used by dependent systems to check connectivity to keyworker,
rather than calling the `/health` endpoint.
- `/health`: provides information about the application health and its dependencies.  This should only be used
by keyworker health monitoring (e.g. pager duty) and not other systems who wish to find out the state of keyworker.
- `/info`: provides information about the version of deployed application.

### Pre Release Testing

Keyworker api is best tested by the DPS front end.  To manually smoke test / regression test keyworker api prior to release:

1. Navigate to [DPS](https://digital-preprod.prison.service.justice.gov.uk/) and search for a prisoner
1. In the quick look section ensure that they have a key worker set - most prisoners should have one
1. Check [My key worker allocations](https://digital-preprod.prison.service.justice.gov.uk/key-worker-allocations)
1. Navigate to [Manage key workers](https://preprod.manage-key-workers.service.justice.gov.uk/manage-key-workers)
1. Search for [Your key workers](https://preprod.manage-key-workers.service.justice.gov.uk/manage-key-workers/key-worker-search)
   1. Check search results show key workers
   1. Navigate to individual key worker and check stats are displayed and current allocations
1. [Manually allocate key workers](https://preprod.manage-key-workers.service.justice.gov.uk/manage-key-workers/offender-search) and view key worker history for an offender
1. Select [Prison statistics](https://preprod.manage-key-workers.service.justice.gov.uk/manage-key-workers/key-worker-statistics) and ensure statistics are displayed

### Deployment Notes

#### Prerequisites

- Ensure you have helm v3 client installed.

```sh
$ helm version
version.BuildInfo{Version:"v3.0.1", GitCommit:"7c22ef9ce89e0ebeb7125ba2ebf7d421f3e82ffa", GitTreeState:"clean", GoVersion:"go1.13.4"}
```

- Ensure a TLS cert for your intended hostname is configured and ready, see section below.

### Useful helm (v3) commands:

__Test chart template rendering:__

This will out the fully rendered kubernetes resources in raw yaml.

```sh
helm template [path to chart] --values=values-dev.yaml --values=secrets-example.yaml
```

__List releases:__

```sh
helm --namespace [namespace] list
```

__List current and previously installed application versions:__

```sh
helm --namespace [namespace] history [release name]
```

__Rollback to previous version:__

```sh
helm --namespace [namespace] rollback [release name] [revision number] --wait
```

Note: replace _revision number_ with one from listed in the `history` command)

__Example deploy command:__

The following example is `--dry-run` mode - which will allow for testing. CircleCI normally runs this command with actual secret values (from AWS secret manager), and also updated the chart's application version to match the release version:

```sh
helm upgrade [release name] [path to chart]. \
  --install --wait --force --reset-values --timeout 5m --history-max 10 \
  --dry-run \
  --namespace [namespace] \
  --values values-dev.yaml \
  --values example-secrets.yaml
```

### Ingress TLS certificate

Ensure a certificate definition exists in the cloud-platform-environments repo under the relevant namespaces folder:

e.g.

```sh
cloud-platform-environments/namespaces/live-1.cloud-platform.service.justice.gov.uk/[INSERT NAMESPACE NAME]/05-certificate.yaml
```

Ensure the certificate is created and ready for use.

The name of the kubernetes secret where the certificate is stored is used as a value to the helm chart - this is used to configured the ingress.


### Running against localstack

Localstack has been introduced for some integration tests and it is also possible to run the application against localstack.

* In the root of the localstack project, run command
```
docker-compose -f docker-compose-localstack.yaml down && docker-compose -f docker-compose-localstack.yaml up
```
to clear down and then bring up localstack
* Start the Spring Boot app with profile='localstack'
* You can now use the aws CLI to send messages to the queue
* The queue's health status should appear at the local healthcheck: http://localhost:8081/health
* Note that you will also need local copies of Oauth server, Case notes API and Delius API running to do anything useful

### Running the tests

With localstack now up and running (see previous section), run
```bash
./gradlew test
```
