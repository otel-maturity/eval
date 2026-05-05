# OpenTelemetry Support Maturity Evaluation: Tekton Pipelines

## Project overview

- **Project**: Tekton Pipelines — a CNCF-graduated Kubernetes-native CI/CD framework providing CRDs (Task, TaskRun, Pipeline, PipelineRun) and controllers that execute CI/CD workloads as Kubernetes Pods
- **Version evaluated**: v1.12.0 (released 2026-05-04)
- **Evaluation date**: 2026-05-05
- **Cluster**: otel-eval-tekton (kind)
- **Maturity model version**: OpenTelemetry Support Maturity Model for CNCF Projects (draft)

---

## Summary

| Dimension | Level | Summary |
|-----------|-------|---------|
| Integration Surface | 2 | OTLP traces (HTTP/gRPC) natively; metrics via Prometheus scrape with OTLP export option; no OTLP logs |
| Semantic Conventions | 1 | Custom span/attribute names; Prometheus-style metric names; HTTP client/server metrics use current semconv attributes |
| Resource Attributes & Configuration | 1 | `service.name` set natively on traces; no `service.version`; inconsistent identity across signals; no `OTEL_*` env var support |
| Trace Modeling & Context Propagation | 2 | W3C Trace Context propagation; PipelineRun→TaskRun cross-reconciler context propagation via annotations; all spans INTERNAL kind |
| Multi-Signal Observability | 1 | Traces (OTLP) and metrics (Prometheus) flow; no OTLP logs; `service.name` differs between signals; no trace context in metrics |
| Audience & Signal Quality | 2 | Trace span names are logical CI/CD operations; child spans lack attributes; metrics are well-labeled with pipeline/task/status dimensions |
| Stability & Change Management | 2 | Breaking changes documented with migration guide; metrics documented with stability status; schema URL present on traces; no `OTEL_*` env var contract |

---

## Telemetry overview

### Signals observed
- **Traces**: Flowing — OTLP HTTP push from the controller process (`pipelinerun-reconciler`, `taskrun-reconciler`)
- **Metrics**: Flowing — Prometheus scrape from four services (`tekton-pipelines-controller`, `tekton-pipelines-webhook`, `tekton-events-controller`, `tekton-pipelines-remote-resolvers`); OTLP metric export also supported but not used in this evaluation
- **Logs**: Not flowing — Tekton uses structured JSON logging (zap) to stdout only; no OTLP log export is supported

### Resource attributes (native, before collector enrichment)

