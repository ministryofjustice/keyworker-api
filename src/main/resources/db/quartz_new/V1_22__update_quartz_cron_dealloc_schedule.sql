update qrtz_cron_triggers set cron_expression = '0 0 * ? * *'
where trigger_name = 'deallocationJobCron';