import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 50,
    duration: '10s',
    thresholds: {
        'http_req_duration': ['p(95)<200'],
        'http_req_failed': ['rate<0.01']
    }
};

export default function () {
    const url = 'http://host.docker.internal:8080/api/v1/profiles/me';
    
    // In a real scenario, this would dynamically fetch a token. For baseline testing, we assume a static test token or mock.
    const params = {
        headers: {
            'Authorization': 'Bearer test-token-for-load-testing',
            'Content-Type': 'application/json',
        },
    };

    const res = http.get(url, params);
    
    check(res, {
        'is status 200': (r) => r.status === 200,
    });
    
    sleep(1);
}
