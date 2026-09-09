ALTER TABLE exams
    ADD COLUMN due_at DATETIME(6) NULL;

ALTER TABLE notifications
    ADD COLUMN dedup_key VARCHAR(120) NULL;

ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type CHECK (
        type IN (
            'MATERIAL_UPLOADED',
            'NOTICE_PUBLISHED',
            'JOIN_REQUEST_RECEIVED',
            'JOIN_REQUEST_PROCESSED',
            'EXAM_PUBLISHED',
            'EXAM_DEADLINE_APPROACHING',
            'EXAM_GRADED'
        )
    );

CREATE UNIQUE INDEX uk_notifications_dedup
    ON notifications (dedup_key);
