# 0003: Order the saga steps so that payment is the pivot

## Status

Accepted

## Context

Every saga step that can be followed by a failure needs a compensation, and compensations are not free: a cancelled
seat may already have been offered to someone else, and a refund costs fees, takes days and makes customers nervous.
The order of the steps decides which compensations can ever run.

## Decision

Run the steps in this order:

1. Hold a seat (compensation: cancel the seat hold).
2. Hold a room (compensation: cancel the room hold).
3. Charge the card. This is the **pivot**: once the money is captured, the saga only moves forward.
4. Confirm the seat and confirm the room. These steps are retried until they succeed; they are never compensated.

The cheap, reversible holds come first and the expensive, hard to reverse step comes last. A failure before the pivot
undoes what was done, newest first. A participant's business rejection (no seat, no room, card declined) is a final
answer and is not retried. It starts the compensation at once.

## Consequences

- A declined card never leads to a refund, because nothing was captured.
- Seats and rooms are blocked for a short time while the saga runs, even for bookings that end up cancelled.
- The confirm steps must not fail for business reasons. A hold that the saga created is only cancelled by the saga
  itself, so after the pivot nothing can cancel it.
- A booking that fails ends as `CANCELLED` with the failure reason and every step, including the compensations, in its
  history.
