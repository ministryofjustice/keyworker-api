UPDATE prison_configuration_audit SET allocation_order_modified = true WHERE rev_type = 0;
UPDATE prison_configuration_audit SET allocation_order_modified = false WHERE rev_type = 1;