# TripSaga

A travel booking backend: one booking reserves a flight seat, a hotel room and takes a payment, across three services
that each own their own database. The steps are kept consistent with an orchestrated **saga** on **Temporal**: when a
step fails, the steps before it are compensated.

Work in progress.

## Repository layout

| Module | Port | Role |
| --- | --- | --- |
| `booking` | 8080 | Public API and the saga orchestrator |
| `flight` | 8081 | Flight seats |
| `hotel` | 8082 | Hotel rooms |
| `payment` | 8083 | Payments and refunds |

## Run locally

```bash
docker compose up -d
./mvnw -pl flight spring-boot:run
```

PostgreSQL listens on port 5440 and holds one database and one user per service (`flight`, `hotel`, `payment`,
`booking`), created by [`docker/postgres/init.sql`](docker/postgres/init.sql).

## Checks

```bash
./mvnw test
```

Decisions are recorded in [`docs/adr`](docs/adr) and the terms in [`GLOSSARY.md`](GLOSSARY.md).
