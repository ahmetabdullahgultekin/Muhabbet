/**
 * k6 Load Test — Auth Flow (OTP Request + Verify)
 *
 * Measures P50/P95/P99 latencies for the OTP authentication flow.
 * Uses mock OTP mode (OTP_MOCK_ENABLED=true) so no real SMS is sent.
 *
 * Usage:
 *   k6 run --env BASE_URL=https://your-domain.com infra/k6/auth-load-test.js
 *
 * Stages:
 *   1. Ramp up to 10 VUs over 30s
 *   2. Hold 10 VUs for 1 minute
 *   3. Ramp up to 25 VUs for 1 minute
 *   4. Ramp down over 30s
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Custom metrics
const otpRequestDuration = new Trend('otp_request_duration', true);
const otpVerifyDuration = new Trend('otp_verify_duration', true);
const authSuccessRate = new Rate('auth_success_rate');

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '1m', target: 25 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        'otp_request_duration': ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
        'otp_verify_duration': ['p(50)<300', 'p(95)<800', 'p(99)<1500'],
        'auth_success_rate': ['rate>0.95'],
        'http_req_failed': ['rate<0.05'],
    },
};

// Generate unique test phone numbers per VU
function getTestPhone() {
    // Use +90500 prefix (unallocated in Turkey, safe for testing)
    const vuId = __VU;
    const iter = __ITER;
    const num = String(vuId * 10000 + iter).padStart(7, '0');
    return `+90500${num}`;
}

export default function () {
    const phone = getTestPhone();
    const headers = { 'Content-Type': 'application/json' };

    // Step 1: Request OTP
    const otpReqPayload = JSON.stringify({ phoneNumber: phone });
    const otpRes = http.post(
        `${BASE_URL}/api/v1/auth/otp/request`,
        otpReqPayload,
        { headers, tags: { name: 'OTP_REQUEST' } }
    );

    otpRequestDuration.add(otpRes.timings.duration);

    const otpOk = check(otpRes, {
        'OTP request returns 200': (r) => r.status === 200,
        'OTP response has ttlSeconds': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.ttlSeconds > 0;
            } catch { return false; }
        },
    });

    if (!otpOk) {
        authSuccessRate.add(false);
        sleep(1);
        return;
    }

    sleep(0.5); // Brief pause between OTP request and verify

    // Step 2: Verify OTP (mock mode returns fixed code from logs)
    // In mock mode, OTP is logged — for load testing we try common mock codes
    const verifyPayload = JSON.stringify({
        phoneNumber: phone,
        code: '123456', // Mock OTP sender typically uses a fixed code
    });
    const verifyRes = http.post(
        `${BASE_URL}/api/v1/auth/otp/verify`,
        verifyPayload,
        { headers, tags: { name: 'OTP_VERIFY' } }
    );

    otpVerifyDuration.add(verifyRes.timings.duration);

    const verifyOk = check(verifyRes, {
        'OTP verify returns 200': (r) => r.status === 200,
        'Verify response has accessToken': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.accessToken;
            } catch { return false; }
        },
    });

    authSuccessRate.add(verifyOk);

    sleep(1);
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: '  ', enableColors: true }),
    };
}

function textSummary(data, opts) {
    // k6 built-in text summary
    return JSON.stringify(data, null, 2);
}
