import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const profile = __ENV.PROFILE || 'small';
const virtualUsers = Number(__ENV.VUS || ({ smoke: 1, small: 10, medium: 25 }[profile]));
if (!Number.isInteger(virtualUsers) || virtualUsers < 1) {
  throw new Error('Set PROFILE to smoke, small, or medium, or set VUS to a positive integer');
}

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const duration = __ENV.DURATION || '15s';
const workload = __ENV.WORKLOAD || 'all';
const paths = {
  listing: '/v1/restaurants?page=0&size=20&sortBy=name',
  combined: '/v1/restaurants?city=Gurgaon&cuisine=North%20Indian&minRating=3&maxCostForTwo=2000&currency=INR&page=0&size=20',
  geo: '/v1/restaurants?latitude=28.4674&longitude=77.0639&radiusKm=5&sortBy=distance&page=0&size=20',
  natural: '/v1/restaurants/search?q=best%20North%20Indian%20restaurants%20in%20Gurugram%20under%20Rs%202000%20for%20two&page=0&size=20',
};
const selected = workload === 'all' ? Object.keys(paths) : [workload];
if (selected.some((name) => !paths[name])) {
  throw new Error('WORKLOAD must be all, listing, combined, geo, or natural');
}

const apiErrors = new Rate('api_errors');
export const options = {
  scenarios: { restaurants: { executor: 'constant-vus', vus: virtualUsers, duration } },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: { http_req_failed: ['rate<0.01'], api_errors: ['rate<0.01'] },
};

export default function () {
  const name = selected[(__ITER + __VU - 1) % selected.length];
  const response = http.get(baseUrl + paths[name], { tags: { workload: name }, timeout: '30s' });
  const valid = check(response, {
    'HTTP 200': (r) => r.status === 200,
    'page response': (r) => {
      try {
        const body = r.json();
        return Array.isArray(body.content) && body.page === 0 &&
          Number.isInteger(body.totalElements) && Number.isInteger(body.totalPages);
      } catch (_) {
        return false;
      }
    },
  });
  apiErrors.add(!valid, { workload: name });
}
