import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const ttft = new Trend('ragdoc_client_ttft_ms', true);
const failures = new Rate('ragdoc_client_failures');

export const options = {
  scenarios: {
    chat_sse: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<15000'],
    ragdoc_client_ttft_ms: ['p(95)<2000'],
    ragdoc_client_failures: ['rate<0.01'],
  },
};

export default function () {
  const baseUrl = __ENV.CHAT_URL || 'http://localhost:8080';
  const token = __ENV.AUTH_TOKEN;
  if (!token) throw new Error('AUTH_TOKEN is required');

  const response = http.post(
    `${baseUrl}/api/v1/chat/sse`,
    JSON.stringify({ query: __ENV.QUERY || '请概括当前知识库的主要内容', top_k: 5 }),
    { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } },
  );
  const ok = check(response, {
    'status is 200': (r) => r.status === 200,
    'SSE has terminal event': (r) => r.body.includes('event:done'),
  });
  failures.add(!ok);
  ttft.add(response.timings.waiting);
}
