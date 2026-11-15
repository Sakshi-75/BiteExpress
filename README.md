# BiteExpress

BiteExpress is a Spring Boot application for a restaurant platform. **Phase 1** provides the application skeleton, an HTTP server, an Actuator health endpoint, and a MongoDB connection. Docker Compose starts the app and MongoDB together and waits for MongoDB to become healthy. Restaurant features and APIs will arrive in later phases.

## Technology stack

- Java 21 and Spring Boot 4.1.2
- Maven
- Spring Web, Spring Data MongoDB, and Spring Boot Actuator
- MongoDB 8.0 and Docker Compose

## Prerequisites

- For the Docker workflow: Docker Desktop or another Docker installation with Compose.
- For the IDE workflow: JDK 21, a Java IDE with Maven support, and Docker for the local MongoDB container.
- To run tests outside Docker: JDK 21 and Maven 3.9 or later.

## Run with Docker Compose

From the project directory, run:

```sh
docker compose up --build -d
```

The app is available on port 8080. MongoDB is available to other Compose services at `mongo:27017` and to tools on your computer at `localhost:27018`. MongoDB data is stored in the named `mongo_data` volume. The Docker build also runs the Maven tests.

Check the app at [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). It should report `UP` when the app and MongoDB are available. View logs with `docker compose logs -f app`.

## Run from an IDE

1. Start only MongoDB: `docker compose up -d mongo`.
2. If the Compose app is already running, stop it with `docker compose stop app` to free port 8080.
3. Open `pom.xml` as a Maven project and select JDK 21 for the project and run configuration.
4. Set the run configuration environment variable `SPRING_MONGODB_URI=mongodb://localhost:27018/biteexpress`.
5. Run `com.biteExpress.BiteExpressApplication` and check [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

## Run tests

With JDK 21 and Maven installed:

```sh
mvn test
```

For a clean build that also runs the tests:

```sh
mvn clean verify
```

## Stop services and manage local data

Stop the IDE run configuration if you started the app there. Then remove the Compose containers and network while **preserving** MongoDB data:

```sh
docker compose down
```

To also **delete** the local MongoDB data volume:

```sh
docker compose down --volumes
```
