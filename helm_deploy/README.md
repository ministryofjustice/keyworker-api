
### Example deploy command
```
helm --namespace keyworker-api-dev  --tiller-namespace keyworker-api-dev upgrade keyworker-api ./keyworker-api/ --install --values=values-dev.yaml --values=example-secrets.yaml
```

### Rolling back a release
Find the revision number for the deployment you want to roll back:
```
helm --tiller-namespace keyworker-api-dev history keyworker-api -o yaml
```
(note, each revision has a description which has the app version and circleci build URL)

Rollback
```
helm --tiller-namespace keyworker-api-dev rollback keyworker-api [INSERT REVISION NUMBER HERE] --wait
```

### Helm init

```
helm init --tiller-namespace keyworker-api-dev --service-account tiller --history-max 200
```

### Setup Lets Encrypt cert

Ensure the certificate definition exists in the cloud-platform-environments repo under the relevant namespaces folder

e.g.
```
cloud-platform-environments/namespaces/live-1.cloud-platform.service.justice.gov.uk/[INSERT NAMESPACE NAME]/05-certificate.yaml
```
