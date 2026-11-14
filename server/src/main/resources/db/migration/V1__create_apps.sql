CREATE TABLE apps
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(50)   NOT NULL,
    repository_url VARCHAR(500)  NOT NULL,
    internal_port  INT           NOT NULL,
    assigned_port  INT           NULL,
    container_id   VARCHAR(64)   NULL,
    status         VARCHAR(20)   NOT NULL,
    failure_reason VARCHAR(1000) NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NULL,
    updated_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_apps_name UNIQUE (name),
    CONSTRAINT uk_apps_assigned_port UNIQUE (assigned_port)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
