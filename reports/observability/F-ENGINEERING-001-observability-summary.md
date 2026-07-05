# F-ENGINEERING-001 Observability Summary

## M0 Observability Stack

- **Metrics**: vmagent → VictoriaMetrics (port 8428)
- **Logs**: Vector → Loki (port 3100)
- **Dashboards**: Grafana (port 3000) with VictoriaMetrics + Loki datasources
- **Health endpoints**: /health/live, /health/ready, /health/info
- **Audit**: sys_operation_audit table (append-only)
