CREATE TABLE transport (
    id BIGSERIAL PRIMARY KEY,
    reference VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE job (
    id BIGSERIAL PRIMARY KEY,
    transport_id BIGINT NOT NULL,
    reference VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_job_transport
        FOREIGN KEY (transport_id)
        REFERENCES transport(id)
);

CREATE INDEX idx_job_transport_id ON job(transport_id);

CREATE TABLE container (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    container_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_container_job
        FOREIGN KEY (job_id)
        REFERENCES job(id),

    CONSTRAINT uk_container_number
        UNIQUE(container_number)
);

CREATE INDEX idx_container_job_id ON container(job_id);