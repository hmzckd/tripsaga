# 0002: Make every participant call idempotent with an Idempotency-Key

## Status

Accepted

## Context

The orchestrator calls the participant services over HTTP, and Temporal retries a call that fails or times out. A
timeout does not tell the caller whether the request was processed: the seat may already be held and only the answer
was lost. Without protection, every retry could hold another seat or take the money again.

## Decision

Every request that creates something (`POST /holds`, `POST /charges`) carries an `Idempotency-Key` header. The key is
stored in a `UNIQUE` column next to the row it created.

- The first request with a key does the work and answers `201 Created`.
- A repeat with the same key and the same body does nothing and answers `200 OK` with the first result and an
  `Idempotent-Replayed: true` header. A declined payment is declined again with the same charge id.
- A repeat with the same key and a different body answers `422`, because the caller has a bug.
- Two repeats that arrive at the same time are serialized by the unique index: the insert runs first with
  `ON CONFLICT DO NOTHING`, so only one of them takes stock.

Business rejections that do not create a row (no free seat, no free room) are not stored. A repeat checks the stock
again.

Confirm, cancel and refund work on an existing row and only move it from one state to the next with a conditional
`UPDATE ... WHERE status = ...`, so repeating them is already safe.

## Consequences

- The orchestrator can retry any call as often as it needs to.
- The key has to be stable across retries. The saga builds it from the booking id and the step name.
- Each service keeps one more column and one more index per table.
