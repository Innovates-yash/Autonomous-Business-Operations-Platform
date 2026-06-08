-- =====================================================================
-- Auth Service initial schema (Requirements 1.x, 2.x)
-- Entities: Role, Permission, User, RefreshToken, LoginAttempt, OAuthIdentity
-- =====================================================================

-- ---------------------------------------------------------------------
-- Roles: exactly one per user (Req 2.1); Guest is the default (Req 2.2).
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Permissions: discrete capabilities a role may hold (Req 2.3).
-- ---------------------------------------------------------------------
CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uq_permissions_name UNIQUE (name)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Role <-> Permission join table.
-- ---------------------------------------------------------------------
CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Users: authenticated principals (Req 1.1). One role each (Req 2.1).
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100),
    role_id         BIGINT       NOT NULL,
    enabled         BIT(1)       NOT NULL DEFAULT b'1',
    account_locked  BIT(1)       NOT NULL DEFAULT b'0',
    lock_expires_at DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Refresh tokens: opaque, hashed, single-use with rotation (Req 1.5-1.7).
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                BIGINT       NOT NULL,
    token_hash             VARCHAR(255) NOT NULL,
    issued_at              DATETIME(6)  NOT NULL,
    expires_at             DATETIME(6)  NOT NULL,
    revoked                BIT(1)       NOT NULL DEFAULT b'0',
    used                   BIT(1)       NOT NULL DEFAULT b'0',
    replaced_by_token_hash VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------
-- Login attempts: drives rolling-window lockout counting (Req 1.11).
-- ---------------------------------------------------------------------
CREATE TABLE login_attempts (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    email        VARCHAR(254) NOT NULL,
    successful   BIT(1)       NOT NULL,
    ip_address   VARCHAR(45),
    attempted_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_login_attempts_email_time ON login_attempts (email, attempted_at);

-- ---------------------------------------------------------------------
-- OAuth identities: external identity -> Platform user (Req 1.8).
-- ---------------------------------------------------------------------
CREATE TABLE oauth_identities (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_provider_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_oauth_identities_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Seed the six platform roles (Req 2.1, 2.2).
-- ---------------------------------------------------------------------
INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('ARCHITECT'),
    ('PRODUCT_MANAGER_ROLE'),
    ('DEVELOPER'),
    ('CLIENT'),
    ('GUEST');
