#
# This script is used to run the Key Worker API locally, to interact with
# existing PostgreSQL and localstack containers.
#
# It runs with a combination of properties from the default spring profile (in application.yaml) and supplemented
# with the -local profile (from application-local.yml). The latter overrides some of the defaults.
#
# The environment variables here will also override values supplied in spring profile properties, specifically
# around removing the SSL connection to the database and setting the DB properties, SERVER_PORT and client credentials
# to match those used in the docker-compose files.
#
# Provide the DB connection details to local container-hosted Postgresql DB
# Match with the credentials set in docker-compose.yml
export DB_SERVER=localhost:5432
export DB_NAME=keyworker-api-db
export DB_USER=admin
export DB_PASS=admin_password
export DB_SSL_MODE=prefer

# AWS configuration
export AWS_REGION=eu-west-2

# Client credentials from environment variables
export CLIENT_ID=$HMPPS_KEY_WORKER_CLIENT_ID
export CLIENT_SECRET=$HMPPS_KEY_WORKER_CLIENT_SECRET

# Provide URLs to other dependent services. Dev services used here (can be local if you set up the dependent services locally)
export PRISON_API_URI_ROOT=https://prison-api-dev.prison.service.justice.gov.uk
export MANAGE_USERS_API_URI_ROOT=https://manage-users-api-dev.hmpps.service.justice.gov.uk
export CASE_NOTES_API_URI_ROOT=https://dev.offender-case-notes.service.justice.gov.uk
export PRISONER_SEARCH_API_URI_ROOT=https://prisoner-search-dev.prison.service.justice.gov.uk
export PRISON_REGISTER_API_URI_ROOT=https://prison-register-dev.hmpps.service.justice.gov.uk
export AUTH_URI_ROOT=https://sign-in-dev.hmpps.service.justice.gov.uk/auth
export COMPLEXITY_OF_NEED_URI=https://complexity-of-need-staging.hmpps.service.justice.gov.uk
export NOMIS_USER_ROLES_API_URI_ROOT=https://nomis-user-roles-api-dev.prison.service.justice.gov.uk

# Run the application with local profiles active
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# End
