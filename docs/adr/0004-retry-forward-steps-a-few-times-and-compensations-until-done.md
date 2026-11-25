# 0004: Retry forward steps a few times, compensations until they are done

## Status

Accepted

## Context

A participant can fail for two very different reasons:

- **Business rejection** (`4xx`): no seat, no room, card declined. The answer will not change if we ask again.
- **Technical failure** (`5xx`, timeout, connection refused): the service is slow, restarting or down. Asking again
  later will probably work.

Temporal retries a failed activity forever by default. That is right for some steps and wrong for others: a booking
should not wait an hour for a hotel that is down, but a seat that must be given back must be given back eventually.

## Decision

- A `4xx` answer becomes a **non-retryable** failure. It is never retried.
- A `5xx` answer or a timeout becomes a **retryable** failure.
- Forward steps (hold seat, hold room, charge) are retried at most **4 times** with exponential backoff (1 s, 2 s,
  4 s). After that the saga gives up and compensates.
- Compensations (cancel seat, cancel room) and the steps after the pivot (confirm seat, confirm room) are retried
  **until they succeed**, waiting up to one minute between attempts.
- Each HTTP call waits at most 5 seconds for an answer, and each activity attempt at most 10 seconds.
- If a compensation or a confirm is *rejected*, retrying cannot help. The booking ends as `NEEDS_ATTENTION` with the
  failed step in its history, and nothing else is undone.

## Consequences

- A short outage of a participant does not cancel bookings.
- A long outage cancels new bookings within about ten seconds instead of leaving customers waiting.
- A participant that is down for hours keeps compensations waiting for hours; they finish as soon as it is back.
- `NEEDS_ATTENTION` is the one state that needs a person. The step history says what went wrong.
