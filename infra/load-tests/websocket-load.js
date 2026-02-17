import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// --- Configuration ---
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';
const TEST_PHONE_PREFIX = '+90500000'; // Safe unallocated Turkish prefix

// --- Custom Metrics ---
const wsConnectRate = new Rate('ws_connect_success');
const wsMessageRate = new Rate('ws_message_delivered');
const wsLatency = new Trend('ws_message_latency_ms');
const messagesSent = new Counter('messages_sent');
const messagesReceived = new Counter('messages_received');

// --- Scenarios ---
export const options = {
    scenarios: {
        // Scenario 1: Ramp up WebSocket connections
        websocket_connections: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // Ramp to 50
                { duration: '1m', target: 100 },   // Ramp to 100
                { duration: '2m', target: 100 },   // Hold at 100
                { duration: '30s', target: 0 },    // Ramp down
            ],
            exec: 'wsConnectionTest',
        },
        // Scenario 2: HTTP API load
        http_api: {
            executor: 'constant-arrival-rate',
            rate: 50,          // 50 requests per second
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 20,
            maxVUs: 50,
            exec: 'httpApiTest',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],       // 95% of HTTP requests under 500ms
        ws_message_latency_ms: ['p(95)<200'],   // 95% of WS messages under 200ms
        ws_connect_success: ['rate>0.95'],       // 95% WS connections succeed
    },
};

// --- Helper: Get auth token ---
function getAuthToken(vuId) {
    const phone = `${TEST_PHONE_PREFIX}${String(vuId).padStart(4, '0')}`;

    // Request OTP
    const otpReq = http.post(`${BASE_URL}/api/v1/auth/otp/request`, JSON.stringify({
        phoneNumber: phone,
    }), { headers: { 'Content-Type': 'application/json' } });

    if (otpReq.status !== 200) return null;

    // In mock mode, OTP is logged. Use a known test code or extract from response.
    // For load testing, we assume OTP_MOCK_ENABLED=true and use a fixed OTP
    const verifyRes = http.post(`${BASE_URL}/api/v1/auth/otp/verify`, JSON.stringify({
        phoneNumber: phone,
        otp: '123456', // Mock OTP (check console log in dev mode)
        deviceName: `k6-vu-${vuId}`,
        platform: 'android',
    }), { headers: { 'Content-Type': 'application/json' } });

    if (verifyRes.status !== 200) return null;

    const body = JSON.parse(verifyRes.body);
    return body.data?.accessToken || null;
}

// --- Scenario 1: WebSocket Connection + Messaging ---
export function wsConnectionTest() {
    const token = getAuthToken(__VU);
    if (!token) {
        wsConnectRate.add(false);
        return;
    }

    const url = `${WS_URL}?token=${token}`;

    const res = ws.connect(url, {}, function (socket) {
        wsConnectRate.add(true);

        // Send heartbeat ping every 30s
        socket.setInterval(function () {
            socket.send(JSON.stringify({ type: 'ping' }));
        }, 30000);

        // Listen for incoming messages
        socket.on('message', function (msg) {
            messagesReceived.add(1);
            try {
                const parsed = JSON.parse(msg);
                if (parsed.type === 'message.new') {
                    // Send DELIVERED ack
                    socket.send(JSON.stringify({
                        type: 'message.ack',
                        messageId: parsed.messageId,
                        conversationId: parsed.conversationId,
                        status: 'DELIVERED',
                    }));
                }
            } catch (_) {}
        });

        // Send a message every 5 seconds (simulate active user)
        socket.setInterval(function () {
            const sendTime = Date.now();
            socket.send(JSON.stringify({
                type: 'message.send',
                conversationId: 'test-conv', // Would need a real conversation ID
                content: `Load test message from VU ${__VU} at ${sendTime}`,
                contentType: 'TEXT',
                clientMessageId: `${__VU}-${sendTime}`,
            }));
            messagesSent.add(1);
        }, 5000);

        // Listen for server ack to measure latency
        socket.on('message', function (msg) {
            try {
                const parsed = JSON.parse(msg);
                if (parsed.type === 'ack' && parsed.status === 'OK') {
                    wsMessageRate.add(true);
                }
            } catch (_) {}
        });

        // Keep connection open for the VU's lifetime
        sleep(60);

        socket.close();
    });

    check(res, {
        'WebSocket connected': (r) => r && r.status === 101,
    });
}

// --- Scenario 2: HTTP API Load ---
export function httpApiTest() {
    // Health check (unauthenticated)
    const healthRes = http.get(`${BASE_URL}/actuator/health`);
    check(healthRes, {
        'health endpoint returns 200': (r) => r.status === 200,
    });
}

// --- Teardown ---
export function teardown() {
    console.log('Load test complete.');
}
