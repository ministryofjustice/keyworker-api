delete from qrtz_cron_triggers where sched_name = 'keyworker-quartz';

delete from qrtz_scheduler_state where sched_name = 'keyworker-quartz';

delete from qrtz_fired_triggers where sched_name = 'keyworker-quartz';

delete from qrtz_locks where sched_name = 'keyworker-quartz';

delete from qrtz_paused_trigger_grps where sched_name = 'keyworker-quartz';

delete from qrtz_simple_triggers where sched_name = 'keyworker-quartz';

delete from qrtz_triggers where sched_name = 'keyworker-quartz';

delete from qrtz_job_details where sched_name = 'keyworker-quartz';