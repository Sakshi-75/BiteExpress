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

The single `restaurants` collection stores one document per restaurant. New documents have UUID-string MongoDB `_id` values. API creation uses a random UUID; CSV import derives a deterministic UUID from the original source restaurant ID using Java's name-based UUID method and the UTF-8 input `biteexpress:restaurant:<source-id>`. The source ID is used to derive `_id` and is not stored separately. Restaurant name is `name`; address, country (`code` and `name`), pricing, rating, and operational features are embedded. `cuisines` is an array. Valid coordinates use a GeoJSON `Point` in `[longitude, latitude]` order. There is no separate countries collection. The importer uses `MongoTemplate`; the API uses `RestaurantRepository`.

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

The reader streams rows, retaining at most 250 restaurants for saving. Documents are saved in source order by deterministic UUID: a repeated import updates the same documents, and the **last valid occurrence** of a source ID within a CSV wins. The completion log reports processed, valid, saved/upserted, legacy-skipped, rejected, and unavailable-location counts, duration, rejection reason counts, and up to ten row examples. `saved/upserted` means write operations, not newly inserted documents. Imports intentionally update matching UUID documents; normal app startup never does.

Existing databases may contain restaurants imported before UUID IDs were introduced. Those documents keep their original IDs and remain available through GET. When a CSV source ID already exists as a legacy `_id`, the importer skips that row so a repeat import does not duplicate or overwrite it. This is a compatibility rule, not a data migration: no existing document is deleted or renamed. New imports into an empty database use UUID IDs throughout.

## Cleanup policy

- Trim string fields. A missing ID or name, unknown country code, malformed nonblank number, out-of-range rating/price range, negative cost/votes, or unsupported Yes/No value rejects that row. An inconsistent column count rejects that row. A malformed CSV structure or missing required header fails the file.
- Empty optional address, city, currency, locality, cost, price range, votes, rating color/text, and feature fields become `null`; empty cuisines become `[]`. Cuisines are split on commas and each item is trimmed.
- Blank or `Not rated` aggregate rating becomes `null`, even when the source encodes the unrated value as `0`; a genuine rated zero remains zero.
- Missing, malformed, out-of-range, or `(0, 0)` coordinates produce a `null` location without rejecting an otherwise valid row. The source coordinate values are not stored separately because they are unavailable/invalid for GeoJSON use.
- Source currency is preserved. The local full CSV has 22 records with country code 162 (Philippines) and `Botswana Pula(P)` currency; the importer does not infer a replacement currency.

## Restaurant API

`POST /v1/restaurants` creates a restaurant and returns `201 Created` with the complete restaurant JSON. It does not send a `Location` header. `GET /v1/restaurants/{id}` returns the same representation with `200 OK`, or a `404` problem response when the ID is absent. GET looks up the supplied ID without special handling. Names need not be unique.

Example creation request:

```sh
curl -i -X POST http://localhost:8080/v1/restaurants \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spice House","address":{"line1":"2 Market Road","city":"Gurgaon"},"country":{"name":"India"},"location":{"latitude":28.4595,"longitude":77.0266},"cuisines":["Indian","Chinese"],"pricing":{"averageCostForTwo":800,"currency":"Indian Rupees(Rs.)","priceRange":2},"rating":{"average":4.4,"votes":10},"features":{"hasOnlineDelivery":true}}'
```

The generated ID varies. A successful response has this shape:

```json
{
  "id": "<generated-uuid>",
  "name": "Spice House",
  "address": {"line1": "2 Market Road", "city": "Gurgaon", "locality": null, "localityVerbose": null},
  "country": {"code": null, "name": "India"},
  "location": {"latitude": 28.4595, "longitude": 77.0266},
  "cuisines": ["Indian", "Chinese"],
  "pricing": {"averageCostForTwo": 800, "currency": "Indian Rupees(Rs.)", "priceRange": 2},
  "rating": {"average": 4.4, "color": null, "text": null, "votes": 10},
  "features": {"hasTableBooking": null, "hasOnlineDelivery": true, "isDeliveringNow": null, "switchToOrderMenu": null}
}
```

Retrieve it with `curl -i http://localhost:8080/v1/restaurants/<returned-id>`. The API does not assign a Zomato country code to new restaurants; `country.code` is optional. Location is optional, but when present both coordinates are required. The API accepts a required name (nonblank, at most 200 characters) and address with a required city (nonblank, at most 100 characters). Latitude must be within -90 to 90, longitude within -180 to 180, cost and votes must be nonnegative, price range must be 1 to 4, and rating average must be 0 to 5. Optional cuisine entries must be nonblank. Invalid or malformed input is rejected before persistence.

Errors use `application/problem+json` with a stable `code`, for example:

```json
{"detail":"Restaurant not found: missing","instance":"/v1/restaurants/missing","status":404,"title":"Not Found","code":"RESTAURANT_NOT_FOUND"}
```

Validation errors return `400` with `code: "VALIDATION_FAILED"` and an `errors` array of field messages. Malformed JSON or an invalid JSON value type returns `400` with `code: "INVALID_REQUEST"`. Unexpected server errors retain server-error status.

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

The HTTP integration test requires `BITEEXPRESS_API_TEST_MONGODB_URI` and `SPRING_MONGODB_URI` to point to the same isolated database with a name starting `biteexpress_api_test_`. It creates one uniquely named restaurant and leaves the document in that database. For example, set both variables to `mongodb://localhost:27018/biteexpress_api_test_local` before running `./mvnw test`.

## Stop services

`docker compose down` removes containers and the network while preserving the MongoDB volume. Only if you intentionally want to erase all local MongoDB data, use `docker compose down --volumes`.
