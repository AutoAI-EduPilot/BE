CREATE TABLE exam_attempt_starts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_attempt_starts_exam
        FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_exam_attempt_starts_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_exam_attempt_starts_exam_user UNIQUE (exam_id, user_id)
);

ALTER TABLE exam_submissions
    ADD COLUMN started_at DATETIME(6) NULL;

ALTER TABLE exam_submissions
    ADD COLUMN duration_seconds INT NULL;

ALTER TABLE exam_submissions
    ADD CONSTRAINT chk_exam_submissions_duration_seconds
        CHECK (duration_seconds IS NULL OR duration_seconds >= 0);
