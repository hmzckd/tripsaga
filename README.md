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

## Checks

```bash
./mvnw test
```

Decisions are recorded in [`docs/adr`](docs/adr) and the terms in [`GLOSSARY.md`](GLOSSARY.md).
