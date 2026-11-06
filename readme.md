# signuplogin

Signup and login service. Postgres must already be running, with a database named `signuplogin` on `localhost:5432`.

Use two terminals. Leave the first one running while you use the second.

## Terminal 1: start the app

From the project directory:

```bash
export DB_PASSWORD=your-postgres-password
./mvnw spring-boot:run
```

Replace `your-postgres-password` with the password for the Postgres user `postgres`. The username defaults to `postgres`. To use another user:

```bash
export DB_USERNAME=your-user
export DB_PASSWORD=your-postgres-password
```

Wait until the log says `Tomcat started on port 8080`. Hibernate creates or updates the `users` table on startup (`spring.jpa.hibernate.ddl-auto=update`). Leave this terminal open. Stop the app later with `Ctrl+C`.

## Terminal 2: inspect the users table

Open a second terminal after the app has started, then connect to the same database:

```bash
psql -h localhost -p 5432 -U postgres -d signuplogin
```

Enter the same password. At the `signuplogin=#` prompt:

```text
\d users
```

That lists the columns: `id`, `email`, `password_hash`, `role`, and `created_at`.

Leave `psql` with:

```text
\q
```
