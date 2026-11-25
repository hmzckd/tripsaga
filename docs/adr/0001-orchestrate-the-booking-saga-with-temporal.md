# 0001: Orchestrate the booking saga with Temporal

## Status

Accepted

## Context

A booking touches three services, and each service owns its own database: `flight`, `hotel` and `payment`. There is
no transaction that spans the three databases, so a booking is a saga: a chain of local steps, where a failure later
in the chain is repaired by compensating the steps that already happened.

A saga can be coordinated in two ways:

- **Choreography**: every service listens to the events of the others and decides on its own what to do next.
- **Orchestration**: one component calls the services in order and decides when to compensate.

A saga also has to survive crashes. If the coordinator dies after the seat was held but before the room was held,
someone has to remember that and carry on, or undo the seat.

## Decision

Use orchestration. The `booking` service runs the saga as a **Temporal workflow**. Each call to a participant service
is a Temporal **activity**. Temporal stores every step of the workflow, so after a crash the workflow resumes where it
stopped instead of starting over.

Temporal runs locally with the official dev server image; nothing runs in the cloud.

## Consequences

- The whole booking flow, including the compensations, reads top to bottom in one class.
- Crash recovery, retries and timers come from Temporal; we do not write a state table, a relay or a scheduler.
- The Temporal Web UI shows every booking step by step, which makes demos and debugging easy.
- One more piece of infrastructure to run, and workflow code has rules of its own: it must be deterministic.
- Participant services still have to handle repeated requests safely, because Temporal retries activities.
