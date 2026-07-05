<!--
Function: F-PLATFORM-003 observability summary — dictionary and i18n observability
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-003 Observability Summary — Dictionary & i18n

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs (port 3100)

## Audit
- Dictionary type publish and item changes recorded (platform.dictionary.changed.v1)
- i18n resource create/update/delete recorded (platform.i18n.changed.v1)

## Metrics
- Dict lookup cache hit/miss
- i18n resource query latency

## Tracing
- trace_id propagated through dict/i18n controller -> service -> repository

## Logging
- Structured logs with trace_id
- Dictionary version transitions logged (DRAFT -> PUBLISHED)
