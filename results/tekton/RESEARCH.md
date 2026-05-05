# Tekton Pipeline — OTel Evaluation Research

## What is Tekton?

Tekton is a CNCF-graduated CI/CD framework for Kubernetes. It provides CRDs and controllers to define and run pipelines as Kubernetes-native resources:
- **Task** / **TaskRun** — a single unit of work executed in a Pod
- **Pipeline** / **PipelineRun** — an ordered graph of Tasks
- **Resolver** — pluggable mechanism to fetch Task/Pipeline definitions (git, bundle, cluster, etc.)

The project lives at https://github.com/tektoncd/pipeline and is installed into the `tekton-pipelines` namespace.

## Installation Method

- **Method**: Plain Kubernetes manifests (no Helm chart for the core pipeline component)
- **Version installed**: v1.12.0 (released 2026-05-04)
- **Manifest URL**: `https://github.com/tektoncd/pipeline/releases/download/v1.12.0/release.yaml`
- **Namespaces created**: `tekton-pipelines` and `tekton-pipelines-resolvers`

## Telemetry Capabilities

### Traces — OTLP HTTP (native, confirmed flowing)

Tekton supports OTLP trace export via two ConfigMaps in `tekton-pipelines`:

**`config-tracing`** (legacy, still honored in v1.12.0):
```yaml
enabled: "true"
endpoint: "http://<otlp-http-endpoint>:4318/v1/traces"
```

**`config-observability`** (new in v1.12.0):
```yaml
tracing-protocol: "http/protobuf"
tracing-endpoint: "http://<otlp-http-endpoint>:4318/v1/traces"
tracing-sampling-rate: "1.0"
```

Both were patched. Traces are flowing via OTLP HTTP from the controller.

**Confirmed trace characteristics:**
- Service names (set by Tekton itself): `pipelinerun-reconciler`, `taskrun-reconciler`
- Instrumentation scopes: `PipelineRunReconciler`, `TaskRunReconciler` (no version set)
- Span names observed: `PipelineRun:Reconciler`, `PipelineRun:ReconcileKind`, `TaskRun:Reconciler`, `TaskRun:ReconcileKind`, `reconcile`, `prepare`, `createPod`, `stopSidecars`, `resolvePipelineState`, `runNextSchedulableTask`, `createTaskRun`, `createTaskRuns`, `updateLabelsAndAnnotations`, `finishReconcileUpdateEmitEvents`, `durationAndCountMetrics`, `updateTaskRunWithDefaultWorkspaces`, `updatePipelineRunStatusFromInformer`
- Span attributes on root spans: `pipelinerun`/`taskrun` (resource name), `namespace`
- Most child spans have **no attributes** — only name + parent link
- W3C Trace Context used for propagation (traceparent)
- No `net.*`, `http.*`, or semantic convention attributes on spans

**Source**: Project-native (in-process OTel SDK in the controller binary).

### Metrics — Prometheus scrape (native, confirmed flowing)

Tekton exposes Prometheus metrics on port `9090` from four services. Controlled by `metrics-protocol: prometheus` in `config-observability` (the default). OTLP metric export is also supported (`grpc`, `http/protobuf`) but was not used — Prometheus scrape is the default and most complete path.

**Services scraped:**

| Service | Namespace | Notable metrics |
|---|---|---|
| `tekton-pipelines-controller` | `tekton-pipelines` | Tekton-specific + kn + Go runtime |
| `tekton-pipelines-webhook` | `tekton-pipelines` | kn webhook + HTTP + Go runtime |
| `tekton-events-controller` | `tekton-pipelines` | kn workqueue + Go runtime |
| `tekton-pipelines-remote-resolvers` | `tekton-pipelines-resolvers` | kn workqueue + Go runtime |

**Tekton-specific metric families (from `tekton_pipelines_controller` scope):**
- `tekton_pipelines_controller_taskrun_duration_seconds` — histogram
- `tekton_pipelines_controller_taskrun_total` — sum (counter)
- `tekton_pipelines_controller_pipelinerun_duration_seconds` — histogram
- `tekton_pipelines_controller_pipelinerun_taskrun_duration_seconds` — histogram
- `tekton_pipelines_controller_pipelinerun_total` — sum (counter)
- `tekton_pipelines_controller_running_taskruns` — gauge
- `tekton_pipelines_controller_running_pipelineruns` — gauge
- `tekton_pipelines_controller_running_taskruns_waiting_on_task_resolution_count` — gauge
- `tekton_pipelines_controller_running_pipelineruns_waiting_on_pipeline_resolution` — gauge
- `tekton_pipelines_controller_running_pipelineruns_waiting_on_task_resolution` — gauge
- `tekton_pipelines_controller_taskruns_pod_latency_milliseconds` — histogram

**Also present (Knative-derived, from `knative.dev/pkg`):**
- `kn_workqueue_*` — workqueue depth, adds, latency
- `kn_k8s_client_http_response_status_code_total`
- `kn_webhook_handler_duration_seconds` (webhook only)

**Source**: Project-native Prometheus endpoint scraped by the OTel Collector's Prometheus receiver.

### Logs — stdout only (not collected into OTLP pipeline)

Tekton uses structured JSON logging (zap) to stdout. No OTLP log export is supported. Logs were not captured in `logs.jsonl` (0 lines). Logs are only available via `kubectl logs`.

**Source**: Not project-native OTLP; stdout only.

## Context Propagation

- W3C Trace Context (`traceparent`/`tracestate`) — confirmed by span structure
- No B3 support documented or observed

## Special Setup Notes

- The `tekton-pipelines` namespace is created with PSA label `pod-security.kubernetes.io/enforce: restricted`
- No sidecar injection; all instrumentation is in-process in the controller binary
- The controller watches Pods, TaskRuns, PipelineRuns, etc. via ClusterRole
- No ingress/routing needed for telemetry evaluation

## Deviations from Documentation

- Both `config-tracing` and `config-observability` were patched; it is unclear which one is authoritative in v1.12.0. Both reference the same endpoint. In practice, traces flowed correctly.
- Instrumentation scope version is empty (not set by Tekton).
- Child spans (e.g., `reconcile`, `prepare`) have no span attributes — only the root reconciler spans carry `taskrun`/`pipelinerun` + `namespace` attributes.
- Metrics from the webhook and resolvers are Go/Knative runtime metrics, not Tekton-specific business metrics.

## What is Project-Native vs Collector-Derived

| Attribute/Signal | Source |
|---|---|
| Traces (spans, traceIDs, span names) | **Project-native** — Tekton OTel SDK |
| `service.name` on traces | **Project-native** — set by Tekton |
| Tekton-specific metrics (`tekton_pipelines_controller_*`) | **Project-native** — Prometheus endpoint |
| `kn_*` metrics | **Project-native** — Knative pkg library |
| `k8s.*` attributes on metrics/traces | **Collector-derived** — k8sattributes processor |
| `k8s.*` cluster metrics | **Collector-derived** — k8s_cluster receiver |
