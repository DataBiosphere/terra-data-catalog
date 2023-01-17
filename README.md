# Terra Data Catalog

[![Build and Test](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/build-and-test.yml)
[![Publish and deploy](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/publish.yml/badge.svg)](https://github.com/DataBiosphere/terra-data-catalog/actions/workflows/publish.yml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=terra-data-catalog&metric=coverage)](https://sonarcloud.io/summary/new_code?id=terra-data-catalog)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=terra-data-catalog&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=terra-data-catalog)

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
psql -f scripts/postgres-init.sql
```

After the database is initialized, then run integration tests:

```sh
./scripts/render_configs.sh    # render service account credentials needed for tests
./gradlew bootRun &    # start up a local instance of the data catalog service
sleep 5                # wait until service comes up
./gradlew runTest --args="suites/local/FullIntegration.json build/reports"
```

## Handling database migrations

The catalog service uses [Liquibase](https://liquibase.org/) to track and manage changes to the
database schema. Liquibase runs each changeset (migration) listed in the
[changelog.xml](common/src/main/resources/db/changelog.xml) file and
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

## Running SourceClear locally

[SourceClear](https://srcclr.github.io) is a static analysis tool that scans a project's Java
dependencies for known vulnerabilities. If you get a build failure due a SourceClear error and want
to debug the problem locally, you need to get the API token from vault before running the gradle
task.

```shell
export SRCCLR_API_TOKEN=$(vault read -field=api_token secret/secops/ci/srcclr/gradle-agent)
./gradlew srcclr
```

## Running SonarQube locally

[SonarQube](https://www.sonarqube.org) is a static analysis code that scans code for a wide
range of issues, including maintainability and possible bugs. If you get a build failure due to
SonarQube and want to debug the problem locally, you need to get the the sonar token from vault
before runing the gradle task.

```shell
export SONAR_TOKEN=$(vault read -field=sonar_token secret/secops/ci/sonarcloud/catalog)
./gradlew sonarqube
```

Unlike SourceClear, running this task produces no output unless your project has errors. To always
generate a report, run using `--info`:

```shell
./gradlew sonarqube --info
```

## Dataset entry schema

Datasets in the catalog (also known as catalog entries) have a JSON schema that they must conform to.
The current schema for dataset entries is [schema.json](common/src/main/resources/schema/development/schema.json),
and an example entry that conforms to this schema is [example.json](common/src/main/resources/schema/development/example.json).
This schema is based on the [TerraDCAT_AP model](https://github.com/DataBiosphere/terra-interoperability-model/blob/master/releases/1.x/terra-core/TerraDCAT-AP.ttl)
from the [Terra Interoperability Model](https://github.com/DataBiosphere/terra-interoperability-model).

The example entry was generated from the JSON schema using the online tool [JSON Schema Faker](https://json-schema-faker.js.org).
