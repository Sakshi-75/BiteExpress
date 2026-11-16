# Performance testing

## Reproduce

Start the current app with `docker compose up --build -d`. The load test makes only GET requests and uses the existing `biteexpress` database. It creates no test database or restaurant documents. These PowerShell commands run the four workloads together at 1, 10, and 25 virtual users:

~~~powershell
New-Item -ItemType Directory performance/results -Force | Out-Null
$k6 = 'grafana/k6@sha256:3d8e5c7f1d53ec59408c2ffbd9943602a0985ffcbd4a2430ec00f3ffca89796e'
foreach ($profile in 'smoke','small','medium') {
  docker run --rm -e BASE_URL=http://host.docker.internal:8080 -e PROFILE=$profile -e DURATION=10s `
    -v "${PWD}/performance:/scripts:ro" -v "${PWD}/performance/results:/output" `
    $k6 run --quiet --summary-export="/output/$profile.json" /scripts/restaurants.js
}
~~~

`BASE_URL`, `DURATION`, `VUS`, and `WORKLOAD` are configurable environment variables. `PROFILE` selects 1 (`smoke`), 10 (`small`), or 25 (`medium`) VUs; `VUS` overrides it. `WORKLOAD` selects `all`, `listing`, `combined`, `geo`, or `natural`. For an endpoint-specific run:

~~~powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080 -e VUS=10 -e DURATION=5s -e WORKLOAD=geo `
  -v "${PWD}/performance:/scripts:ro" -v "${PWD}/performance/results:/output" `
  $k6 run --quiet --summary-export=/output/geo.json /scripts/restaurants.js
~~~

The script checks HTTP 200 and the page response shape. k6 reports latency p50/p95/p99, `http_reqs` count and rate, `http_req_failed`, and custom `api_errors`. Summaries go to Git-ignored `performance/results/`.

Run the read-only MongoDB explain script with:

~~~powershell
$queryScript = Get-Content -Raw performance/explain.js
docker compose exec -T mongo mongosh biteexpress --quiet --eval $queryScript
~~~

The script reports `explain('executionStats')` for listing count/page, combined count/page, geospatial count/page, and the structured query produced by a representative natural-language request. It never creates or drops indexes.

## Observed run — 16 November 2026

Windows 11, Docker Desktop, Java 21 app image rebuilt from the current source, MongoDB 8.0.38, k6 2.4.0. The existing `biteexpress.restaurants` collection contained 9,554 documents. The app and MongoDB ran on the same Docker Desktop host; k6 ran in a separate container through `host.docker.internal`. These short runs are local smoke benchmarks, not capacity guarantees.

| Mixed workload | VUs | Duration | Requests | Requests/s | p50 ms | p95 ms | p99 ms | API errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| smoke | 1 | 10 s | 603 | 60.26 | 15.98 | 22.59 | 34.98 | 0% |
| small | 10 | 10 s | 1,440 | 143.30 | 58.82 | 156.30 | 167.66 | 0% |
| medium | 25 | 10 s | 1,226 | 118.61 | 211.41 | 461.53 | 501.29 | 0% |

| Single workload | VUs | Duration | Requests | Requests/s | p50 ms | p95 ms | p99 ms | API errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| listing | 10 | 5 s | 878 | 174.64 | 56.68 | 61.71 | 65.54 | 0% |
| combined filters | 10 | 5 s | 427 | 84.04 | 127.19 | 146.34 | 158.59 | 0% |
| geospatial | 10 | 5 s | 1,320 | 263.29 | 37.30 | 42.14 | 45.43 | 0% |
| natural language | 10 | 5 s | 298 | 58.55 | 169.40 | 198.41 | 212.32 | 0% |

All requests passed the response checks; both k6 error metrics were 0%. Workloads were run sequentially, with no deliberate think time. Results include network and application time. The 25-VU run had lower throughput and much higher latency than the 10-VU run, so this local setup was saturated by 25 concurrent clients.

## MongoDB plans

The database had `_id_` and `location_2dsphere` indexes. The explain script's representative combined query matched 265 restaurants; its natural-language equivalent matched 491. A 5 km geospatial query considered 896 nearby locations and matched 215 after the combined filters.

| Query | Winning plan | Index | Keys examined | Documents examined | Execution ms |
| --- | --- | --- | ---: | ---: | ---: |
| listing count | `GROUP → COLLSCAN` | none | 0 | 9,554 | 1 |
| listing page | `SORT → COLLSCAN` | none | 0 | 9,554 | 4 |
| combined count | `GROUP → COLLSCAN` | none | 0 | 9,554 | 6 |
| combined page | `SORT → COLLSCAN` | none | 0 | 9,554 | 6 |
| geospatial count | `GEO_NEAR_2DSPHERE → FETCH → IXSCAN` | `location_2dsphere` | 1,213 | 980 | 7 |
| geospatial page | `GEO_NEAR_2DSPHERE → FETCH → IXSCAN` | `location_2dsphere` | 1,213 | 980 | 6 |
| natural-language count | `GROUP → COLLSCAN` | none | 0 | 9,554 | 7 |

The geospatial rows' keys/documents examined and returned-candidate count describe the `$geoNear` cursor before the later filter, count, and page stages. The service performs separate count and page queries; sorting and pagination happen in MongoDB. The geospatial index is used. Case-insensitive regular-expression filters and the default name sort scan the collection in this dataset. Exact counts also require work even for an empty filter.

## Decisions and limits

No application query or index was changed. The full-scan plans are measurable, but 9,554 documents are small and these short runs do not establish that an additional index would improve the complete request. Case-insensitive regexes would need a different search strategy or normalized fields to benefit reliably from ordinary B-tree indexes; that would change storage and import behavior. The existing geospatial index is justified by the observed plan. There is no valid before/after optimization comparison because no optimization was applied.

The data includes the local full CSV import and three sample records. Results depend on host load, caches, Docker resources, and data distribution; run longer tests with stable traffic and a representative deployment before setting production targets. The benchmark does not exercise writes, cold starts, deep pages, or concurrent imports.
