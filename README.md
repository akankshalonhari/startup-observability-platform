# Unified Observability Platform

A simple Spring Boot implementation of the P0 scope in [PRD.pdf](PRD.pdf). The platform has three main parts:

- a **knowledge graph** that gives each asset one identity, links assets through their dependencies and records change events
- a **supervisor agent** that scopes an alert storm through the graph, fans out to specialist agents and runs three validation gates
- **one evidence-linked brief** per incident, with **one page** to the team that owns the probable cause

The PRD's reference incident, "the Malaysia Tuesday", is built in as a replay. Running it is the check for Gate 2: the replay must find the DNS change.

## Run it

Requires Java 21. The Maven wrapper is included.

```bash
./mvnw verify
```

```bash
./mvnw spring-boot:run
```

```bash
curl -s -X POST localhost:8080/api/demo/malaysia-replay | jq .brief
```

The replay sends four alerts from LogicMonitor, Azure Monitor and Turbonomic, plus one duplicate. They become **one** incident:

- **Probable cause:** ServiceNow change `CHG0049217`, which led Infoblox scavenging to remove 14 HANA replication A-records.
- **Switch flap at 02:14:** kept as context and flagged *coincidental*, because the link recovered while the symptoms carried on.
- **Restart and shutdown suggestions:** the safety gate strips them. Every recommendation is recommend-only.
- **Paging:** only `network-oncall` is paged, instead of three rotations.

## What maps to the PRD

| PRD | Where |
|---|---|
| C-1 one resource schema | `ingest/ResourceAttributes` (`host.id`, `host.name`, `service.name`, `site.code`, `zone.tier`, `deployment.environment`) |
| C-3 bridge existing tools | `POST /api/ingest/alerts` takes each tool's own identifiers (`identity/SourceTool`) |
| S-1 Mimir / Loki / Tempo | `telemetry/TelemetryStore`, an in-memory stand-in. Evidence is PromQL, log ids and trace ids |
| G-1 identity resolver | `identity/IdentityResolver`: VMUUID = host.id, then tool id, then IP, then normalised name. Every alias is kept |
| G-2 CMDB + ServiceNow changes | `POST /api/ingest/cmdb`, `POST /api/ingest/changes` (MODIFIED edges) |
| G-3 dependency edges, v1 ontology | `graph/NodeType`, `graph/EdgeType`, time-bounded `graph/Edge` |
| G-4 read-only query API | `GET /api/graph/...` backed by the read-only `graph/GraphReader` |
| A-1 supervisor | `agents/SupervisorAgent` |
| A-2 specialists, read-only tools | `NetworkAgent`, `SapAgent`, `InfraAgent`, `CloudAgent`, `LogAnalystAgent`, all given only `ReadOnlyTools` |
| A-3 three gates | `agents/validation/EvidenceGate`, `ContradictionGate`, `SafetyAuditGate` |
| A-4 dedup + correlation | `incident/IncidentService` (graph distance + time window, hold-down before paging) |
| I-1 one page, ServiceNow incident on the CI | `integration/PagerDutyGateway`, `ServiceNowGateway` (stubs that record instead of calling out) |
| I-2 recommendations as change requests | `POST /api/change-requests` (refused for L0, status `PENDING_APPROVAL`) |
| O-1 GenAI tracing | `aiobs/AgentTelemetry`: spans with tool calls, tokens, latency and cost. Metrics on `/actuator/prometheus` |
| O-3 PII redaction (P1) | `aiobs/PiiRedactor`, applied to brief output |
| Hard constraint 1: OT is L0, observe-only | Build time: `policy/ot_observe_only.rego` and `OtObserveOnlyPolicyTest`. Run time: `policy/ActionPolicy`, the safety gate, and assets with no zone treated as L0 |
| Hard constraint 2/3: human in the loop, change ladder | No code path executes remediation. `ChangeLevel` starts at `RECOMMEND_ONLY` |
| Hard constraint 4: evidence or nothing | `EvidenceGate` re-resolves every pointer. Uncited claims appear in `droppedClaims` and never reach the brief |
| NFR auditability | `IncidentBrief.audit`: prompts, tool calls, evidence, gate log, model version |

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/demo/malaysia-replay` | Replay the reference incident |
| POST | `/api/ingest/alerts` · `/signals` · `/changes` · `/cmdb` | Ingestion |
| GET | `/api/graph/nodes?type=` · `/nodes/{id}` · `/nodes/{id}/neighbours?hops=&at=` | Graph |
| GET | `/api/graph/nodes/{id}/changes?hops=2&days=7` | "What changed within two hops in the last 7 days?" |
| GET | `/api/graph/nodes/{id}/dependents` · `/zone-tier` | Blast radius, zone tier |
| POST/GET | `/api/identity/resolve` · `/lookup?identifier=` · `/coverage` | Identity |
| GET/POST | `/api/incidents` · `/{id}` · `/{id}/brief` · `/{id}/triage` | Incidents |
| POST/GET | `/api/change-requests` | Raise a change request from a recommendation |
| GET | `/api/ai-observability/agents` · `/spans` | Per-agent runs, latency, tokens, cost, evidence pass rate |
| GET | `/api/integrations/pagerduty/pages` · `/servicenow/tickets` | What the stubs would have sent |

## Deliberate simplifications

- **In-memory graph and telemetry store.** The graph engine (Neptune or Memgraph/FalkorDB) is an open question in the PRD. `KnowledgeGraph` and `TelemetryStore` are the seams a real engine or Mimir/Loki/Tempo adapters would sit behind.
- **Rule-based specialists.** The PRD hasn't settled which AI platform and models are approved, so the specialists are deterministic rules for now. `TemplateNarrativeWriter` builds the real prompt for the audit trail and then fills a template. A model-backed `NarrativeWriter` or `SpecialistAgent` can be added without changing the gates or the audit trail.
- **Stubbed PagerDuty and ServiceNow.** They record what they would send.
- **Out of scope:** OTel collector deployment, Kafka, network/IAM enforcement and P1/P2 items such as RAG and anomaly detection. These are infrastructure or later-phase work.
