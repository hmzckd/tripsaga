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

1. Start PostgreSQL and the Temporal dev server:

   ```bash
   docker compose up -d
   ```

   PostgreSQL listens on port 5440 and holds one database and one user per service (`flight`, `hotel`, `payment`,
   `booking`), created by [`docker/postgres/init.sql`](docker/postgres/init.sql). Temporal listens on port 7233 and its
   Web UI is at http://localhost:8233.

2. Start the four services, each in its own terminal:

   ```bash
   ./mvnw -pl flight spring-boot:run
   ./mvnw -pl hotel spring-boot:run
   ./mvnw -pl payment spring-boot:run
   ./mvnw -pl booking spring-boot:run
   ```

3. Book a trip and follow it:

   ```bash
   curl -i -X POST localhost:8080/api/bookings -H 'Content-Type: application/json' \
     -d '{"customer":"Ayse Yilmaz","flightId":"TK2412","hotelId":"AYT-LARA","nights":3,"cardToken":"tok_ok"}'
   curl localhost:8080/api/bookings/<id>
   ```

   The booking answers `202 Accepted` at once; the saga runs in the background and the workflow `booking-<id>` shows
   every step in the Temporal UI.

## Checks

```bash
./mvnw test
```

Decisions are recorded in [`docs/adr`](docs/adr) and the terms in [`GLOSSARY.md`](GLOSSARY.md).
