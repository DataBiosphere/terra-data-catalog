# Terra Data Catalog

[![Build and Test](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/build-and-test.yml)
[![Nightly Tests](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/nightly-tests.yml/badge.svg)](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/nightly-tests.yml)
[![Publish and deploy](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/publish.yml/badge.svg)](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/publish.yml)

The mission of the Terra Data Catalog is to make research data accessible and
searchable to accelerate biomedical discoveries.

## Building the code

> If you are a new member of the Broad, follow the [getting started guide](docs/getting-started.md)
first.

Ensure you have Java 17 and that it is the default. To check this while in the
`terra-data-catalog` directory, type `java --version`.

Then, to build the code without executing tests, run:

```sh
./gradlew build -x test
```

If you don't include `-x test` ensure the Postgres database is initialized as
described below.

## Running the tests

For tests, ensure you have a local Postgres instance running. While in the
`terra-data-catalog` directory, initialize the database:

```sh
psql -f common/postgres-init.sql
```

After the database is initialized, then run integration tests:

```sh
./gradlew bootRun &    # start up a local instance of the data catalog service
sleep 5                # wait until service comes up
render-configs.sh      # render service account credentials needed for tests
./gradlew :integration:runTest --args="suites/FullIntegration.json /tmp/test"
```

To run performance tests, execute:

```sh
render-configs.sh perf
./gradlew :integration:runTest --args="suites/FullPerf.json /tmp/test"
```

## Handling database migrations

The catalog service uses [Liquibase](https://liquibase.org/) to track and manage changes to the
database schema. Liquibase runs each changeset (migration) listed in the
[changelog.xml](service/src/resources/db/changelog.xml) file and
maintains a record of what has been run, so new changes must be added in a new changeset.

To run migrations locally use:

```
./gradlew update
```

If the local database gets into a bad state (for instance while testing/modifying a new changeset),
drop its contents with:

```
./gradlew dropAll
```
