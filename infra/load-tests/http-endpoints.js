import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// --- Configuration ---
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_PHONE = '+905000001234';

// --- Custom Metrics ---
const authLatency = new Trend('auth_latency_ms');
const conversationLatency = new Trend('conversation_latency_ms');
const mediaLatency = new Trend('media_latency_ms');
const apiSuccess = new Rate('api_success_rate');

// --- Options ---
export const options = {
    scenarios: {
        // Steady state: simulate 50 concurrent users making API calls
        steady_load: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
        },
        // Spike test: sudden burst
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 200 },  // Spike to 200
                { duration: '30s', target: 200 },  // Hold
                { duration: '10s', target: 0 },    // Drop
            ],
            startTime: '3m30s', // Start after steady state
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        api_success_rate: ['rate>0.99'],
        auth_latency_ms: ['p(95)<500'],
        conversation_latency_ms: ['p(95)<300'],
    },
};

// --- Setup: Get auth token ---
export function setup() {
    // Request OTP
    http.post(`${BASE_URL}/api/v1/auth/otp/request`, JSON.stringify({
        phoneNumber: TEST_PHONE,
    }), { headers: { 'Content-Type': 'application/json' } });

    // Verify OTP (assuming mock mode)
    const verifyRes = http.post(`${BASE_URL}/api/v1/auth/otp/verify`, JSON.stringify({
        phoneNumber: TEST_PHONE,
        otp: '123456',
        deviceName: 'k6-load-test',
        platform: 'android',
    }), { headers: { 'Content-Type': 'application/json' } });

    if (verifyRes.status !== 200) {
        console.error(`Auth failed: ${verifyRes.status} ${verifyRes.body}`);
        return { token: null };
    }

    const body = JSON.parse(verifyRes.body);
    return { token: body.data?.accessToken };
}

// --- Main Test ---
export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.token}`,
    };

    group('Health & Info', function () {
        const res = http.get(`${BASE_URL}/actuator/health`);
        check(res, { 'health 200': (r) => r.status === 200 });
        apiSuccess.add(res.status === 200);
    });

    group('User Profile', function () {
        const res = http.get(`${BASE_URL}/api/v1/users/me`, { headers });
        const ok = res.status === 200;
        check(res, { 'profile 200': () => ok });
        apiSuccess.add(ok);
    });

    group('Conversations', function () {
        const start = Date.now();
        const res = http.get(`${BASE_URL}/api/v1/conversations`, { headers });
        conversationLatency.add(Date.now() - start);
        const ok = res.status === 200;
        check(res, { 'conversations 200': () => ok });
        apiSuccess.add(ok);
    });

    group('Contact Sync', function () {
        // Generate 100 fake phone hashes
        const hashes = Array.from({ length: 100 }, (_, i) =>
            `hash_${__VU}_${i}_${Date.now()}`
        );
        const res = http.post(`${BASE_URL}/api/v1/contacts/sync`, JSON.stringify({
            phoneHashes: hashes,
        }), { headers });
        const ok = res.status === 200;
        check(res, { 'contact sync 200': () => ok });
        apiSuccess.add(ok);
    });

    group('Auth OTP Request', function () {
        const phone = `+90500${String(__VU).padStart(7, '0')}`;
        const start = Date.now();
        const res = http.post(`${BASE_URL}/api/v1/auth/otp/request`, JSON.stringify({
            phoneNumber: phone,
        }), { headers: { 'Content-Type': 'application/json' } });
        authLatency.add(Date.now() - start);
        // May be rate-limited (429) which is expected
        const ok = res.status === 200 || res.status === 429;
        check(res, { 'otp request ok or rate-limited': () => ok });
        apiSuccess.add(ok);
    });

    sleep(1); // 1 request/sec per VU
}
