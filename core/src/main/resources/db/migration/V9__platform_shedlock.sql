-- ShedLock's lock table (ADR-0010). It only makes one instance run a scheduled job per tick; correctness of every
-- job comes from its conditional SQL, never from this lock. Column names and types are the ones ShedLock requires.
CREATE TABLE platform.shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
