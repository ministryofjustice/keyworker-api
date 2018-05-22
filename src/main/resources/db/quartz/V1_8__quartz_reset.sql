update qrtz_triggers set prev_fire_time = -1 where trigger_name = 'deallocationJobCron';
