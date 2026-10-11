import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = __ENV.K6_BASE_URL || 'http://transport-service:8080';
const profile = __ENV.K6_PROFILE || 'baseline';

const profiles = {
    baseline: {
        executor: 'constant-arrival-rate',
        rate: 5,
        timeUnit: '1s',
        duration: '5m',
        preAllocatedVUs: 10,
        maxVUs: 50,
        exec: 'cdcFlow'
    },

    stress: {
        executor: 'ramping-arrival-rate',
        startRate: 5,
        timeUnit: '1s',
        preAllocatedVUs: 20,
        maxVUs: 200,
        stages: [
            { target: 10, duration: '1m' },
            { target: 20, duration: '1m' },
            { target: 30, duration: '1m' },
            { target: 50, duration: '2m' },
            { target: 0, duration: '30s' }
        ],
        exec: 'cdcFlow'
    },

    spike: {
        executor: 'ramping-arrival-rate',
        startRate: 5,
        timeUnit: '1s',
        preAllocatedVUs: 20,
        maxVUs: 200,
        stages: [
            { target: 5, duration: '30s' },
            { target: 50, duration: '0s' },
            { target: 50, duration: '30s' },
            { target: 5, duration: '30s' },
            { target: 0, duration: '15s' }
        ],
        exec: 'cdcFlow'
    }
};

if (!profiles[profile]) {
    throw new Error(`Unknown K6_PROFILE: ${profile}`);
}

export const options = {
    discardResponseBodies: false,

    scenarios: {
        cdc: profiles[profile]
    },

    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        http_req_duration: [
            'p(95)<1000',
            'p(99)<2000'
        ]
    }
};

function headers(actor) {
    const id = `${__VU}-${__ITER}-${Date.now()}`;

    return {
        'Content-Type': 'application/json',
        'X-User-Id': actor,
        'X-Request-Id': `load-${id}`,
        'X-Correlation-Id': `load-${id}`
    };
}

function json(response) {
    try {
        return response.json();
    } catch (e) {
        fail(`Invalid JSON response: status=${response.status}, body=${response.body}`);
    }
}

function requireId(response, name) {
    const body = json(response);

    if (!body.id) {
        fail(`${name} response does not contain id: ${response.body}`);
    }

    return body.id;
}

export function cdcFlow() {
    const unique = `${__VU}-${__ITER}-${Date.now()}`;
    const actor = `k6-user-${__VU}`;
    const params = {
        headers: headers(actor)
    };

    const transportResponse = http.post(
        `${baseUrl}/api/transports`,
        JSON.stringify({
            reference: `TR-${unique}`
        }),
        {
            ...params,
            tags: {
                operation: 'create_transport'
            }
        }
    );

    const transportCreated = check(transportResponse, {
        'transport created': response => response.status === 201
    });

    if (!transportCreated) {
        return;
    }

    const transportId = requireId(transportResponse, 'transport');

    const jobResponse = http.post(
        `${baseUrl}/api/transports/${transportId}/jobs`,
        JSON.stringify({
            reference: `JOB-${unique}`
        }),
        {
            ...params,
            tags: {
                operation: 'create_job'
            }
        }
    );

    const jobCreated = check(jobResponse, {
        'job created': response => response.status === 201
    });

    if (!jobCreated) {
        return;
    }

    const jobId = requireId(jobResponse, 'job');

    const containerResponse = http.post(
        `${baseUrl}/api/jobs/${jobId}/containers`,
        JSON.stringify({
            containerNumber: `CONT-${unique}`
        }),
        {
            ...params,
            tags: {
                operation: 'create_container'
            }
        }
    );

    const containerCreated = check(containerResponse, {
        'container created': response => response.status === 201
    });

    if (!containerCreated) {
        return;
    }

    const containerId = requireId(containerResponse, 'container');

    const transportUpdate = http.patch(
        `${baseUrl}/api/transports/${transportId}/status`,
        JSON.stringify({
            status: 'PLANNED'
        }),
        {
            ...params,
            tags: {
                operation: 'update_transport'
            }
        }
    );

    check(transportUpdate, {
        'transport updated': response => response.status === 200
    });

    const jobUpdate = http.patch(
        `${baseUrl}/api/jobs/${jobId}/status`,
        JSON.stringify({
            status: 'READY'
        }),
        {
            ...params,
            tags: {
                operation: 'update_job'
            }
        }
    );

    check(jobUpdate, {
        'job updated': response => response.status === 200
    });

    const containerUpdate = http.patch(
        `${baseUrl}/api/containers/${containerId}/status`,
        JSON.stringify({
            status: 'LOADED'
        }),
        {
            ...params,
            tags: {
                operation: 'update_container'
            }
        }
    );

    check(containerUpdate, {
        'container updated': response => response.status === 200
    });
}