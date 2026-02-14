/**
 * k6 Load Test — REST API Endpoints
 *
 * Measures P50/P95/P99 latencies for core REST APIs under load.
 * Requires a valid JWT token (set via --env TOKEN=...).
 *
 * Usage:
 *   k6 run --env BASE_URL=https://your-domain.com --env TOKEN=eyJ... infra/k6/api-load-test.js
 *
 * Stages:
 *   1. Ramp up to 20 VUs over 30s
 *   2. Sustain 20 VUs for 2 minutes
 *   3. Spike to 50 VUs for 30s
 *   4. Ramp down over 30s
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

// Custom metrics per endpoint
const getConversationsDuration = new Trend('get_conversations_duration', true);
const getMessagesDuration = new Trend('get_messages_duration', true);
const getUserProfileDuration = new Trend('get_user_profile_duration', true);
const healthCheckDuration = new Trend('health_check_duration', true);
const apiSuccessRate = new Rate('api_success_rate');

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        'get_conversations_duration': ['p(50)<100', 'p(95)<300', 'p(99)<500'],
        'get_messages_duration': ['p(50)<100', 'p(95)<300', 'p(99)<500'],
        'get_user_profile_duration': ['p(50)<50', 'p(95)<200', 'p(99)<400'],
        'health_check_duration': ['p(50)<30', 'p(95)<100', 'p(99)<200'],
        'api_success_rate': ['rate>0.95'],
        'http_req_failed': ['rate<0.05'],
    },
};

const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${TOKEN}`,
};

export default function () {
    // Health check (unauthenticated)
    group('Health Check', () => {
        const res = http.get(`${BASE_URL}/actuator/health`, {
            tags: { name: 'HEALTH' },
        });
        healthCheckDuration.add(res.timings.duration);
        check(res, { 'health returns 200': (r) => r.status === 200 });
    });

    if (!TOKEN) {
        sleep(1);
        return;
    }

    // GET /api/v1/users/me
    group('Get User Profile', () => {
        const res = http.get(`${BASE_URL}/api/v1/users/me`, {
            headers: authHeaders,
            tags: { name: 'GET_PROFILE' },
        });
        getUserProfileDuration.add(res.timings.duration);
        const ok = check(res, {
            'profile returns 200': (r) => r.status === 200,
            'profile has userId': (r) => {
                try {
                    return JSON.parse(r.body).data.id !== undefined;
                } catch { return false; }
            },
        });
        apiSuccessRate.add(ok);
    });

    // GET /api/v1/conversations
    group('Get Conversations', () => {
        const res = http.get(`${BASE_URL}/api/v1/conversations?limit=20`, {
            headers: authHeaders,
            tags: { name: 'GET_CONVERSATIONS' },
        });
        getConversationsDuration.add(res.timings.duration);
        const ok = check(res, {
            'conversations returns 200': (r) => r.status === 200,
            'conversations has items': (r) => {
                try {
                    return Array.isArray(JSON.parse(r.body).data.items);
                } catch { return false; }
            },
        });
        apiSuccessRate.add(ok);
    });

    // GET /api/v1/conversations/{id}/messages (first conversation)
    group('Get Messages', () => {
        // First get conversations to find a valid ID
        const convRes = http.get(`${BASE_URL}/api/v1/conversations?limit=1`, {
            headers: authHeaders,
        });
        let convId = null;
        try {
            const items = JSON.parse(convRes.body).data.items;
            if (items && items.length > 0) convId = items[0].id;
        } catch { /* no conversations */ }

        if (convId) {
            const res = http.get(
                `${BASE_URL}/api/v1/conversations/${convId}/messages?limit=50`,
                { headers: authHeaders, tags: { name: 'GET_MESSAGES' } }
            );
            getMessagesDuration.add(res.timings.duration);
            const ok = check(res, {
                'messages returns 200': (r) => r.status === 200,
                'messages has items': (r) => {
                    try {
                        return Array.isArray(JSON.parse(r.body).data.items);
                    } catch { return false; }
                },
            });
            apiSuccessRate.add(ok);
        }
    });

    sleep(1);
}
