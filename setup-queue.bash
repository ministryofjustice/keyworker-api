#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4575 sns create-topic --name offender_events
aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name keyworker_api_queue
aws --endpoint-url=http://localhost:4575 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4576/queue/keyworker_api_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[\"EXTERNAL_MOVEMENT_RECORD-INSERTED\", \"BOOKING_NUMBER-CHANGED\"]}"}'
