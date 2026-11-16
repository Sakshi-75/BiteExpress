# Runs a repeat-import check in one dedicated database and removes only that database.
$ErrorActionPreference = 'Stop'
$testDatabase = 'biteexpress_manual_test'
$createdForThisRun = $false

function Get-RestaurantCount {
    $result = & docker compose exec -T mongo mongosh $testDatabase --quiet --eval 'db.restaurants.countDocuments({})'
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the manual-test restaurant count.' }
    return [long]($result | Select-Object -Last 1)
}

try {
    $check = "db.adminCommand({listDatabases:1,nameOnly:true,filter:{name:'$testDatabase'}}).databases.length"
    $existing = & docker compose exec -T mongo mongosh admin --quiet --eval $check
    if ($LASTEXITCODE -ne 0) { throw 'Could not check whether the manual-test database already exists.' }
    if (($existing | Select-Object -Last 1).Trim() -ne '0') {
        throw "The $testDatabase database already exists. Refusing to change or delete it."
    }

    $createdForThisRun = $true
    $connection = "SPRING_MONGODB_URI=mongodb://mongo:27017/$testDatabase"
    & docker compose run --rm -e $connection -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BITEEXPRESS_IMPORT_ENABLED=true app
    if ($LASTEXITCODE -ne 0) { throw 'The first sample import failed.' }
    $firstCount = Get-RestaurantCount
    if ($firstCount -eq 0) { throw 'The first sample import created no restaurants.' }

    & docker compose run --rm -e $connection -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BITEEXPRESS_IMPORT_ENABLED=true app
    if ($LASTEXITCODE -ne 0) { throw 'The repeat sample import failed.' }
    $secondCount = Get-RestaurantCount
    if ($secondCount -ne $firstCount) { throw "Repeat import changed the count: $firstCount -> $secondCount." }
    Write-Output "Repeat import verified: $secondCount restaurants."
}
finally {
    if ($createdForThisRun) {
        $drop = "if (db.getName() !== '$testDatabase') throw new Error('Unexpected database'); printjson(db.dropDatabase())"
        & docker compose exec -T mongo mongosh $testDatabase --quiet --eval $drop
        if ($LASTEXITCODE -ne 0) { throw "Cleanup failed: $testDatabase may still exist." }
        $remaining = & docker compose exec -T mongo mongosh admin --quiet --eval $check
        if ($LASTEXITCODE -ne 0 -or ($remaining | Select-Object -Last 1).Trim() -ne '0') {
            throw "Cleanup could not confirm that $testDatabase was removed."
        }
        Write-Output "Removed the $testDatabase database created for this run."
    }
}
