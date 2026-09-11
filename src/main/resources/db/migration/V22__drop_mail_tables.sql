-- Drop mail tables — notification logic moved to notification-service
-- Data migration not needed (dev environment, clean break per OQ-002)

DROP TABLE IF EXISTS mail_queue;
DROP TABLE IF EXISTS mail_templates;
