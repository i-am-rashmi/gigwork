-- Contact methods (FR-2): which channels a user is willing to share. They use only the verified mobile number
-- (WhatsApp, call) and, once email verification exists, the verified email. The numbers themselves stay in
-- identity.mobile_identity and are never copied here. A row exists once a user has chosen; no row means none shared.
CREATE TABLE identity.contact_method (
    account_id  uuid        PRIMARY KEY REFERENCES identity.account (id),
    whatsapp    boolean     NOT NULL,
    call        boolean     NOT NULL,
    email       boolean     NOT NULL,
    updated_at  timestamptz NOT NULL
);
