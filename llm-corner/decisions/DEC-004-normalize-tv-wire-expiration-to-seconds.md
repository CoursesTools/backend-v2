# DEC-004 — Normalize TradingView wire expiration to whole seconds

Status: ACCEPTED
Date: 2026-08-21
Author: Codex (operator-approved incident hardening)

## Context

The TradingView access service accepted a controlled `/open` request after its
`expiration` value was reduced to whole seconds, while a startup burst of ten
trial-recovery commands had produced HTTP 2xx acknowledgements without visible
access. The service currently acknowledges that a command was written to a
shared `open.txt`; this does not prove that TradingView applied it and is not a
safe concurrent-command contract.

Backend DTOs previously used the application-wide `ISO_LOCAL_DATE_TIME`
serializer directly. That produces a value-dependent shape: fractional
seconds when present and even no seconds when the value ends in `:00`. A strict
field `@JsonFormat` was not viable because it also rejects legacy fractional
retry payloads during deserialization.

## Options considered

1. Truncate subscription timestamps in the database — rejected because wire
   compatibility must not alter entitlement arithmetic or stored precision.
2. Change the application-wide Jackson serializer — rejected because it would
   silently change unrelated API contracts.
3. Add strict DTO formatting annotations — rejected because old durable retry
   payloads must remain readable.
4. **CHOSEN: canonicalize the two TV DTO expiration setters to whole seconds.**

## Decision

`ActivateTradingViewAccessDto` and `ChangeTradingViewNameDto` truncate their
`expiration` to seconds at the DTO boundary. Payment buffering is applied
before normalization. Database values and all subscription calculations are
unchanged.

Jackson also invokes those setters when it reads persisted retry JSON. Legacy
fractional payloads therefore remain deserializable and are normalized before
replay; no data migration is needed. The naive, offset-less date-time contract
is otherwise unchanged because adding an offset requires a coordinated bot
deployment.

The backend continues to treat HTTP 2xx as transport acceptance only. It must
not enforce a success response schema until the bot owner deploys and confirms
the proposed response contract. The shared-file concurrency defect remains an
external High-severity open item.

## Consequences

All new activation and rename commands, including durable retry snapshots,
carry `yyyy-MM-dd'T'HH:mm:ss`. Values ending in zero seconds still include the
seconds component because the stored DTO value has whole-second precision and
the existing serializer emits it in current regression tests. Old retry rows
remain operable. This hardening removes fractional-wire ambiguity but does not
solve timezone ambiguity, remote queuing, or proof that TradingView applied a
command.
