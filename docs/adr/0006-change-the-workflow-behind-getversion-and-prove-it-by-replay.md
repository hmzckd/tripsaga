# 0006: Change the workflow behind `Workflow.getVersion` and prove it by replay

## Status

Accepted

## Context

A booking can wait up to 15 minutes for its payment, so new code is deployed while old bookings are still running.
After a restart, Temporal rebuilds every running workflow by **replaying** its event history through the *current*
workflow code. The code must make the same calls in the same order as the code that wrote the history. If it does
not, Temporal stops the workflow with a non-determinism error.

The first change of this kind: the booking row should be written when the saga starts, not only when it ends, so the
`bookings` table also shows running bookings. That adds an activity call at the very beginning of the workflow, which
histories written by the old code do not contain.

## Decision

- Every change that adds, removes or reorders workflow steps goes behind `Workflow.getVersion(changeId, ...)`. New
  runs take the new path; runs that started before the change keep the old path until they finish.
- Histories written by the old code are kept in `booking/src/test/resources/histories/` and replayed in `ReplayTest`
  against the current code on every build.
- The first change id is `record-on-start`.

## Consequences

- Deploying while bookings are running is safe, and the build fails before an unsafe change reaches production.
- Old branches stay in the code until no workflow that needs them can be running any more; then they can be removed
  together with their history file.
- Workflow code has to stay deterministic: no `new Date()`, `Random`, threads or direct I/O, only Temporal's
  `Workflow.*` APIs and activities.
