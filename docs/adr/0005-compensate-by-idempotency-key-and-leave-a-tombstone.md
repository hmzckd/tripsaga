# 0005: Compensate by idempotency key and leave a tombstone

## Status

Accepted

## Context

When a call times out or its answer is lost, the orchestrator does not know what happened. The seat may be held, or the
request may never have arrived, or it may still be on its way and arrive after the saga has given up. Until now a
compensation was registered only after a successful answer and cancelled the hold by its id. A hold whose answer was
lost had no id in the saga, so nothing cancelled it and the seat stayed blocked forever.

## Decision

- The saga registers each compensation **before** it calls the step, not after the step succeeded.
- Compensations name what they undo by the **idempotency key** of the original request, which the saga always knows:
  `POST /holds/by-key/{key}/cancel` and `POST /charges/by-key/{key}/refund`.
- If a row with that key exists, it is cancelled or refunded as before.
- If no row exists yet, the service stores a **tombstone**: a `CANCELLED` row with only the key. A hold or charge that
  arrives later with the same key hits the tombstone and is answered `409 Conflict` without taking stock or money.
- A hold or charge and its cancel are serialized by the same `UNIQUE` index on the key, so they cannot both win.

## Consequences

- A lost answer can no longer leak a seat, a room or money.
- A compensation for a step that was rejected or never arrived is harmless: it leaves a tombstone.
- The step history shows a compensation for every step the saga *tried*, not only for the steps that succeeded.
- Tombstone rows have empty business columns, so the tables allow nulls for `CANCELLED` rows only (enforced by a
  `CHECK` constraint).
