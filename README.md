# BiteExpress

BiteExpress is a Java 21 / Spring Boot 4.1.2 restaurant discovery application. It runs with MongoDB through Docker Compose, exposes an Actuator health endpoint, and provides an explicit CSV importer for restaurant data.

## Prerequisites

- Docker Desktop with Compose for the container workflow, or JDK 21 and Maven for IDE runs.
- MongoDB 8.0 is supplied by Compose. The app uses Spring Data MongoDB and Apache Commons CSV.

## Run the app

```sh
docker compose up --build -d
docker compose ps
```

Open [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). MongoDB is reachable from Compass at `mongodb://localhost:27018/biteexpress`, and from containers at `mongo:27017`. Compose waits for MongoDB's ping health check. The named `mongo_data` volume persists across restarts. Ordinary app startup **does not import** any data. MongoDB creates a database only after a document is written.

For an IDE run, start `docker compose up -d mongo`, stop the Compose app if it occupies port 8080 (`docker compose stop app`), select JDK 21, set `SPRING_MONGODB_URI=mongodb://localhost:27018/biteexpress`, and run `com.biteExpress.BiteExpressApplication`. The default import setting is false.

## Data model and source

The single `restaurants` collection stores one document per source restaurant ID. The source ID is retained as the stable MongoDB `_id` (for example, `100001`), including compatibility with previously imported records. Restaurant name is `name`; address, country (`code` and `name`), pricing, rating, and operational features are embedded. `cuisines` is an array. Valid coordinates use a GeoJSON `Point` in `[longitude, latitude]` order. All 21 source columns have a corresponding field in the document. There is no separate countries collection or repository interface; the importer uses `MongoTemplate`.

The three-row [sample CSV](data/sample-restaurants.csv) is an illustrative, redistributable fixture. The full local `data/zomato.csv` is Git ignored and excluded from Docker build context; Compose mounts `data/` read-only at runtime. If needed, obtain the Zomato restaurant dataset (9,551 rows in the local copy) from [Kaggle's Zomato Restaurants Data page](https://www.kaggle.com/datasets/shrutimehta/zomato-restaurants-data), place it at `data/zomato.csv`, and confirm the CSV has the same 21 headers. Dataset redistribution rights have not been verified, so the full file is not committed.

The original country mapping was supplied in Excel. Export the two columns **Country Code** and **Country** to UTF-8 CSV as [data/country-codes.csv](data/country-codes.csv). The included 15-row fixture is sufficient for this Zomato copy. It corrects the supplied spelling of `Phillipines` to `Philippines` for code 162. The importer loads the map once per run, preserves the numeric code, and rejects restaurants with an unmapped code instead of inventing a name.

## Explicit imports

`application.yml` defaults to `biteexpress.import.enabled: false`, `file: file:./data/sample-restaurants.csv`, and `country-file: file:./data/country-codes.csv`. An import is a one-off, non-web process:

```sh
docker compose build app
docker compose run --rm -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BITEEXPRESS_IMPORT_ENABLED=true app
```

To import the full local file:

```sh
docker compose run --rm -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BITEEXPRESS_IMPORT_ENABLED=true -e BITEEXPRESS_IMPORT_FILE=file:./data/zomato.csv app
```

Set `BITEEXPRESS_IMPORT_COUNTRY_FILE` if the country CSV is elsewhere. From an IDE, use the same properties as environment variables plus `SPRING_MONGODB_URI=mongodb://localhost:27018/biteexpress` and `SPRING_MAIN_WEB_APPLICATION_TYPE=none`. A missing file, malformed header, or database failure exits with an error. A run with zero valid rows fails. Database writes are not transactional, so a failure after some saves may leave a partial import.

The reader streams rows, retaining at most 250 restaurants for saving. Documents are saved in source order by stable `_id`: a repeated import updates the same documents, and the **last valid occurrence** of an ID within a CSV wins. The completion log reports processed, valid, saved/upserted, rejected, and unavailable-location counts, duration, rejection reason counts, and up to ten row examples. `saved/upserted` means write operations, not newly inserted documents. Imports intentionally overwrite documents with matching IDs; normal app startup never does.

## Cleanup policy

- Trim string fields. A missing ID or name, unknown country code, malformed nonblank number, out-of-range rating/price range, negative cost/votes, or unsupported Yes/No value rejects that row. An inconsistent column count rejects that row. A malformed CSV structure or missing required header fails the file.
- Empty optional address, city, currency, locality, cost, price range, votes, rating color/text, and feature fields become `null`; empty cuisines become `[]`. Cuisines are split on commas and each item is trimmed.
- Blank or `Not rated` aggregate rating becomes `null`, even when the source encodes the unrated value as `0`; a genuine rated zero remains zero.
- Missing, malformed, out-of-range, or `(0, 0)` coordinates produce a `null` location without rejecting an otherwise valid row. The source coordinate values are not stored separately because they are unavailable/invalid for GeoJSON use.
- Source currency is preserved. The local full CSV has 22 records with country code 162 (Philippines) and `Botswana Pula(P)` currency; the importer does not infer a replacement currency.

## Verify data and tests

In Compass, refresh `biteexpress` after an import, open `restaurants`, and inspect `country`, `cuisines`, `pricing`, `rating`, and `location`. Or use:

```sh
docker compose exec mongo mongosh biteexpress --quiet --eval "print(db.restaurants.countDocuments({})); printjson(db.restaurants.findOne())"
```

Run tests with the Maven wrapper (or `mvn` with JDK 21):

```sh
./mvnw test
./mvnw verify
```

`docker compose build app` also runs the tests via the Dockerfile. The unit tests do not require a running database. A separate isolated database should be used for any manual repeat-import check; do not delete the development database to test idempotency.

The MongoDB integration test runs only when `BITEEXPRESS_TEST_MONGODB_URI` is set to a database whose name starts with `biteexpress_import_test_`. For example, with JDK 21 and MongoDB running, set it to `mongodb://localhost:27018/biteexpress_import_test_local` before `./mvnw test`. This test creates uniquely named records and leaves them in that isolated database.

## Stop services

`docker compose down` removes containers and the network while preserving the MongoDB volume. Only if you intentionally want to erase all local MongoDB data, use `docker compose down --volumes`.
