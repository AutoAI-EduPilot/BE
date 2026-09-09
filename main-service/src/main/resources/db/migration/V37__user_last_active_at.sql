ALTER TABLE users
    ADD COLUMN last_active_at DATETIME(6) NULL;

UPDATE users u
SET last_active_at = NULLIF(
    GREATEST(
        COALESCE(
            (
                SELECT MAX(rt.created_at)
                FROM refresh_tokens rt
                WHERE rt.user_id = u.id
            ),
            CAST('1000-01-01 00:00:00.000000' AS DATETIME(6))
        ),
        COALESCE(
            (
                SELECT MAX(cm.created_at)
                FROM chat_messages cm
                JOIN learning_sessions ls
                    ON ls.id = cm.session_id
                WHERE ls.user_id = u.id
            ),
            CAST('1000-01-01 00:00:00.000000' AS DATETIME(6))
        ),
        COALESCE(
            (
                SELECT MAX(ls.updated_at)
                FROM learning_sessions ls
                WHERE ls.user_id = u.id
            ),
            CAST('1000-01-01 00:00:00.000000' AS DATETIME(6))
        ),
        COALESCE(
            (
                SELECT MAX(es.submitted_at)
                FROM exam_submissions es
                WHERE es.user_id = u.id
            ),
            CAST('1000-01-01 00:00:00.000000' AS DATETIME(6))
        )
    ),
    CAST('1000-01-01 00:00:00.000000' AS DATETIME(6))
);

CREATE INDEX idx_users_last_active_at ON users (last_active_at);
