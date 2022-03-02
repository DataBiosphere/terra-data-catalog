# Terra Data Catalog

[![Build and Test](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/build-and-test.yml)

The mission of the Terra Data Catalog is to make research data accessible and
searchable to accelerate biomedical discoveries.

## Building the code

> If you are a new member of the Broad, follow the [getting started guide](docs/getting-started.md)
first.

Ensure you have Java 17 and that it is the default. To check this while in the
`terra-data-catalog` directory, type `./gradlew --version`.

Then, to build the code, run:

```
./gradlew build
```

## Running the tests

For tests, ensure you have a local Postgres instance running. While in the
`terra-data-catalog` directory, initialize the database as follows:

```
psql -f common/postgres-init.sql
```

After the database is initialized, then you may run integration tests as follows:

```
render-configs.sh
./gradlew :integration:runTest --scan --args="suites/FullIntegration.json /tmp/test"
```