**Traces** (set by Tekton's OTel SDK initialization):
- `service.name`: `pipelinerun-reconciler` or `taskrun-reconciler`
- Schema URL: `https://opentelemetry.io/schemas/1.12.0` (set via `semconv.SchemaURL` from `semconv/v1.12.0`)
- No `service.version`, no `service.namespace`, no `telemetry.sdk.*`

**Metrics** (Prometheus labels promoted to resource attributes by the collector's Prometheus receiver):
- `service.name`: `tekton-pipelines-controller` (derived from Prometheus `service_name` label)
- `service_name`: `tekton-pipelines-controller` (raw Prometheus label)
- `service_version`: `7798558` (raw Prometheus label — a commit hash, not a semver)
- `telemetry_sdk_language`: `go`, `telemetry_sdk_name`: `opentelemetry`, `telemetry_sdk_version`: `1.43.0` (Prometheus labels from OTel SDK)
- `service.instance.id`: `tekton-pipelines-controller.tekton-pipelines.svc.cluster.local:9090`

### Resource attributes (after collector enrichment)

The k8sattributes processor adds the following to trace resource spans:
- `k8s.pod.name`, `k8s.pod.uid`, `k8s.pod.start_time`
- `k8s.namespace.name`, `k8s.node.name`
- `k8s.deployment.name`, `k8s.replicaset.name`, `k8s.container.name`
- `k8s.pod.label.*` (including `app`, `app.kubernetes.io/*`, `pipeline.tekton.dev/release`, `version`)

---

## Dimension evaluations

### 1. Integration Surface

**Level: 2 — OTLP Native**

#### Evidence

- **Traces**: Tekton emits traces natively via the OTel Go SDK (`go.opentelemetry.io/otel v1.43.0`) using OTLP HTTP (`otlptracehttp`). Configured via the `config-observability` ConfigMap (`tracing-protocol: http/protobuf`, `tracing-endpoint`). Also supports OTLP gRPC (`tracing-protocol: grpc`). Confirmed flowing: 1,262 spans observed across 9 distinct traces.
- **Metrics**: Tekton natively exposes Prometheus metrics on port `9090` from four services. The `config-observability` ConfigMap also supports `metrics-protocol: grpc` and `metrics-protocol: http/protobuf` for direct OTLP metric export. Default is `prometheus`. In this evaluation, the Prometheus path was used (scraped by the OTel Collector's Prometheus receiver).
- **Logs**: No OTLP log export. Tekton logs via zap to stdout only. `logs.jsonl` contains 0 lines.
- **Configuration mechanism**: All telemetry configuration is via Kubernetes ConfigMaps (`config-observability`, legacy `config-tracing`). No `OTEL_*` environment variable support — the tracing code (`pkg/tracing/tracing.go`) hardcodes the exporter construction from ConfigMap values and does not read any `OTEL_*` env vars.
- **Documentation**: `docs/metrics.md` documents both Prometheus and OTLP export options. A dedicated migration guide (`docs/metrics-migration-otel.md`) documents the OpenCensus→OpenTelemetry transition.

#### Checklist assessment

- ✅ Project emits at least one signal via OTLP natively (traces via OTLP HTTP)
- ✅ OTLP is the primary recommended path for traces
- ✅ Metrics can also be exported via OTLP (gRPC or HTTP) — documented and supported
- ✅ Integration is documented in official project docs
- ❌ No OTLP log export
- ❌ No `OTEL_*` environment variable support (configuration is ConfigMap-only)
- ❌ Metrics default to Prometheus, not OTLP

#### Rationale

Tekton reaches Level 2 because it natively emits traces via OTLP (not requiring a sidecar or collector agent for the trace signal), and also supports OTLP metric export as a documented option. The absence of OTLP logs and the Prometheus-first metric default prevent Level 3. The ConfigMap-only configuration model (no `OTEL_*` env vars) is a notable limitation but does not affect the integration surface level itself.

---

### 2. Semantic Conventions

**Level: 1 — Partial Alignment**

#### Evidence

##### Trace attributes

Span-level attributes observed (all spans):
- `pipelinerun`: `"demo-pipeline-run-1"` — custom attribute key (not semconv)
- `taskrun`: `"hello-world-1"` — custom attribute key (not semconv)
- `namespace`: `"default"` — custom attribute key (not semconv; OTel semconv would use `k8s.namespace.name`)

Only root-level `*:Reconciler` and `*:ReconcileKind` spans carry any attributes. All 1,137 child spans (`reconcile`, `prepare`, `createPod`, `finishReconcileUpdateEmitEvents`, etc.) have **zero span attributes**.

Span kinds: All 1,262 spans use `kind=1` (INTERNAL). No SERVER, CLIENT, PRODUCER, or CONSUMER spans are emitted by Tekton natively. This is technically correct for internal reconciler operations but means there are no entry-point SERVER spans.

Instrumentation scope names: `PipelineRunReconciler`, `TaskRunReconciler` — no version set (version is empty/unknown).

Schema URL on traces: `https://opentelemetry.io/schemas/1.12.0` — this is the schema URL from `go.opentelemetry.io/otel/semconv/v1.12.0`, which is **outdated**. The current stable semconv schema is v1.26.0 (as of early 2025). The source code imports `semconv "go.opentelemetry.io/otel/semconv/v1.12.0"` despite using OTel SDK v1.43.0.

No deprecated HTTP attributes (`http.method`, `http.status_code`, etc.) are present on Tekton's own spans — because Tekton doesn't use HTTP semconv attributes on its spans at all.

##### Metric names and attributes

Tekton-specific metrics use Prometheus naming convention (underscore-separated, `_seconds`/`_total`/`_milliseconds` suffixes):
- `tekton_pipelines_controller_pipelinerun_duration_seconds` (histogram)
- `tekton_pipelines_controller_taskrun_duration_seconds` (histogram)
- `tekton_pipelines_controller_pipelinerun_taskrun_duration_seconds` (histogram)
- `tekton_pipelines_controller_pipelinerun_total` (counter/sum)
- `tekton_pipelines_controller_taskrun_total` (counter/sum)
- `tekton_pipelines_controller_running_pipelineruns` (gauge)
- `tekton_pipelines_controller_running_taskruns` (gauge)
- `tekton_pipelines_controller_taskruns_pod_latency_milliseconds` (histogram — inconsistent unit suffix vs `_seconds` used elsewhere)

These are Prometheus-native names, not OTel naming convention (which uses dots: `tekton.pipelines.controller.pipelinerun.duration`). Since these metrics are emitted via a Prometheus endpoint, Prometheus naming is appropriate for that path. However, OTLP metric export would ideally use dot-separated names.

Metric attributes on Tekton-specific metrics are meaningful and well-chosen:
- `status=success` (consistent across all duration/count metrics)
- `namespace=default` (on duration metrics)
- `pipeline=demo-pipeline` (on pipelinerun metrics)
- `task=task-a/task-b/...` (on taskrun and pipelinerun_taskrun metrics)

**HTTP client/server metrics** (from `otelhttp` instrumentation on the webhook and controller):
- `http_client_request_duration_seconds` uses: `http_request_method`, `server_address`, `server_port`, `url_scheme`, `url_template` — these are **current semconv** attribute names (using underscores as Prometheus label format of the dot-separated OTel names `http.request.method`, `server.address`, etc.)
- `http_server_request_duration_seconds` uses: `http_request_method`, `http_response_status_code`, `http_route`, `network_protocol_name`, `network_protocol_version`, `server_address`, `url_scheme` — also **current semconv** attribute names

No deprecated attributes (`http.method`, `http.status_code`, `http.url`, `http.target`) observed.

##### Log attributes

No OTLP logs — not applicable.

#### Checklist assessment

- ✅ No deprecated HTTP semantic convention attributes observed
- ✅ HTTP client/server metrics use current semconv attribute names
- ✅ Metric attributes are semantically meaningful (status, namespace, pipeline, task)
- ❌ Custom span attribute keys (`pipelinerun`, `taskrun`, `namespace`) instead of semconv keys (`k8s.pipelinerun.name` or similar)
- ❌ All spans are INTERNAL kind regardless of operation type
- ❌ Instrumentation scope has no version set
- ❌ Schema URL references outdated semconv v1.12.0 (should be v1.26.0+)
- ❌ Child spans have no attributes at all
- ❌ Metric names use Prometheus underscore convention (acceptable for Prometheus path but not OTel-native)
- ❌ `tekton_pipelines_controller_taskruns_pod_latency_milliseconds` uses `_milliseconds` suffix while other duration metrics use `_seconds` (inconsistent units)

#### Rationale

Level 1 reflects that Tekton uses the OTel SDK correctly at a structural level and its HTTP instrumentation uses current semconv, but the project's own span attributes use custom keys rather than established semantic conventions, child spans are attribute-bare, and the semconv version referenced in the schema URL is significantly outdated.

---

### 3. Resource Attributes & Configuration

**Level: 1 — Basic Resource Identity**

#### Evidence

##### Native resource attributes

**Traces**: The controller sets only `service.name` natively:
- `pipelinerun-reconciler` (from the PipelineRun reconciler)
- `taskrun-reconciler` (from the TaskRun reconciler)

This is set via `semconv.ServiceNameKey.String(service)` in `pkg/tracing/tracing.go`. No `service.version`, `service.namespace`, `service.instance.id`, or `telemetry.sdk.*` attributes are set by Tekton itself on traces.

**Metrics**: The Prometheus endpoint exposes `service_name`, `service_version` (a commit hash: `7798558`), `telemetry_sdk_language`, `telemetry_sdk_name`, `telemetry_sdk_version` as Prometheus labels. These are promoted to resource attributes by the Prometheus receiver. The `service.name` value for metrics is `tekton-pipelines-controller`.

##### OTEL_* environment variable support

**Not supported.** Tekton's tracing configuration is exclusively managed through Kubernetes ConfigMaps (`config-observability`, `config-tracing`). The source code (`pkg/tracing/tracing.go`) constructs the OTLP exporter directly from ConfigMap values and does not read any `OTEL_*` environment variables. Operators cannot use `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`, or any other standard OTel environment variables to configure Tekton's telemetry.

##### Identity consistency across signals

**Inconsistent.** The same physical process (the `tekton-pipelines-controller` pod) emits:
- Traces with `service.name = pipelinerun-reconciler` and `service.name = taskrun-reconciler`
- Metrics with `service.name = tekton-pipelines-controller`

These are three different identity values for what is effectively one process. There is no shared `service.name` value that would allow an operator to correlate traces and metrics from the controller in a backend that uses `service.name` as the join key.

Additionally, `service.version` is absent from traces entirely. The metrics expose a commit hash (`7798558`) as `service_version`, not a semver string.

#### Checklist assessment

- ✅ `service.name` is set natively on traces
- ✅ `service.name` is present on metrics (via Prometheus label)
- ❌ `service.name` is inconsistent between traces and metrics for the same process
- ❌ `service.version` is absent from traces
- ❌ No `OTEL_*` environment variable support
- ❌ `telemetry.sdk.*` attributes absent from traces (present in metrics as Prometheus labels only)
- ❌ No `service.instance.id` on traces

#### Rationale

Level 1 because `service.name` is set natively on at least one signal (traces), but the inconsistency between signals, the absence of `service.version` on traces, and the complete lack of `OTEL_*` env var support prevent Level 2. The ConfigMap-only configuration model is a significant ergonomic limitation for operators who expect standard OTel configuration primitives.

---

### 4. Trace Modeling & Context Propagation

**Level: 2 — Coherent Trace Model**

#### Evidence

##### Span structure

A full PipelineRun execution produces a single coherent trace with the following structure (observed for `demo-pipeline-run-1`):

```
PipelineRun:Reconciler [ROOT, traceId=aed6aad...]
  └─ PipelineRun:ReconcileKind [×30 reconcile loops]
       ├─ reconcile
       │    ├─ resolvePipelineState [×2]
       │    ├─ runNextSchedulableTask
       │    │    └─ createTaskRuns
       │    │         └─ createTaskRun
       │    │              └─ TaskRun:ReconcileKind [×N, child of createTaskRun]
       │    │                   ├─ updateTaskRunWithDefaultWorkspaces
       │    │                   ├─ prepare
       │    │                   ├─ createPod
       │    │                   ├─ stopSidecars
       │    │                   ├─ durationAndCountMetrics
       │    │                   ├─ finishReconcileUpdateEmitEvents
       │    │                   └─ updateLabelsAndAnnotations
       │    └─ updatePipelineRunStatusFromInformer
       ├─ durationAndCountMetrics
       ├─ finishReconcileUpdateEmitEvents
       │    └─ updateLabelsAndAnnotations
       └─ updatePipelineRunStatusFromInformer
```

The PipelineRun trace (`aed6aadfe2ad075360ff3c095cf054b8`) contains **610 total spans** including 51 `TaskRun:ReconcileKind` spans as children of `createTaskRun`. This is the key cross-reconciler propagation: the PipelineRun reconciler injects its `traceparent` into the TaskRun's annotations (`tekton.dev/taskrunSpanContext`), and the TaskRun reconciler extracts it to continue the same trace.

Standalone TaskRuns (not part of a PipelineRun) create their own root traces:
- `993f6bb1764d9df7bf4e7ff2b73ea895` → `hello-world-1`
- `484e5a586e198d7625b9f712b4dd6305` → `hello-world-2`
- etc.

Total unique traces observed: 9 (1 PipelineRun + 8 standalone TaskRun traces).

##### Context propagation

W3C Trace Context (`traceparent`/`tracestate`) is used exclusively. Confirmed by:
1. `otel.SetTextMapPropagator(propagation.TraceContext{})` in `pkg/tracing/tracing.go`
2. The presence of parent span IDs on all non-root spans
3. Successful cross-reconciler propagation (TaskRun spans appear as children within the PipelineRun trace)

The propagation mechanism is unique: rather than HTTP header injection, Tekton serializes the W3C `traceparent` into the Kubernetes resource's annotations and status (`pr.Status.SpanContext`, `tr.Status.SpanContext`). This is necessary because the PipelineRun and TaskRun reconcilers are separate goroutines that communicate via Kubernetes API objects, not HTTP calls.

Span events are used to mark the propagation point: `"updating PipelineRun status with SpanContext"` and `"updating TaskRun status with SpanContext"` events appear on the root reconciler spans.

##### Trace coherence

The trace tells a complete and coherent story of a PipelineRun execution:
- Each reconcile loop is a distinct child of the root span
- Task creation, resolution, and pod scheduling are visible as child spans
- The relationship between PipelineRun reconciliation and TaskRun execution is captured in a single trace

**Limitation**: All spans use `kind=1` (INTERNAL). The root `PipelineRun:Reconciler` span could arguably use a different kind to indicate it represents the start of a logical operation. However, since the reconciler is an internal controller loop (not an inbound RPC), INTERNAL is defensible.

**Limitation**: Child spans (`reconcile`, `prepare`, `createPod`, etc.) carry no attributes. While the parent span carries `pipelinerun`/`taskrun` + `namespace`, operators cannot filter by these dimensions on child spans.

#### Checklist assessment

- ✅ W3C Trace Context propagation
- ✅ Root spans have meaningful attributes (resource name + namespace)
- ✅ PipelineRun→TaskRun cross-reconciler context propagation via Kubernetes annotations
- ✅ Trace structure reflects the actual execution model (reconcile loops, task creation)
- ✅ Standalone TaskRun and PipelineRun traces are correctly modeled
- ❌ All spans use INTERNAL kind (no SERVER/CLIENT spans for the controller's entry points)
- ❌ Child spans have no attributes — cannot filter or aggregate on pipeline/task dimensions at the span level
- ❌ Instrumentation scope has no version

#### Rationale

Level 2 because the trace model is coherent and tells a complete operational story, with the sophisticated cross-reconciler context propagation via Kubernetes annotations being a standout feature. The absence of attributes on child spans and the uniform INTERNAL span kind prevent Level 3.

---

### 5. Multi-Signal Observability

**Level: 1 — Multiple Signals, Weak Correlation**

#### Evidence

##### Signal availability

| Signal | Status | Protocol | Notes |
|--------|--------|----------|-------|
| Traces | ✅ First-class | OTLP HTTP (native) | 1,262 spans observed |
| Metrics | ✅ First-class | Prometheus scrape (native default) | 11 Tekton-specific metrics + Knative/Go runtime metrics |
| Logs | ❌ Not OTLP | stdout only | 0 OTLP log records |

##### Cross-signal correlation

**Trace context in logs**: No. Tekton logs via zap to stdout. The structured log lines do not carry `trace_id` or `span_id` fields. No OTLP log export means trace context cannot be correlated to logs.

**Shared attributes between signals**: The `service.name` attribute is present on both traces and metrics, but with **different values**:
- Traces: `pipelinerun-reconciler`, `taskrun-reconciler`
- Metrics: `tekton-pipelines-controller`

This prevents backend-level correlation using `service.name` as a join key. An operator cannot easily navigate from a trace for `pipelinerun-reconciler` to the corresponding metrics for `tekton-pipelines-controller` without knowing this mapping.

**Metric attributes don't carry trace context**: The Tekton-specific metrics (`tekton_pipelines_controller_pipelinerun_duration_seconds`, etc.) carry `pipeline`, `status`, `namespace`, and `task` labels but no `trace_id` or `span_id` — this is expected for metrics but means exemplar-based correlation is not available.

##### Collection model

- **Traces**: OTLP HTTP push directly from the controller process → OTel Collector
- **Metrics**: Prometheus scrape (pull) by OTel Collector → OTLP export
- **Logs**: stdout → not collected into OTLP pipeline

The dual-protocol model (OTLP for traces, Prometheus for metrics) creates an operational split: traces flow through one pipeline path, metrics through another.

#### Checklist assessment

- ✅ Two signals (traces + metrics) are natively first-class
- ✅ Metrics are rich and actionable (duration histograms, counters, gauges with meaningful labels)
- ❌ No OTLP logs
- ❌ `service.name` is inconsistent between traces and metrics (prevents correlation)
- ❌ No trace context (trace_id, span_id) in log output
- ❌ Metrics do not carry exemplars linking to traces

#### Rationale

Level 1 because two signals are available and useful, but the inconsistent `service.name` across signals and the complete absence of OTLP logs prevent Level 2. The signal-to-signal correlation story is weak: an operator cannot navigate from a trace to the corresponding metrics using standard backend join keys.

---

### 6. Audience & Signal Quality

**Level: 2 — Operator-Friendly**

#### Evidence

##### Span naming

Span names are logical CI/CD operations, not internal Go function names:
- `PipelineRun:Reconciler` — root span for a PipelineRun lifecycle
- `PipelineRun:ReconcileKind` — one reconcile loop iteration
- `TaskRun:ReconcileKind` — one TaskRun reconcile loop iteration
- `reconcile` — the main reconciliation logic
- `resolvePipelineState` — pipeline state resolution
- `runNextSchedulableTask` — task scheduling
- `createTaskRuns`, `createTaskRun` — task run creation
- `prepare` — pod preparation
- `createPod` — Kubernetes pod creation
- `stopSidecars` — sidecar lifecycle management
- `durationAndCountMetrics` — metrics emission (meta-span)
- `finishReconcileUpdateEmitEvents` — reconcile completion
- `updateLabelsAndAnnotations` — Kubernetes resource update
- `updatePipelineRunStatusFromInformer` — status synchronization
- `updateTaskRunWithDefaultWorkspaces` — workspace configuration

These names are meaningful to a CI/CD platform operator without needing to understand Tekton's Go source code.

##### Signal-to-noise ratio

**Traces**: The trace volume is proportional to activity — each reconcile loop produces a bounded set of spans. The `durationAndCountMetrics` span is a minor noise item (it represents Tekton's own internal metrics emission, not a user-visible operation), but it is harmless.

**Metrics**: The 11 Tekton-specific metrics are all actionable:
- Duration histograms for PipelineRuns and TaskRuns (with `status`, `pipeline`, `task`, `namespace` labels)
- Running counts for active PipelineRuns and TaskRuns
- Pod scheduling latency histogram

The Knative and Go runtime metrics (`kn_workqueue_*`, `go_*`) are standard infrastructure metrics that add value for platform operators monitoring controller health.

The `tekton_pipelines_controller_taskruns_pod_latency_milliseconds` histogram has unbounded cardinality (per the project's own docs, see issue #9393) due to `task` and `taskrun` labels — this is a known quality issue.

##### Default usability

**Traces**: Usable out of the box for understanding PipelineRun and TaskRun execution flow. The cross-reconciler propagation (PipelineRun→TaskRun in the same trace) is particularly valuable. However, the lack of attributes on child spans limits filtering and aggregation in trace backends.

**Metrics**: Usable out of the box for dashboards. The `status`, `pipeline`, `task`, and `namespace` labels provide standard CI/CD observability dimensions. The metrics documentation (`docs/metrics.md`) includes a complete reference table.

**Logs**: Not OTLP; operators must use `kubectl logs` or a separate log collection pipeline.

#### Checklist assessment

- ✅ Span names represent logical CI/CD operations, not internal function names
- ✅ Root spans carry meaningful context (resource name + namespace)
- ✅ Metrics have actionable labels (status, pipeline, task, namespace)
- ✅ Metrics are documented with type, labels, and stability status
- ✅ Trace structure is coherent and useful without backend customization
- ❌ Child spans have no attributes — limits filtering in trace backends
- ❌ `durationAndCountMetrics` span is an internal implementation detail exposed as a span
- ❌ `tekton_pipelines_controller_taskruns_pod_latency_milliseconds` has unbounded cardinality (known issue #9393)
- ❌ No logs in the OTLP pipeline

#### Rationale

Level 2 because the telemetry is genuinely useful to a CI/CD platform operator without significant customization. Span names are logical, metrics are well-labeled, and the trace model tells a complete operational story. The main quality gaps (child span attribute absence, unbounded cardinality on one metric) prevent Level 3.

---

### 7. Stability & Change Management

**Level: 2 — Documented Stability**

#### Evidence

##### Documentation of telemetry behavior

Tekton provides dedicated documentation for its telemetry:
- `docs/metrics.md` — complete reference for all metrics with name, type, labels, and stability status (all core metrics marked `experimental`)
- `docs/metrics-migration-otel.md` — comprehensive migration guide for the OpenCensus→OpenTelemetry transition, with before/after tables for all renamed metrics

The metrics documentation explicitly labels all core Tekton metrics as `experimental`, providing an honest stability signal to operators.

##### Change communication

The v1.12.0 release notes reference PR #9641 (`📖 docs: update metrics.md to reflect OpenTelemetry migration`) and PR #9757 (`bump go.opentelemetry.io/otel/sdk from 1.42.0 to 1.43.0`), indicating that telemetry changes are tracked in release notes.

The OpenCensus→OpenTelemetry migration was documented as a **breaking change** with:
- An executive summary of what changed
- Per-category impact levels (HIGH/MEDIUM/LOW)
- Complete before/after metric name mapping tables
- Configuration migration instructions
- An explicit "Action Required" section

This is an exemplary breaking-change communication for telemetry.

##### Schema URL presence

**Traces**: `https://opentelemetry.io/schemas/1.12.0` is set on the resource spans. This is collector-enriched via the k8sattributes processor adding the v1.12.0 schema URL. The native Tekton trace resource does not independently set a schema URL — the `semconv.SchemaURL` constant from `semconv/v1.12.0` is used only when constructing the resource in `createTracerProvider()`.

Wait — re-examining: the source code does set `semconv.SchemaURL` as the schema URL when creating the resource:
```go
tracesdk.WithResource(resource.NewWithAttributes(
    semconv.SchemaURL,  // = "https://opentelemetry.io/schemas/1.12.0"
    semconv.ServiceNameKey.String(service),
))
```
This confirms the schema URL is **project-native** — Tekton explicitly sets it to `https://opentelemetry.io/schemas/1.12.0`. However, this schema version is outdated (current stable is v1.26.0).

**Metrics**: No schema URL is set on the Prometheus-scraped metrics resource or scope.

##### Stability guarantees

All core Tekton metrics are documented as `experimental`. No stability guarantee is provided for span names or span attributes. The project's approach to breaking changes (migration guide, documented in release notes) is strong, but no formal stability contract for telemetry exists.

#### Checklist assessment

- ✅ Metrics are documented with a complete reference table
- ✅ Breaking telemetry changes are documented with migration guides
- ✅ Schema URL is set natively on trace resources
- ✅ Metrics have explicit stability labels (`experimental`)
- ✅ Release notes reference telemetry-related changes
- ❌ Schema URL references outdated semconv v1.12.0 (not current v1.26.0)
- ❌ No stability guarantee for span names or attributes
- ❌ No schema URL on metrics
- ❌ No `OTEL_*` env var configuration contract documented

#### Rationale

Level 2 because Tekton provides genuine documentation of its telemetry behavior, communicates breaking changes explicitly, and sets a schema URL on traces. The outdated schema URL version and the absence of a formal stability contract for spans prevent Level 3.

---

## Key findings

### Strengths

1. **Cross-reconciler trace propagation via Kubernetes annotations**: Tekton's mechanism for propagating W3C Trace Context through Kubernetes resource annotations (`tekton.dev/pipelinerunSpanContext`, `tekton.dev/taskrunSpanContext`) is architecturally elegant and enables a complete end-to-end trace of a PipelineRun execution, including all its TaskRun children, in a single coherent trace. This is a sophisticated solution to a genuinely hard distributed tracing problem in a Kubernetes controller environment.

2. **Rich, actionable CI/CD metrics**: The 11 Tekton-specific metrics provide meaningful observability for CI/CD platform operators — duration histograms with `pipeline`, `task`, `status`, and `namespace` dimensions, running counts, and pod scheduling latency. The metrics are well-documented and the OpenCensus→OTel migration guide is an exemplary breaking-change communication.

3. **Dual OTLP export paths**: Both traces (OTLP HTTP/gRPC) and metrics (OTLP gRPC/HTTP, in addition to Prometheus) support native OTLP export, giving operators flexibility in their collection architecture. The OTel SDK version is current (v1.43.0).

### Areas for improvement

1. **Consistent `service.name` across signals**: The most impactful improvement would be aligning `service.name` between traces (`pipelinerun-reconciler`, `taskrun-reconciler`) and metrics (`tekton-pipelines-controller`). A single consistent value (e.g., `tekton-pipelines-controller`) would enable backend correlation. Additionally, adding `service.version` to the trace resource (currently absent) would complete the basic resource identity.

2. **Add attributes to child spans**: The 1,137 child spans (`reconcile`, `prepare`, `createPod`, etc.) carry zero attributes. Adding `pipelinerun`/`taskrun` + `namespace` to child spans (or at minimum to the key operational spans like `createPod`, `prepare`) would dramatically improve the utility of traces for filtering and aggregation in backends like Jaeger or Tempo.

3. **OTLP logs support and `OTEL_*` env var configuration**: Adding OTLP log export (even as an opt-in option) would complete the three-signal story. Separately, supporting standard `OTEL_*` environment variables (at minimum `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`) alongside the ConfigMap model would improve compatibility with standard OTel deployment patterns (e.g., operator-injected configuration).

### Notable observations

- **The Kubernetes annotation-based span context propagation is unique and noteworthy**: Unlike HTTP services where `traceparent` is injected into request headers, Tekton must propagate trace context across asynchronous reconciler invocations. The solution — serializing the W3C `traceparent` into a Kubernetes resource's annotations/status and re-extracting it on subsequent reconcile loops — is a creative and correct approach that deserves recognition as a pattern for other Kubernetes controllers.

- **Outdated semconv version in source**: Despite using OTel SDK v1.43.0 (current), the tracing code imports `semconv/v1.12.0` and sets the corresponding schema URL. This is a maintenance gap — the semconv package is a minor version import and could be updated to v1.26.0 without changing any attribute names used by Tekton (since Tekton doesn't use any standard semconv attributes on its spans).

- **All metrics are marked `experimental`**: The project honestly labels all its core CI/CD metrics as `experimental` in the documentation. While this is good transparency, it means operators cannot rely on metric name stability. Graduating at least the core duration and count metrics to stable would benefit the ecosystem.

- **Mixed metric naming for `_milliseconds`**: `tekton_pipelines_controller_taskruns_pod_latency_milliseconds` uses milliseconds while all other duration metrics use seconds. This inconsistency is a minor but real usability issue for operators building dashboards.

---

## Methodology notes

- Telemetry was collected using an OpenTelemetry Collector with file export (`fileexporter`) in a local kind cluster (`otel-eval-tekton`)
- The k8sattributes processor was used to enrich telemetry with Kubernetes metadata; native vs enriched attributes were distinguished by checking which attributes appear before k8s enrichment
- Traces were collected via OTLP HTTP receiver; metrics were collected via Prometheus receiver (scraping four Tekton service endpoints)
- Test workloads included: 6 standalone `TaskRun` objects (`hello-world-1` through `hello-world-6`), 1 multi-step `TaskRun` (`multi-step-1`), and 1 `PipelineRun` (`demo-pipeline-run-1`) with 4 tasks (task-a, task-b, task-c, task-d)
- Semantic conventions were checked against the OpenTelemetry specification (stable, as of early 2025)
- Source code for `pkg/tracing/tracing.go`, `pkg/reconciler/pipelinerun/tracing.go`, `pkg/reconciler/taskrun/tracing.go`, and `pkg/apis/config/metrics.go` was reviewed directly from the v1.12.0 tag
- Documentation reviewed: `docs/metrics.md`, `docs/metrics-migration-otel.md`, `config/config-observability.yaml`
