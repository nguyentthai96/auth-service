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
    const url = 'http://host.docker.internal:8080/api/v1/auth/login';
    const payload = JSON.stringify({
        username: 'loaduser',
        password: 'password123'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);
    
    check(res, {
        'is status 200': (r) => r.status === 200,
    });
    
    sleep(1);
}
