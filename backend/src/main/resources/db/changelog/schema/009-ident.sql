--liquibase formatted sql

--changeset aciee:009-ident
CREATE TABLE ident.token_issuance (
    user_id      integer NOT NULL REFERENCES core.user_account,
    offering_id  integer NOT NULL REFERENCES core.course_offering,
    issued_week  ref.week_start NOT NULL,
    blinded_msg  bytea NOT NULL,
    blind_sig    bytea NOT NULL,
    PRIMARY KEY (user_id, offering_id)
);

CREATE TABLE ident.token_vault (
    user_id    integer PRIMARY KEY REFERENCES core.user_account,
    ciphertext bytea NOT NULL,
    nonce      bytea NOT NULL CHECK (octet_length(nonce) = 12),
    updated_at timestamptz NOT NULL DEFAULT now()
);
--rollback DROP TABLE ident.token_vault, ident.token_issuance;
