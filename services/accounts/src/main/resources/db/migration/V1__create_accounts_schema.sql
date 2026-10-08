CREATE TABLE account_holders (
    id            UUID         NOT NULL,
    cpf           CHAR(11)     NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    birth_date    DATE         NOT NULL,
    email         VARCHAR(320) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_account_holders PRIMARY KEY (id),
    CONSTRAINT uk_account_holders_cpf UNIQUE (cpf),
    CONSTRAINT uk_account_holders_owner_subject UNIQUE (owner_subject)
);

CREATE TABLE accounts (
    id                UUID        NOT NULL,
    account_holder_id UUID        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    opened_at         TIMESTAMPTZ NOT NULL,
    activated_at      TIMESTAMPTZ,
    version           BIGINT      NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders (id),
    CONSTRAINT ck_accounts_status CHECK (status IN ('PENDING', 'ACTIVE', 'CLOSED'))
);

-- At most one non-closed account per holder (and therefore per CPF).
CREATE UNIQUE INDEX uk_accounts_open_per_holder ON accounts (account_holder_id) WHERE status <> 'CLOSED';
