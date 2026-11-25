# Glossary

Terms used in the code, tests and docs of TripSaga.

| Term | Meaning |
| --- | --- |
| Booking | One trip a customer asks for: a flight seat, a hotel room for some nights and one payment. Owned by the `booking` service. |
| Participant service | A service that owns one part of the booking and its own database: `flight`, `hotel`, `payment`. |
| Saga | A business transaction split into local steps, one per service. Each step commits on its own. If a later step fails, the earlier ones are undone by compensations. |
| Compensation | The step that semantically undoes an earlier step: cancel the seat, cancel the room, refund the payment. It does not delete history, it adds a new fact. |
| Orchestration | One component (the orchestrator) tells each participant what to do next and decides when to compensate. |
| Choreography | No central component: each service reacts to the events of the others. Not used here. |
| Orchestrator | The `booking` service's saga, written as a Temporal workflow. |
