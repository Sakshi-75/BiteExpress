param(
    [string]$RestaurantId,
    [string]$IdDuration = '10s',
    [string]$EndpointDuration = '5s'
)

# Requires docker compose up --build -d. All writes go to disposable containers.
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$mongo = "biteexpress-idbench-mongo-$suffix"
$app = "biteexpress-idbench-app-$suffix"
$databaseUri = "mongodb://${mongo}:27017/biteexpress_bench"
$baseUrl = "http://${app}:8080"
$k6 = 'grafana/k6@sha256:3d8e5c7f1d53ec59408c2ffbd9943602a0985ffcbd4a2430ec00f3ffca89796e'
$mongoStarted = $false
$appStarted = $false
New-Item -ItemType Directory performance/results -Force | Out-Null

try {
    docker run --rm -d --name $mongo --network biteexpress_default mongo:8.0 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start disposable MongoDB' }
    $mongoStarted = $true
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        docker exec $mongo mongosh --quiet --eval 'db.adminCommand({ping:1}).ok' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Disposable MongoDB did not become ready' }

    foreach ($file in 'sample-restaurants.csv', 'zomato.csv') {
        docker compose run --rm --no-deps -e SPRING_MAIN_WEB_APPLICATION_TYPE=none `
            -e BITEEXPRESS_IMPORT_ENABLED=true -e "BITEEXPRESS_IMPORT_FILE=file:./data/$file" `
            -e "SPRING_MONGODB_URI=$databaseUri" app | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Import failed: $file" }
    }

    if (-not $RestaurantId) {
        $RestaurantId = (docker exec $mongo mongosh biteexpress_bench --quiet `
            --eval "print(db.restaurants.findOne({name:'Cafe Arabelle'})._id)").Trim()
    }
    if ($RestaurantId -notmatch '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$') {
        throw 'RestaurantId must be an existing UUID in the seeded database'
    }
    $matchingIds = (docker exec $mongo mongosh biteexpress_bench --quiet `
        --eval "print(db.restaurants.countDocuments({_id:'$RestaurantId'}))").Trim()
    if ($LASTEXITCODE -ne 0 -or $matchingIds -ne '1') {
        throw 'RestaurantId does not exist in the seeded database'
    }
    Write-Output "Benchmark UUID: $RestaurantId"

    docker run --rm -d --name $app --network biteexpress_default `
        -e "SPRING_MONGODB_URI=$databaseUri" bite-express:local | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start disposable app' }
    $appStarted = $true
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        if ((docker logs $app 2>&1 | Out-String).Contains('Started BiteExpressApplication')) {
            $ready = $true; break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Disposable app did not become ready' }

    foreach ($vus in 1, 10, 25) {
        docker run --rm --network biteexpress_default -e "BASE_URL=$baseUrl" -e WORKLOAD=id `
            -e "RESTAURANT_ID=$RestaurantId" -e "VUS=$vus" -e "DURATION=$IdDuration" `
            -v "${PWD}/performance:/scripts:ro" -v "${PWD}/performance/results:/output" `
            $k6 run --quiet "--summary-export=/output/id-${vus}vu-$IdDuration.json" /scripts/restaurants.js
        if ($LASTEXITCODE -ne 0) { throw "GET-by-ID benchmark failed at $vus VUs" }
    }
    foreach ($workload in 'listing', 'combined', 'geo', 'natural', 'id') {
        docker run --rm --network biteexpress_default -e "BASE_URL=$baseUrl" -e "WORKLOAD=$workload" `
            -e "RESTAURANT_ID=$RestaurantId" -e VUS=10 -e "DURATION=$EndpointDuration" `
            -v "${PWD}/performance:/scripts:ro" -v "${PWD}/performance/results:/output" `
            $k6 run --quiet "--summary-export=/output/current-${workload}-10vu-$EndpointDuration.json" /scripts/restaurants.js
        if ($LASTEXITCODE -ne 0) { throw "Endpoint benchmark failed: $workload" }
    }

    docker cp performance/explain.js "${mongo}:/tmp/explain.js"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy explain script' }
    docker exec -e "RESTAURANT_ID=$RestaurantId" $mongo mongosh biteexpress_bench --quiet --file /tmp/explain.js
    if ($LASTEXITCODE -ne 0) { throw 'MongoDB explain failed' }
}
finally {
    if ($appStarted) { docker stop $app | Out-Null }
    if ($mongoStarted) { docker stop $mongo | Out-Null }
}
