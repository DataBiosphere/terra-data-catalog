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

Then, to build the code, run:

```sh
./gradlew build
```

## Running the tests

For tests, ensure you have a local Postgres instance running. While in the
`terra-data-catalog` directory, initialize the database as follows:

```sh
psql -f common/postgres-init.sql
```

After the database is initialized, then you may run integration tests as follows:

```sh
./gradlew bootRun & # start up a local instance of the data catalog service
render-configs.sh # render service account credentials needed for tests
./gradlew :integration:runTest --args="suites/FullIntegration.json /tmp/test"
```

Performance tests may be run as follows:

```sh
render-configs.sh perf
./gradlew :integration:runTest --args="suites/FullPerf.json /tmp/test"
```
