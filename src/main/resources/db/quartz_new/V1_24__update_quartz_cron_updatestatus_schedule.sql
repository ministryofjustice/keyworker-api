update qrtz_cron_triggers set cron_expression = '0 30 0 ? * *'
where trigger_name = 'updateStatusJobCron';