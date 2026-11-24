-- 배포 1회 = 1행. 앱 생성·재배포·Webhook 등 트리거와 단계별 소요 시간을 남긴다.
CREATE TABLE deployments
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    app_id         BIGINT        NOT NULL,
    trigger_type   VARCHAR(20)   NOT NULL,
    commit_sha     VARCHAR(40)   NULL,
    status         VARCHAR(20)   NOT NULL,
    failure_reason VARCHAR(1000) NULL,
    clone_ms       BIGINT        NULL,
    build_ms       BIGINT        NULL,
    started_at     DATETIME(6)   NOT NULL,
    finished_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_deployments_app_started (app_id, started_at),
    -- 앱을 지우면 이력도 함께 지운다 (JPA 연관관계 대신 DB 가 처리)
    CONSTRAINT fk_deployments_app FOREIGN KEY (app_id) REFERENCES apps (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
