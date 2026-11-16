# Performance testing

## Reproduce

Start the current app with `docker compose up --build -d`. The load test makes only GET requests and uses the existing `biteexpress` database. It creates no test database or restaurant documents. These PowerShell commands run the four workloads together at 1, 10, and 25 virtual users:

~~~powershell
New-Item -ItemType Directory performance/results -Force | Out-Null
$k6 = 'grafana/k6@sha256:3d8e5c7f1d53ec59408c2ffbd9943602a0985ffcbd4a2430ec00f3ffca89796e'
foreach ($profile in 'smoke','small','medium') {
  docker run --rm --network biteexpress_default -e BASE_URL=http://app:8080 -e PROFILE=$profile -e DURATION=10s `
    -v "${PWD}/performance:/scripts:ro" -v "${PWD}/performance/results:/output" `
    $k6 run --quiet --summary-export="/output/$profile.json" /scripts/restaurants.js
}
~~~

`BASE_URL`, `DURATION`, `VUS`, and `WORKLOAD` are configurable environment variables. `PROFILE` selects 1 (`smoke`), 10 (`small`), or 25 (`medium`) VUs; `VUS` overrides it. `WORKLOAD` selects `all`, `id`, `listing`, `combined`, `geo`, or `natural`. The `all` profile retains the original four search workloads; `id` requires an existing UUID in `RESTAURANT_ID`. For an endpoint-specific run:

~~~powershell
docker run --rm --network biteexpress_default -e BASE_URL=http://app:8080 -e VUS=10 -e DURATION=5s -e WORKLOAD=geo `
  -v "${PWD}/performance:/scripts:ro" -v "${PWD}/performance/results:/output" `
  $k6 run --quiet --summary-export=/output/geo.json /scripts/restaurants.js
~~~

The script checks HTTP 200 and the page response shape, or the exact returned ID for `WORKLOAD=id`. k6 reports latency p50/p95/p99, `http_reqs` count and rate, `http_req_failed`, and custom `api_errors`. Summaries go to Git-ignored `performance/results/`.

Run the read-only MongoDB explain script with:

~~~powershell
$queryScript = Get-Content -Raw performance/explain.js
docker compose exec -T mongo mongosh biteexpress --quiet --eval $queryScript
~~~

The script reports `explain('executionStats')` for listing count/page, combined count/page, geospatial count/page, and the structured query produced by a representative natural-language request. Set `RESTAURANT_ID` in the `mongosh` process to add the exact-ID plan. It never creates or drops indexes.

## Observed run — 16 November 2026

Windows 11, Docker Desktop, Java 21 app image rebuilt from the then-current source, MongoDB 8.0.38, k6 2.4.0. The existing `biteexpress.restaurants` collection contained 9,554 documents. The app and MongoDB ran on the same Docker Desktop host; k6 ran in a separate container through `host.docker.internal`. The commands above now use the Compose network because the app's host port is bound to `127.0.0.1`. These short runs are local smoke benchmarks, not capacity guarantees.

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

All requests passed the response checks; both k6 error metrics were 0%. Workloads were run sequentially, with no deliberate think time. Results include network and application time. The 25-VU run had lower throughput and much higher latency than the 10-VU run, suggesting saturation in this short local test; longer repeat runs are needed to establish a capacity limit.

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

### Read-only plan recheck — 17 November 2026

The same explain script was rerun against the existing 9,554-document development collection **without changing its records or indexes**. Plans and scan counts were unchanged: listing/combined/natural count queries each examined 9,554 documents and zero keys; geo count and page each examined 980 documents and 1,213 keys using `location_2dsphere`. Measured execution times in this recheck were 4 ms (listing count), 5 ms (listing page), 7 ms (combined count), 7 ms (combined page), 9 ms (geo count), 8 ms (geo page), and 7 ms (natural count). These individual explain timings vary with cache and host state and are not a before/after optimization result.

A separate 3-second, 1-VU mixed-workload connectivity smoke test on 17 November used the Compose network after the localhost port change: 196 requests, 65.13 requests/s, p50 14.51 ms, p95 23.03 ms, p99 34.41 ms, and 0% API errors. Its short duration makes it unsuitable for comparison with the 16 November benchmarks.

## GET by ID and current endpoint comparison — 17 November 2026

The current Java 21 app image and MongoDB 8.0.38 ran in disposable containers on the same Windows 11 / Docker Desktop host. The bundled full CSV plus three sample rows produced 9,554 documents with deterministic UUIDs. k6 2.4.0 ran on the Compose network without think time, using the existing script and image digest above. The chosen full-dataset restaurant was `Cafe Arabelle`, UUID `25353a27-cd07-3b7d-95f1-32eed9819391`. The script checked both HTTP 200 and the returned `id` on every request. No development database was modified.

Run `docker compose up --build -d` first, then reproduce the isolated suite with:

~~~powershell
.\performance\run-id-benchmark.ps1
# Or select another existing UUID after seeding:
.\performance\run-id-benchmark.ps1 -RestaurantId 25353a27-cd07-3b7d-95f1-32eed9819391
~~~

The helper creates uniquely named MongoDB and app containers without volumes or published ports, seeds the bundled data, runs the three ID profiles and all five endpoint workloads, writes ignored JSON summaries to `performance/results/`, runs the read-only explain script, and stops both containers in `finally`. `-IdDuration` and `-EndpointDuration` override the default 10-second and 5-second durations. A separate 1-second-per-workload run verified the helper and its cleanup; the following measurements are from the longer runs.

| GET by ID | VUs | Duration | Requests | Requests/s | p50 ms | p95 ms | p99 ms | API errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| UUID lookup | 1 | 10 s | 13,635 | 1,363.44 | 0.56 | 1.20 | 1.92 | 0% |
| UUID lookup | 10 | 10 s | 135,891 | 13,588.26 | 0.59 | 0.95 | 1.72 | 0% |
| UUID lookup | 25 | 10 s | 216,616 | 21,657.87 | 0.92 | 1.69 | 2.50 | 0% |

To compare endpoint workloads on the **same isolated app and dataset**, each was also run separately at 10 VUs for 5 seconds. These are different query shapes and response sizes, not equivalent A/B operations.

| Current endpoint workload | Requests | Requests/s | p50 ms | p95 ms | p99 ms | API errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Listing | 537 | 106.48 | 90.47 | 115.53 | 168.99 | 0% |
| Combined filters | 445 | 87.95 | 116.57 | 145.22 | 176.79 | 0% |
| Geospatial | 1,306 | 260.43 | 37.44 | 42.68 | 48.84 | 0% |
| Natural language | 381 | 75.00 | 135.33 | 161.42 | 186.67 | 0% |
| GET by ID | 59,948 | 11,987.70 | 0.68 | 1.07 | 1.54 | 0% |

The same disposable collection had `_id_` and `location_2dsphere` indexes. `find({_id: UUID}).limit(1).explain('executionStats')` returned `EXPRESS_IXSCAN` on `_id_`: **1 index key examined, 1 document examined, 1 returned, 0 ms reported execution time**. The indexed plan is evidence for the exact-ID query, not a prediction of production latency. Endpoint runs followed the ID profiles and were sequential, so cache state and run order may affect results. The 16 November endpoint numbers above used a different database state and network route; compare the five current rows with each other only as observations from this run, and do not treat differences from the older rows as measured improvements.

## Decisions and limits

The 16 November load and explain tables remain historical measurements. The 17 November suite uses the current code and an isolated database; no valid before/after application optimization comparison exists. The full-scan plans are measurable, but 9,554 documents are small and these short runs do not establish that an additional index would improve the complete request. Case-insensitive regexes would need a different search strategy or normalized fields to benefit reliably from ordinary B-tree indexes; that would change storage and import behavior. The existing geospatial index is justified by the observed plan. Separate geospatial count and page aggregations remain: a `$facet` rewrite would add complexity without measured evidence of benefit.

The data includes the local full CSV import and three sample records. Results depend on host load, caches, Docker resources, and data distribution; run longer tests with stable traffic and a representative deployment before setting production targets. The benchmark does not exercise writes, cold starts, deep pages, or concurrent imports.
