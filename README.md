# BiteExpress

BiteExpress is a Java 21 / Spring Boot 4.1.2 restaurant discovery API backed by MongoDB 8. It supports restaurant creation, CSV import, structured filters, distance search, and a small rule-based natural-language search. No LLM or vector service runs in the app.

## Quick start

Requires Docker Desktop with Compose. For an IDE run, use JDK 21 and Maven.

~~~sh
docker compose up --build -d
docker compose ps
~~~

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- MongoDB Compass: mongodb://localhost:27018/biteexpress

For an IDE run, start MongoDB with docker compose up -d mongo, stop the Compose app if port 8080 is occupied, set SPRING_MONGODB_URI=mongodb://localhost:27018/biteexpress, and run com.biteExpress.BiteExpressApplication. Ordinary startup does not import data.

## Import data

Import is disabled by default. The included [sample CSV](data/sample-restaurants.csv) has three illustrative rows. The country-code CSV is exported from the supplied Excel mapping; its code and resolved name are embedded in each restaurant. To import the sample:

~~~sh
docker compose run --rm -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BITEEXPRESS_IMPORT_ENABLED=true app
~~~

For the full dataset, download the Zomato restaurant CSV from [Kaggle](https://www.kaggle.com/datasets/shrutimehta/zomato-restaurants-data), place it at data/zomato.csv, then run:

~~~sh
docker compose run --rm -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BITEEXPRESS_IMPORT_ENABLED=true -e BITEEXPRESS_IMPORT_FILE=file:./data/zomato.csv app
~~~

The full CSV is Git ignored because its redistribution rights have not been verified. BITEEXPRESS_IMPORT_COUNTRY_FILE overrides the country-code file. For an IDE import, also set SPRING_MAIN_WEB_APPLICATION_TYPE=none and the local MongoDB URI above.

The importer streams rows in batches of 250. Stable UUIDs derived from source IDs make repeat imports idempotent; the last valid duplicate row wins. Missing names/IDs, unknown country codes, or malformed required values reject a row. Missing/unrated ratings stay null; invalid or (0, 0) coordinates become null without rejecting an otherwise valid row. Strings and cuisines are trimmed. Source currencies are preserved, including known country/currency anomalies.

## API

| Endpoint | Behavior |
| --- | --- |
| POST /v1/restaurants | Create a restaurant; returns 201 and its full document, without a Location header. |
| GET /v1/restaurants/{id} | Retrieve a restaurant; returns 200 or 404. |
| GET /v1/restaurants | Structured listing with filters and pagination. |
| GET /v1/restaurants/search?q=... | Parse supported phrases, then run the same MongoDB search. |

Both listing endpoints accept name (partial), city, cuisine, minRating, maxCostForTwo with currency, latitude and longitude, radiusKm, page, size, sortBy, and sortDir. Filters combine with AND. Page is zero-based; size is 1–100. Sort fields are name, city, rating, cost, and distance. With coordinates, the default is nearest first; otherwise it is name ascending. Equal sort values use ID as a tiebreaker. Invalid requests return HTTP 400 with an INVALID_REQUEST problem code.

The natural-language parser recognizes phrases such as “best North Indian restaurants in Gurugram under Rs 800 for two” and “nearest Chinese restaurants within 5 km”. It supports city, known cuisine labels, best/top-rated, nearest, and rupee maximum-cost phrases. Gurgaon/Gurugram and Rs/INR/Indian Rupees(Rs.) are explicit search synonyms. Supply latitude and longitude for nearest or radius searches. Explicit query parameters override inferred values. Ambiguous, unsupported, and dish/menu-item requests return 400; use the structured cuisine parameter for other cuisine labels.

~~~sh
curl 'http://localhost:8080/v1/restaurants?city=Gurgaon&cuisine=Indian&minRating=4'
curl 'http://localhost:8080/v1/restaurants?latitude=28.4595&longitude=77.0266&radiusKm=5&sortBy=distance'
curl 'http://localhost:8080/v1/restaurants/search?q=best%20North%20Indian%20restaurants%20in%20Gurugram%20under%20Rs%20800'
curl 'http://localhost:8080/v1/restaurants/search?q=nearest%20Chinese%20restaurants%20within%205%20km&latitude=28.4595&longitude=77.0266'
~~~

The listing response contains content, page, size, totalElements, and totalPages. Each item includes ID, name, address, city, country, cuisines, average rating, location, and distanceKm (null without a search center). Swagger UI shows the POST body and all query parameters.

## Design and verification

One restaurants collection stores embedded address, country, pricing, rating, and features. API-created IDs are random UUID strings; imported IDs are deterministic UUID strings. Valid locations are GeoJSON Points in [longitude, latitude] order. The app creates the 2dsphere location index at startup and rechecks it on nearby requests; refresh the restaurants collection's Indexes tab in Compass to see it. Nearby searches use MongoDB $geoNear; filtering, counts, sorting, and pagination stay in MongoDB. There is no separate country collection or dish-level data.

~~~sh
./mvnw test
./mvnw clean verify
docker compose exec mongo mongosh biteexpress --quiet --eval "print(db.restaurants.countDocuments({})); printjson(db.restaurants.findOne())"
~~~

On Windows, use .\mvnw.cmd. Start Docker Desktop for verify. Integration tests use an isolated Testcontainers MongoDB and verify its endpoint before clearing test fixtures; ordinary test contexts use an unreachable MongoDB URI. The combined JaCoCo report is at target/site/jacoco/index.html. For a manual repeat-import check that cleans up its own test database, run .\scripts\verify-sample-import.ps1 after building the app image. It refuses an existing test database and reports cleanup failures.

docker compose down stops services and preserves the MongoDB volume. Only use docker compose down --volumes when you intentionally want to remove all Compose MongoDB data.

## AI-assisted development

Codex assisted with implementation, test drafting, review, and documentation. The repository's tests and MongoDB-backed checks are used to verify behavior; the API does not use AI at runtime.
