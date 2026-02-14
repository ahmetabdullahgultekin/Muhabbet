/**
 * k6 Load Test — WebSocket Messaging
 *
 * Measures WebSocket connection time, message send latency, and throughput.
 * Requires a valid JWT token (set via --env TOKEN=...).
 *
 * Usage:
 *   k6 run --env WS_URL=wss://your-domain.com --env TOKEN=eyJ... infra/k6/websocket-load-test.js
 *
 * Stages:
 *   1. Ramp up to 10 concurrent WS connections over 20s
 *   2. Hold 10 connections for 1 minute (each sending messages)
 *   3. Ramp up to 30 connections for 1 minute
 *   4. Ramp down over 20s
 */

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

// Custom metrics
const wsConnectDuration = new Trend('ws_connect_duration', true);
const wsSendDuration = new Trend('ws_send_duration', true);
const wsMessagesReceived = new Counter('ws_messages_received');
const wsConnectionSuccess = new Rate('ws_connection_success');

export const options = {
    stages: [
        { duration: '20s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '1m', target: 30 },
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        'ws_connect_duration': ['p(50)<500', 'p(95)<2000', 'p(99)<5000'],
        'ws_send_duration': ['p(50)<100', 'p(95)<300', 'p(99)<500'],
        'ws_connection_success': ['rate>0.90'],
    },
};

export default function () {
    if (!TOKEN) {
        console.warn('No TOKEN provided — skipping WebSocket test');
        sleep(5);
        return;
    }

    const url = `${WS_URL}/ws?token=${TOKEN}`;
    const connectStart = Date.now();

    const res = ws.connect(url, {}, function (socket) {
        const connectTime = Date.now() - connectStart;
        wsConnectDuration.add(connectTime);
        wsConnectionSuccess.add(true);

        // Send GoOnline presence
        socket.send(JSON.stringify({
            type: 'presence.online',
        }));

        // Listen for messages
        socket.on('message', function (msg) {
            wsMessagesReceived.add(1);
        });

        socket.on('error', function (e) {
            console.error('WebSocket error:', e);
        });

        // Send periodic typing indicators and messages
        let messageCount = 0;
        const maxMessages = 5;

        socket.setInterval(function () {
            if (messageCount >= maxMessages) {
                socket.close();
                return;
            }

            // Send typing indicator
            const typingMsg = JSON.stringify({
                type: 'presence.typing',
                conversationId: '00000000-0000-0000-0000-000000000100',
                isTyping: true,
            });

            const sendStart = Date.now();
            socket.send(typingMsg);
            wsSendDuration.add(Date.now() - sendStart);

            messageCount++;
        }, 3000); // Every 3 seconds

        // Close after test duration
        socket.setTimeout(function () {
            socket.close();
        }, 30000);
    });

    check(res, {
        'WS connection established': (r) => r && r.status === 101,
    });

    if (!res || res.status !== 101) {
        wsConnectionSuccess.add(false);
    }

    sleep(2);
}
