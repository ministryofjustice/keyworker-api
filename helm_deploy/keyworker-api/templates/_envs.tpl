    {{/* vim: set filetype=mustache: */}}
{{/*
Environment variables for web and worker containers
*/}}
{{- define "deployment.envs" }}
env:
  - name: SERVER_PORT
    value: "{{ .Values.image.port }}"

  - name: SPRING_PROFILES_ACTIVE
    value: "batch,sqs"

  - name: JAVA_OPTS
    value: "{{ .Values.env.JAVA_OPTS }}"

  - name: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI
    value: "{{ .Values.env.SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI }}"

  - name: ELITE2_URI_ROOT
    value: "{{ .Values.env.ELITE2_URI_ROOT }}"

  - name: AUTH_URI_ROOT 
    value: "{{ .Values.env.AUTH_URI_ROOT }}"

  - name: SERVER_CONNECTION_TIMEOUT 
    value: "180000"

  - name: WOMENS_ESTATE
    value: "{{ .Values.env.WOMENS_ESTATE }}"

  - name: WOMENS_ESTATE_COMPLEX_OFFENDERS
    value: "{{ .Values.env.WOMENS_ESTATE_COMPLEX_OFFENDERS }}"

  - name: APPINSIGHTS_INSTRUMENTATIONKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: APPLICATIONINSIGHTS_CONNECTION_STRING
    value: "InstrumentationKey=$(APPINSIGHTS_INSTRUMENTATIONKEY)"

  - name: ELITE2API_CLIENT_CLIENTID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: ELITE2API_CLIENT_CLIENTID

  - name: ELITE2API_CLIENT_CLIENTSECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: ELITE2API_CLIENT_CLIENTSECRET

  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: database_username

  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: database_password

  - name: DB_NAME
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: database_name

  - name: DB_ENDPOINT
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: rds_instance_endpoint

  - name: APP_DB_URL
    value: "jdbc:postgresql://$(DB_ENDPOINT)/$(DB_NAME)?sslmode=verify-full"

  - name: OFFENDER_EVENTS_SQS_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: kw-sqs-instance-output
        key: access_key_id

  - name: OFFENDER_EVENTS_SQS_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: kw-sqs-instance-output
        key: secret_access_key

  - name: OFFENDER_EVENTS_SQS_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: kw-sqs-instance-output
        key: sqs_kw_name

  - name: OFFENDER_EVENTS_SQS_AWS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: kw-sqs-dl-instance-output
        key: access_key_id

  - name: OFFENDER_EVENTS_SQS_AWS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: kw-sqs-dl-instance-output
        key: secret_access_key

  - name: OFFENDER_EVENTS_SQS_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: kw-sqs-dl-instance-output
        key: sqs_kw_name
{{- end -}}
