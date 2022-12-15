# admin-cli

This is a command line tool for catalog admins to examine the contents of the catalog
database.

To use it:

1. Build:
```shell
./gradlew :admin-cli:bootJar
```

2. Start the database proxy. `ENV` is the database environment to use. Note that you currently
must use port `5432`, which may conflict with a locally running postgres. If the port is
in use, `db_connect.sh` will report an error and exit.

```shell
ENV=dev PORT=5432 ./scripts/db_connect.sh
```

In the proxy output, look for the database name, username and password.

3. Run the command:

```shell
DATABASE_NAME=catalog DATABASE_USER=catalog DATABASE_USER_PASSWORD=<password> \
  java -jar admin-cli/build/libs/admin-cli-<version>.jar validate
```

You can also run the command without building a jar using `bootRun` and `--args`:

```shell
DATABASE_NAME=catalog DATABASE_USER=catalog DATABASE_USER_PASSWORD=<password> \
  ./gradlew :admin-cli:bootRun --args=validate
```

## Commands supported

- `validate`
- `list`
- `get` - supply one or more dataset `id`s as arguments: `get --id=<uuid1> --id=<uuid2>`

## Notes

If a command runs without errors, its output will valid JSON.

Currently, the catalog schema itself doesn't load cleanly so a log warning is generated when using
`validate`. You can skip this warning by using `jq`:

```shell
[...] java -jar admin-cli.jar validate | jq  'del(.[1])' > out.json
```
