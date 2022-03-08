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

  - name: PRISON_URI_ROOT
    value: "{{ .Values.env.PRISON_URI_ROOT }}"

  - name: AUTH_URI_ROOT 
    value: "{{ .Values.env.AUTH_URI_ROOT }}"

  - name: SERVER_CONNECTION_TIMEOUT 
    value: "180000"

  - name: PRISONS_WITH_OFFENDERS_THAT_HAVE_COMPLEX_NEEDS
    value: "{{ .Values.env.PRISONS_WITH_OFFENDERS_THAT_HAVE_COMPLEX_NEEDS }}"

  - name: COMPLEXITY_OF_NEED_URI
    value: "{{ .Values.env.COMPLEXITY_OF_NEED_URI }}"

  - name: APPINSIGHTS_INSTRUMENTATIONKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: APPLICATIONINSIGHTS_CONNECTION_STRING
    value: "InstrumentationKey=$(APPINSIGHTS_INSTRUMENTATIONKEY)"

  - name: PRISONAPI_CLIENT_CLIENTID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: PRISONAPI_CLIENT_CLIENTID

  - name: PRISONAPI_CLIENT_CLIENTSECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: PRISONAPI_CLIENT_CLIENTSECRET

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

  - name: HMPPS_SQS_QUEUES_OFFENDEREVENTS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: kw-sqs-instance-output
        key: access_key_id

  - name: HMPPS_SQS_QUEUES_OFFENDEREVENTS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: kw-sqs-instance-output
        key: secret_access_key

  - name: HMPPS_SQS_QUEUES_OFFENDEREVENTS_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: kw-sqs-instance-output
        key: sqs_kw_name

  - name: HMPPS_SQS_QUEUES_OFFENDEREVENTS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: kw-sqs-dl-instance-output
        key: access_key_id

  - name: HMPPS_SQS_QUEUES_OFFENDEREVENTS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: kw-sqs-dl-instance-output
        key: secret_access_key

  - name: HMPPS_SQS_QUEUES_OFFENDEREVENTS_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: kw-sqs-dl-instance-output
        key: sqs_kw_name

  - name: HMPPS_SQS_QUEUES_COMPLEXITYOFNEED_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: kw-sqs-complexity-of-need-instance-output
        key: access_key_id

  - name: HMPPS_SQS_QUEUES_COMPLEXITYOFNEED_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: kw-sqs-complexity-of-need-instance-output
        key: secret_access_key

  - name: HMPPS_SQS_QUEUES_COMPLEXITYOFNEED_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: kw-sqs-complexity-of-need-instance-output
        key: sqs_kw_name

  - name: HMPPS_SQS_QUEUES_COMPLEXITYOFNEED_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: kw-sqs-complexity-of-need-dl-instance-output
        key: access_key_id

  - name: HMPPS_SQS_QUEUES_COMPLEXITYOFNEED_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: kw-sqs-complexity-of-need-dl-instance-output
        key: secret_access_key

  - name: HMPPS_SQS_QUEUES_COMPLEXITYOFNEED_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: kw-sqs-complexity-of-need-dl-instance-output
        key: sqs_kw_name	


{{- end -}}
