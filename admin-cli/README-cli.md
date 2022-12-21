# admin-cli

This is a command line tool for catalog admins to perform maintenance operations on the
catalog database.

## Building and Running

1. Build the admin-cli jar.
   ```shell
   ./gradlew :admin-cli:bootJar
   ```

2. Start the database proxy. `ENV` is the database environment to use. You
must use port `5432`, which may conflict with a locally running postgres. If the port is
in use, `db_connect.sh` will report an error and exit.

   ```shell
   ENV=dev PORT=5432 ./scripts/db_connect.sh
   ```

   In the proxy script output, look for the database name, username and password. The
proxy must be left running while the admin-cli is used, and you must manually stop it
when you're done.


3. Run `admin-cli` with a command.

   ```shell
   DATABASE_NAME=catalog DATABASE_USER=catalog DATABASE_USER_PASSWORD=<password> \
     java -jar admin-cli/build/libs/admin-cli-<version>.jar validate
   ```

   You can also run a command without building a jar using `bootRun` and `--args`:

   ```shell
   DATABASE_NAME=catalog DATABASE_USER=catalog DATABASE_USER_PASSWORD=<password> \
     ./gradlew :admin-cli:bootRun --args=validate
   ```

## Commands supported

- `validate`
- `list`
- `get` - supply one or more dataset `id`s as arguments: `get --id=<uuid1> --id=<uuid2>`

## Notes

* If a command runs without errors, its output will valid JSON.

* The catalog schema doesn't load cleanly so a log warning is generated when using
`validate`. You can skip this warning (which is logged as JSON) by using `jq`:

   ```shell
   [...] java -jar admin-cli.jar validate | jq  'del(.[1])' > out.json
   ```

* `admin-cli` can run any pending liquibase migrations if necessary. If you
need to run migrations before running a command, use the `UPGRADE_DB` environment variable.

   ```shell
   UPGRADE_DB=true java -jar admin-cli.jar list
   ```
