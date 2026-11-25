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
| Hold | A temporary reservation: the seat or room is taken out of stock but not sold yet. A hold is later confirmed or cancelled. |
| Confirm | Turn a hold into a final reservation. A confirmed hold can no longer be cancelled by the saga. |
| Database per service | Each service has its own PostgreSQL database and user and cannot read the others. This is why a booking needs a saga instead of one transaction. |
| Capture | Taking the money from the card. A captured charge is the point where the customer has paid. |
| Refund | Giving captured money back. The compensation of a capture. |
| Card token | A stand-in for card details. `tok_declined` makes the fake payment service decline; any other token is captured. |
| Idempotency-Key | A header that names one logical request. Sending the same key again returns the first answer instead of doing the work twice. |
| Idempotent | Doing it twice has the same effect as doing it once. |
| Replay | The answer to a repeated key: the stored result of the first request, marked with `Idempotent-Replayed: true`. |
| Workflow | Temporal's name for durable code. `BookingWorkflowImpl` is the saga; Temporal records every step it takes so it can resume after a crash. |
| Activity | One call to the outside world from a workflow, here one HTTP call to a participant service. Temporal retries activities on failure. |
| Worker | The process that runs workflow and activity code. The `booking` service starts one at boot. |
| Task queue | The named queue (`booking`) a worker polls for work. Starting a workflow puts work on it. |
| Event history | Temporal's log of everything that happened in one workflow run. Replaying it rebuilds the workflow's state. |
| Query | A read-only question to a running or finished workflow, answered from its state. `GET /api/bookings/{id}` uses it. |
