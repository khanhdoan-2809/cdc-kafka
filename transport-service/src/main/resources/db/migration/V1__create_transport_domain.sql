/*
 * ============================================================
 * TRANSPORT
 * ============================================================
 */

CREATE TABLE transport (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    reference VARCHAR(100) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,

    deleted_at TIMESTAMPTZ
);


/*
 * Only active transports must have unique references.
 *
 * A soft-deleted TR-001 does not prevent a new TR-001
 * from being created later.
 */
CREATE UNIQUE INDEX uk_transport_reference_active
    ON transport(reference)
    WHERE deleted_at IS NULL;


/*
 * ============================================================
 * JOB
 * ============================================================
 */

CREATE TABLE job (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    transport_id BIGINT NOT NULL,

    reference VARCHAR(100) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,

    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_job_transport
        FOREIGN KEY (transport_id)
        REFERENCES transport(id)
        ON DELETE RESTRICT
);


CREATE INDEX idx_job_transport_id
    ON job(transport_id);


/*
 * Job reference only needs to be unique inside one Transport.
 */
CREATE UNIQUE INDEX uk_job_reference_active
    ON job(
        transport_id,
        reference
    )
    WHERE deleted_at IS NULL;


/*
 * ============================================================
 * CONTAINER
 * ============================================================
 */

CREATE TABLE container (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    job_id BIGINT NOT NULL,

    container_number VARCHAR(50) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,

    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_container_job
        FOREIGN KEY (job_id)
        REFERENCES job(id)
        ON DELETE RESTRICT
);


CREATE INDEX idx_container_job_id
    ON container(job_id);


CREATE UNIQUE INDEX uk_container_number_active
    ON container(container_number)
    WHERE deleted_at IS NULL;


/*
 * ============================================================
 * CDC
 * ============================================================
 *
 * We want Debezium to receive the complete OLD row for UPDATE
 * and DELETE events.
 */

ALTER TABLE transport
    REPLICA IDENTITY FULL;

ALTER TABLE job
    REPLICA IDENTITY FULL;

ALTER TABLE container
    REPLICA IDENTITY FULL;


/*
 * Debezium may perform an initial snapshot, therefore it needs
 * SELECT permissions on the captured tables.
 *
 * The "debezium" role must already exist before Flyway runs.
 */

GRANT SELECT
ON TABLE transport
TO debezium;

GRANT SELECT
ON TABLE job
TO debezium;

GRANT SELECT
ON TABLE container
TO debezium;


/*
 * ============================================================
 * CDC PUBLICATION
 * ============================================================
 *
 * Only business tables are published.
 *
 * There is intentionally NO audit table in this database.
 */

CREATE PUBLICATION transport_cdc_publication
FOR TABLE
    transport,
    job,
    container
WITH (
    publish = 'insert, update, delete'
);