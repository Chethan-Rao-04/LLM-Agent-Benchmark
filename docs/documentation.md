 # Codebase Documentation: `src/`

Scope: `src/main` (Java sources and resources) plus a note on `src/test`. This is a
read-only, citation-backed documentation pass intended as source material for the
Methodology and Implementation sections of the thesis "Benchmarking LLMs for Autonomous
Execution and Error Recovery in Proprietary CLI Environments: Evaluating Resilience Against
Documentation Quality Degradation."

Conventions used below:

- `[FACT]` — directly observable in the source.
- `[INFERENCE]` — reasoned intent where the source does not state it.
- `UNCLEAR: ...` — could not verify.

Every claim cites a file and, where possible, a line range or method.

---

## 1. Overview

### Purpose

[FACT] The system is a command-line Spring Boot application that generates synthetic
proprietary CLI "tools," asks an LLM to operate them autonomously through Spring AI tool
callbacks, simulates command execution in memory, scores the outcome across several
dimensions, and records each run to console, JSONL, Markdown, and CSV artifacts. The entry
point disables the web server: [BenchmarkApplication.java](../src/main/java/org/benchmark/app/BenchmarkApplication.java#L15-L27) sets `WebApplicationType.NONE`.

[FACT] Documentation quality is a controlled independent variable. The generator applies one
of four degradation profiles defined in [DocumentComplexity.java](../src/main/java/org/benchmark/model/enums/DocumentComplexity.java#L6-L15): `CLEAN`, `INCOMPLETE`, `UNSTRUCTURED`, `LOGICAL_CONFLICT`.

### High-level entry points

[FACT] Startup flow: `BenchmarkApplication.main` boots Spring, and `BenchmarkRunner`
(an `ApplicationRunner`) executes the benchmark once the context is ready, then closes the
context: [BenchmarkRunner.java](../src/main/java/org/benchmark/runner/BenchmarkRunner.java#L44-L51).

[FACT] Per-run orchestration in `runBenchmark()` generates cases, then for each case
initializes a session, generates a user query, executes the case, records the score, and
clears the session: [BenchmarkRunner.java](../src/main/java/org/benchmark/runner/BenchmarkRunner.java#L87-L112).

[FACT] Runtime data flow for a single case:
`BenchmarkRunner` -> `BenchmarkCaseExecutor.execute` -> `LlmClient.execute` (Spring AI chat
call) -> model-issued tool calls dispatched by the executor -> `BenchmarkToolService` ->
`BenchmarkToolExecutionService` -> `CliSimulator` -> `SessionStateManager` -> scoring and
event logging. See [BenchmarkCaseExecutor.java](../src/main/java/org/benchmark/exec/BenchmarkCaseExecutor.java#L43-L109) and [BenchmarkToolExecutionService.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionService.java#L28-L73).

---

## 2. Architecture / Structure

### Package map (main)

| Package | Responsibility (one line) |
|---|---|
| `org.benchmark.app` | Spring Boot entry point. |
| `org.benchmark.config` | Bean wiring for generation graph and model/transport infrastructure; typed properties. |
| `org.benchmark.runner` | Run lifecycle orchestration, summary aggregation, and artifact writing. |
| `org.benchmark.gen` | Case generation: scenarios, tools, decoys, documentation, user queries. |
| `org.benchmark.exec` | Retry loop, in-memory CLI simulation, per-session state. |
| `org.benchmark.tools` | Spring AI tool-callback wiring (`runtime`) and tool-facing service/policy (`server`). |
| `org.benchmark.prompt` | Prompt template rendering and retry feedback construction. |
| `org.benchmark.scoring` | Metric computation and per-attempt evaluation coordination. |
| `org.benchmark.llm` | Spring AI chat wrapper, guardrail advisor, exception hierarchy. |
| `org.benchmark.logging` | Structured JSONL event logging and per-case console/event logging. |
| `org.benchmark.model` | Immutable domain records (`objects`) and enums (`enums`). |

### Key files and responsibilities

**Config**

- [BenchmarkProperties.java](../src/main/java/org/benchmark/config/BenchmarkProperties.java#L17-L56) — `@ConfigurationProperties(prefix = "benchmark")`. Fields include `iterations`, `distractorCount`, `maxExecutionSteps`, `maxExecutionsPerAttempt` (default `1`), `maxRepeatedCommandFailuresPerAttempt` (default `1`), `temperature`, `timeoutSeconds`, `randomSeed`, `domain`, `documentComplexity` (default `CLEAN`), `trapCommand` (default `false`), plus nested `LlmProperties` (`model`, `baseUrl`, `username`, `password`) and `PromptProperties` (`baseSystemPrompt`, `attemptGuardrails`).
- [BenchmarkGenerationConfig.java](../src/main/java/org/benchmark/config/BenchmarkGenerationConfig.java#L19-L167) — declares the generator beans. The shared `Random` is seeded from `randomSeed` when present, else non-deterministic: [lines 28-33](../src/main/java/org/benchmark/config/BenchmarkGenerationConfig.java#L28-L33). The `benchmarkCaseGenerator` bean is assembled from all collaborators and passed `documentComplexity` and `trapCommand`: [lines 138-166](../src/main/java/org/benchmark/config/BenchmarkGenerationConfig.java#L138-L166).
- [BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L34-L173) — builds the `OpenAiChatModel` against an OpenAI-compatible endpoint with a `NoopApiKey`, optional Basic Auth header, model + temperature options, and a shared `RetryTemplate`; builds the `ChatClient` with the guardrail advisor as a default advisor.

**Runner**

- [BenchmarkRunner.java](../src/main/java/org/benchmark/runner/BenchmarkRunner.java#L25-L114) — the `ApplicationRunner`. Assigns a `benchmarkRunId` UUID, pushes it into SLF4J `MDC` (used by log routing), iterates cases, and publishes the summary.
- [BenchmarkRunSummaryAggregator.java](../src/main/java/org/benchmark/runner/BenchmarkRunSummaryAggregator.java#L22-L155) — accumulates per-case scores into `BenchmarkRunSummary` totals and builds per-case reports; `metrics()` converts totals to averages by dividing by `iterations`.
- [BenchmarkRunSummaryPublisher.java](../src/main/java/org/benchmark/runner/BenchmarkRunSummaryPublisher.java#L24-L49) — logs the summary to console, emits a `benchmark_run_completed` event, triggers artifact writing, and flushes the file appender.
- [BenchmarkRunArtifactWriter.java](../src/main/java/org/benchmark/runner/BenchmarkRunArtifactWriter.java#L20-L107) — writes `logs/benchmark-run-<id>.md` and `.csv`. IO failures are logged as warnings, not thrown: [lines 26-29](../src/main/java/org/benchmark/runner/BenchmarkRunArtifactWriter.java#L26-L29).
- [BenchmarkRunSummary.java](../src/main/java/org/benchmark/runner/BenchmarkRunSummary.java#L13-L26) (mutable accumulator) and [BenchmarkRunCaseReport.java](../src/main/java/org/benchmark/runner/BenchmarkRunCaseReport.java#L8-L28) (immutable per-case snapshot record).

**Generation (`gen`)**

- [BenchmarkCaseGenerator.java](../src/main/java/org/benchmark/gen/BenchmarkCaseGenerator.java#L128-L192) — `generateCases`: picks a domain and scenario pattern, resolves the scenario, builds the target tool, generates semantic decoys and random distractors, assembles a per-case manual, and packages a `BenchmarkCase` record. The `BenchmarkCase` record and its helper accessors (`allTools`, `findTool`, `trapCommand`, `generateUserQuery`) are defined at [lines 219-353](../src/main/java/org/benchmark/gen/BenchmarkCaseGenerator.java#L219-L353).
- [ScenarioLoader.java](../src/main/java/org/benchmark/gen/scenario/ScenarioLoader.java#L15-L204) — loads and validates `scenarios.yaml` (SnakeYAML) at construction; `validate` fails fast if a step references a `{variable}` with no matching pool: [lines 178-203](../src/main/java/org/benchmark/gen/scenario/ScenarioLoader.java#L178-L203).
- [ScenarioResolver.java](../src/main/java/org/benchmark/gen/scenario/ScenarioResolver.java#L33-L57) — substitutes `{variable}` placeholders using a randomly chosen domain-specific pool value and computes cumulative expected state by layering each step's effect map.
- Records: [ScenarioPattern.java](../src/main/java/org/benchmark/gen/scenario/ScenarioPattern.java#L17-L28), [ScenarioStep.java](../src/main/java/org/benchmark/gen/scenario/ScenarioStep.java#L14-L26), [ResolvedScenario.java](../src/main/java/org/benchmark/gen/scenario/ResolvedScenario.java#L17-L33), [ResolvedStep.java](../src/main/java/org/benchmark/gen/scenario/ResolvedStep.java#L18-L52) (derives `commandName = verb + "_" + noun`).
- Spec layer (semantic "source of truth"): [BenchmarkCaseSpec.java](../src/main/java/org/benchmark/gen/spec/BenchmarkCaseSpec.java#L20-L57), [CapabilityStep.java](../src/main/java/org/benchmark/gen/spec/CapabilityStep.java#L18-L60), [DecoyPlan.java](../src/main/java/org/benchmark/gen/spec/DecoyPlan.java#L16-L44), [DecoyKind.java](../src/main/java/org/benchmark/gen/spec/DecoyKind.java#L6-L12), [ScoringPolicy.java](../src/main/java/org/benchmark/gen/spec/ScoringPolicy.java#L10-L22).
- Tool generation:
  - [ScenarioToolGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/ScenarioToolGenerator.java#L68-L165) — builds the target tool whose commands match scenario steps, adds 4-6 filler commands ([lines 128-146](../src/main/java/org/benchmark/gen/tool_generator/ScenarioToolGenerator.java#L128-L146)), and can inject a trap + recovery command.
  - [SemanticDecoyGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/SemanticDecoyGenerator.java#L61-L184) — builds distractor tools reusing the same domain pool with alternative nouns; the `SIMILAR_COMMANDS_WRONG_STATE_PATH` kind shifts effect/precondition values with an `_alternate` suffix.
  - [ToolSpecGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/ToolSpecGenerator.java#L49-L124) — builds unrelated same-domain distractor tools (7-9 commands, small int/string state schema).
  - [CommandDict.java](../src/main/java/org/benchmark/gen/tool_generator/CommandDict.java#L13-L190) — domain vocabulary pools (verbs, nouns, state vars, common options) for `MANUFACTURING` and `NETWORK_INFRA`.
  - [CommandAbbreviator.java](../src/main/java/org/benchmark/gen/tool_generator/CommandAbbreviator.java#L11-L205) — single source of truth for command-name abbreviation via a static lookup table; `commandName(verb, noun) = abbr(verb) + "_" + abbr(noun)`.
  - [CommandOptionGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/CommandOptionGenerator.java#L15-L34) — emits at most one option per command; ~50% of commands get no options.
- [DocumentationGenerator.java](../src/main/java/org/benchmark/gen/doc_generator/DocumentationGenerator.java#L18-L303) — renders per-tool documentation for each complexity profile. For the target tool it first overlays semantic command descriptions from the spec: [lines 36-86](../src/main/java/org/benchmark/gen/doc_generator/DocumentationGenerator.java#L36-L86).
- [UserQueryGenerator.java](../src/main/java/org/benchmark/gen/query_generator/UserQueryGenerator.java#L18-L76) — produces a goal-only user request from one of four templates; deliberately describes only the desired end state, not the ordered steps.

**Execution (`exec`)**

- [BenchmarkCaseExecutor.java](../src/main/java/org/benchmark/exec/BenchmarkCaseExecutor.java#L43-L109) — the retry loop. Each attempt: `startAttempt`, optionally append a retry message, call the model, dispatch the returned tool calls, evaluate the attempt, and append feedback. Loop runs while `attempt < maxExecutionSteps` and goal not achieved.
- [SessionStateManager.java](../src/main/java/org/benchmark/exec/SessionStateManager.java#L19-L353) — per-session store (`ConcurrentHashMap`), with per-session monitor locks. Holds the benchmark case, one `ToolEnvironment` per tool, an execution log, and a rejection log. Includes per-attempt bookkeeping (`attemptStartIndex`, `attemptExecutionConsumed`) used by execution safeguards.
- [CliSimulator.java](../src/main/java/org/benchmark/exec/CliSimulator.java#L23-L177) — resolves the command in the tool, checks preconditions, validates the option, then applies effects. Returns a success/failure `ExecutionResult` record.
- [CommandEffectApplier.java](../src/main/java/org/benchmark/exec/CommandEffectApplier.java#L17-L105) — shared effect application for both live session state and plain-map expected-state computation; supports `ASSIGN` / `INCREMENT` / `DECREMENT` / `DELETE` and `$OPTION` value resolution.
- [CommandOptionNormalizer.java](../src/main/java/org/benchmark/exec/CommandOptionNormalizer.java#L3-L28) — normalizes model-supplied option strings (`<none>`, `none`, `null`, `""`, `''`, `-` → empty).
- [ToolEnvironment.java](../src/main/java/org/benchmark/exec/ToolEnvironment.java#L13-L64) — mutable per-tool key/value state seeded with declared schema keys.

**Tools (`tools`)**

- [BenchmarkCaseToolCallbackFactory.java](../src/main/java/org/benchmark/tools/runtime/BenchmarkCaseToolCallbackFactory.java#L17-L82) — exposes exactly two Spring AI `FunctionToolCallback`s per session: `getCurrentState` and `executeCommand`. The `sessionId` is bound into each callback closure.
- [BenchmarkToolService.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolService.java#L11-L74) — stable facade for state inspection and command execution; defines response records and a `CommandOutcomeType` (`EXECUTED` / `REJECTED`).
- [BenchmarkToolExecutionService.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionService.java#L28-L73) — runs a command under a session lock: enforces the single-execution-per-attempt guard, validates tool/command, applies the execution policy, invokes the simulator, and records the result.
- [BenchmarkToolExecutionPolicy.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionPolicy.java#L20-L67) — rejects execution when the per-attempt execution budget is exceeded, when the same command already failed this attempt, or when the scenario is already complete.
- [BenchmarkToolEventPublisher.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolEventPublisher.java#L17-L71) — emits tool-facing execution/rejection events into the JSONL stream.

**Prompt (`prompt`)**

- [BenchmarkPromptBuilder.java](../src/main/java/org/benchmark/prompt/BenchmarkPromptBuilder.java#L24-L62) — renders system/user/retry prompts from `src/main/resources/prompts/*.st` via Spring AI `PromptTemplate`, injecting the case manual and current state snapshot.
- [BenchmarkAttemptFeedbackBuilder.java](../src/main/java/org/benchmark/prompt/BenchmarkAttemptFeedbackBuilder.java#L21-L74) — builds retry feedback from observable runtime evidence only (latest executions + current state), explicitly avoiding leaking the hidden answer path.

**Scoring (`scoring`)**

- [BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L20-L368) — computes the seven metrics and the pass/recovery flags; hosts the `BenchmarkScore` and `AttemptMetrics` records and a longest-increasing-subsequence helper for ordering.
- [BenchmarkScoringCoordinator.java](../src/main/java/org/benchmark/scoring/BenchmarkScoringCoordinator.java#L21-L74) — derives attempt-local executions/rejections, decides `goalAchieved`, builds feedback, and computes attempt metrics.

**LLM (`llm`)**

- [LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L31-L263) — wraps `ChatClient` calls, disables Spring AI internal tool execution, attaches tool callbacks, wraps each call in a Micrometer `Observation`, and translates transport exceptions into a benchmark exception hierarchy.
- [BenchmarkGuardrailPromptAdvisor.java](../src/main/java/org/benchmark/llm/BenchmarkGuardrailPromptAdvisor.java#L22-L80) — a `CallAdvisor` that appends configured guardrail text (`[GUARDRAILS]: ...`) to the system message.
- Exceptions: [LlmClientException.java](../src/main/java/org/benchmark/llm/LlmClientException.java#L6-L17) (base) with [LlmServiceException.java](../src/main/java/org/benchmark/llm/LlmServiceException.java#L6-L17), [LlmToolCallbackException.java](../src/main/java/org/benchmark/llm/LlmToolCallbackException.java#L6-L17), [LlmTransientException.java](../src/main/java/org/benchmark/llm/LlmTransientException.java#L6-L17).

**Logging (`logging`)**

- [BenchmarkEventLogger.java](../src/main/java/org/benchmark/logging/BenchmarkEventLogger.java#L26-L107) — single writer for JSONL events via a dedicated `BENCHMARK_EVENTS` SLF4J logger and Jackson; can flush file appenders after a run.
- [BenchmarkCaseLogger.java](../src/main/java/org/benchmark/logging/BenchmarkCaseLogger.java#L32-L330) — console + JSONL logging for case start, attempt start, attempt completion, and case completion, including trap details when present.

**Model (`model`)**

- Objects (immutable records): [ToolObject.java](../src/main/java/org/benchmark/model/objects/ToolObject.java#L16-L23), [CommandObject.java](../src/main/java/org/benchmark/model/objects/CommandObject.java#L15-L50) (carries both real `commandEffectObjects` and optional `documentedEffects`), [EffectObject.java](../src/main/java/org/benchmark/model/objects/EffectObject.java#L12-L19) (`OPTION_REF = "$OPTION"`), [OptionEntity.java](../src/main/java/org/benchmark/model/objects/OptionEntity.java#L11-L30).
- Enums: [Domain.java](../src/main/java/org/benchmark/model/enums/Domain.java#L6-L11) (`MANUFACTURING`, `NETWORK_INFRA`), [EffectOp.java](../src/main/java/org/benchmark/model/enums/EffectOp.java#L6-L15), [DocumentComplexity.java](../src/main/java/org/benchmark/model/enums/DocumentComplexity.java#L6-L15).

### Data flow (inputs / outputs / side effects)

- **Inputs:** `benchmark.*` properties bound from `application.yml`; scenario templates from [scenarios.yaml](../src/main/resources/scenarios.yaml#L1-L118); prompt templates from `src/main/resources/prompts`; the configured model endpoint.
- **In-memory state:** all mutable state lives in `SessionStateManager` per session; cleared after each case in [BenchmarkRunner.java](../src/main/java/org/benchmark/runner/BenchmarkRunner.java#L106-L108).
- **Outputs / side effects:** console logs; `logs/benchmark-run-<id>.jsonl` (raw events); `logs/benchmark-run-<id>.md`; `logs/benchmark-run-<id>.csv`; outbound HTTP to the model endpoint. Log routing is configured in [logback-spring.xml](../src/main/resources/logback-spring.xml#L1-L40).

---

## 3. Key Design Decisions

### Semantic "spec" as the source of truth

[FACT] Scenario resolution produces a `ResolvedScenario`, but generators and the query
generator operate against a derived `BenchmarkCaseSpec` of `CapabilityStep`s
([BenchmarkCaseSpec.java](../src/main/java/org/benchmark/gen/spec/BenchmarkCaseSpec.java#L39-L56)).
[INFERENCE] This indirection appears intended to decouple user-facing intent (full words) from
proprietary command naming (abbreviations), so queries never leak command spelling. Supported
by the comment in [ResolvedStep.java](../src/main/java/org/benchmark/gen/scenario/ResolvedStep.java#L8-L14) and the separate abbreviation path.

### Uniform command-name abbreviation

[FACT] All generated command names pass through `CommandAbbreviator.commandName`, while NL
queries use the original full words ([CommandAbbreviator.java](../src/main/java/org/benchmark/gen/tool_generator/CommandAbbreviator.java#L11-L18)).
[INFERENCE] This makes command identifiers look "proprietary" (e.g., `ini_sys`) while keeping
a stable logical identity for scoring.

### Documentation degradation is per-profile rendering, not data corruption

[FACT] The four profiles are pure rendering transforms over the same `ToolObject`:
`CLEAN` structured output, `INCOMPLETE` drops one of effects/options/preconditions in a
rotating pattern (`commandIndex % 3`), `UNSTRUCTURED` strips markup/whitespace, and
`LOGICAL_CONFLICT` interleaves correct structured sections with contradictory prose notes
("v2.1 migration guide", "Errata", "Correction"). See [DocumentationGenerator.java](../src/main/java/org/benchmark/gen/doc_generator/DocumentationGenerator.java#L186-L303). [FACT] The underlying command
semantics used by the simulator are unchanged across profiles — only the manual text differs.

### Trap / recovery mechanism for error-recovery measurement

[FACT] Traps are opt-in and probabilistic: a case enables a trap only when `trapCommand` is
true and `random.nextInt(5) == 0`, i.e. ~1 in 5 eligible cases
([BenchmarkCaseGenerator.java](../src/main/java/org/benchmark/gen/BenchmarkCaseGenerator.java#L144-L144)).
[FACT] A trapped step command documents the correct effect but really assigns a wrong value
(`correctValue + "_" + {DEGRADED|PENDING|PARTIAL|UNSTABLE|LIMITED}`), and a separate recovery
command restores the correct value only when the wrong value is present
([ScenarioToolGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/ScenarioToolGenerator.java#L168-L223)).
[INFERENCE] This design forces the model to detect a state/documentation mismatch and find an
unlisted corrective command, which is what the thesis calls autonomous error recovery.

### Strict per-attempt execution budget

[FACT] Each attempt may reach the simulator at most once by default: the executor calls
`startAttempt` and the execution service checks `attemptExecutionConsumed` and marks it after
one real execution ([BenchmarkToolExecutionService.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionService.java#L35-L67)).
Additional guardrails (execution budget, repeated-failure block, scenario-complete block) live
in [BenchmarkToolExecutionPolicy.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionPolicy.java#L25-L59).
[INFERENCE] Forcing one execution per turn makes each attempt an observable decision point and
keeps the retry loop and scoring aligned to discrete steps.

### Feedback must not leak the answer path

[FACT] Retry feedback is built only from the latest executions and current state, with a
generic "read the documentation/state" instruction ([BenchmarkAttemptFeedbackBuilder.java](../src/main/java/org/benchmark/prompt/BenchmarkAttemptFeedbackBuilder.java#L33-L59)). The class comment states this is intentional.

### Composite score excludes decoy resistance

[FACT] `BenchmarkScore.composite()` averages six metrics (tool selection, step completion,
ordering, state accuracy, efficiency, command precision) and deliberately excludes
`decoyResistance`, which is reported separately ([BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L40-L50)). The comment states this keeps historical composite baselines stable.

### Scoring specifics

[FACT] Ordering accuracy uses a longest-increasing-subsequence over the indices of successful
target-tool step executions, divided by step count ([BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L187-L212) and [lines 345-367](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L345-L367)).
[FACT] Efficiency weights failed executions at 1.5x and, when a trap is present, expects two
extra steps (recovery + retry) ([BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L244-L258)).
[FACT] Decoy resistance penalizes a successful state-mutating semantic-decoy execution by 0.60
and a harmless decoy probe by 0.25 ([BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L288-L311)).
[FACT] A case "passes" iff `goalAchieved`, and `goalAchieved` requires step completion == 1.0,
ordering == 1.0, and expected-state match == 1.0 ([BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L149-L154)).
[FACT] `recovery` is true if the recovery command was used, or if the first attempt failed and
the goal was later achieved ([BenchmarkScorer.java](../src/main/java/org/benchmark/scoring/BenchmarkScorer.java#L94-L97)).

### In-process tool callbacks (no external MCP transport)

[FACT] Tools are exposed through Spring AI `FunctionToolCallback` instances created per session,
and Spring AI's internal tool execution is disabled so the executor dispatches tool calls
itself ([LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L102-L106); [BenchmarkCaseExecutor.java](../src/main/java/org/benchmark/exec/BenchmarkCaseExecutor.java#L133-L165)).
[INFERENCE] Manual dispatch lets the benchmark enforce its per-attempt budget and record every
tool call as a scored event rather than delegating the loop to the framework.

### Deterministic generation option

[FACT] A single shared `Random` (optionally seeded via `benchmark.random-seed`) is injected
into every generator ([BenchmarkGenerationConfig.java](../src/main/java/org/benchmark/config/BenchmarkGenerationConfig.java#L28-L33)).
[INFERENCE] This supports reproducible case generation across runs.

### Observability

[FACT] `LlmClient` records Micrometer `Observation`s per model call with session/attempt/model
tags and outcome/latency ([LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L108-L160)), and the infra config registers `ObservationRegistry.create()` ([BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L36-L39)).
[INFERENCE] No `ObservationHandler`/registry export is wired in the shown configuration, so
observations are recorded but not exported to any backend. UNCLEAR: whether an exporter is
configured elsewhere (e.g., in the unreadable `application.yml`).

---

## 4. Dependencies & Integration Points

### External libraries (observed in imports)

- Spring Boot (`org.springframework.boot.*`) — application bootstrap, `ApplicationRunner`, configuration properties. [BenchmarkApplication.java](../src/main/java/org/benchmark/app/BenchmarkApplication.java#L1-L14).
- Spring AI OpenAI + chat client (`org.springframework.ai.*`) — `OpenAiChatModel`, `OpenAiApi`, `ChatClient`, `PromptTemplate`, `FunctionToolCallback`, `CallAdvisor`. [BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L1-L25), [LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L6-L22).
- Spring Retry (`org.springframework.retry.*`) — `RetryTemplate` for transient AI failures. [BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L82-L101).
- Micrometer Observation (`io.micrometer.observation.*`) — LLM call instrumentation. [LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L3-L4).
- SnakeYAML (`org.yaml.snakeyaml.Yaml`) — scenario parsing. [ScenarioLoader.java](../src/main/java/org/benchmark/gen/scenario/ScenarioLoader.java#L3-L3).
- Jackson (`com.fasterxml.jackson.databind.ObjectMapper`) — JSONL event serialization. [BenchmarkEventLogger.java](../src/main/java/org/benchmark/logging/BenchmarkEventLogger.java#L6-L7).
- Logback (`ch.qos.logback.*`) — sifting file appender and appender flushing. [BenchmarkEventLogger.java](../src/main/java/org/benchmark/logging/BenchmarkEventLogger.java#L1-L5), [logback-spring.xml](../src/main/resources/logback-spring.xml#L11-L27).
- Lombok — `@Slf4j`, `@RequiredArgsConstructor`, `@Getter/@Setter` across many classes.
- JDK `HttpClient` via `JdkClientHttpRequestFactory` for transport timeouts. [BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L131-L146).

### Model endpoint integration

[FACT] The client targets an OpenAI-compatible endpoint at `benchmark.llm.base-url` with a
`NoopApiKey` and optional Basic Auth header derived from `benchmark.llm.username/password`
([BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L52-L80), [lines 148-172](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L148-L172)).

### Config / resource dependencies

- `benchmark.*` properties → [BenchmarkProperties.java](../src/main/java/org/benchmark/config/BenchmarkProperties.java#L17-L56).
- `spring.ai.retry.*` properties (with in-code defaults 5 / 2000ms / 2.0 / 10000ms) → [BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L91-L95).
- Classpath resources: `scenarios.yaml`, `prompts/benchmark-system.st`, `prompts/benchmark-user.st`, `prompts/benchmark-retry.st`.

### Scenario templates

[FACT] `scenarios.yaml` defines five patterns (`init_configure_execute`,
`diagnose_repair_verify`, `auth_provision_deploy`, `auth_inspect_configure_verify`,
`diagnose_isolate_repair_verify`), each with steps and per-domain pools ([scenarios.yaml](../src/main/resources/scenarios.yaml#L1-L118)).

### Prompt templates

[FACT] The system prompt injects `{baseSystemPrompt}` and `{caseManual}` and includes a
response policy requiring exactly one `executeCommand` call per attempt while the goal is
incomplete ([benchmark-system.st](../src/main/resources/prompts/benchmark-system.st#L1-L13)). User and retry templates inject session id, user goal, feedback, and current state ([benchmark-user.st](../src/main/resources/prompts/benchmark-user.st#L1-L3), [benchmark-retry.st](../src/main/resources/prompts/benchmark-retry.st#L1-L5)).

---

## 5. Notable Limitations / Edge Cases (explicitly handled in code)

- [FACT] Missing pool values for a scenario variable/domain throw `IllegalStateException`
  ([ScenarioResolver.java](../src/main/java/org/benchmark/gen/scenario/ScenarioResolver.java#L70-L73)); scenario template validation throws for undefined `{variable}` references ([ScenarioLoader.java](../src/main/java/org/benchmark/gen/scenario/ScenarioLoader.java#L190-L200)).
- [FACT] Non-positive `benchmark.timeout-seconds` throws at startup ([BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L140-L145)).
- [FACT] A model call failure (`LlmServiceException`) is caught and treated as a failed attempt
  rather than aborting the run ([BenchmarkCaseExecutor.java](../src/main/java/org/benchmark/exec/BenchmarkCaseExecutor.java#L118-L124)).
- [FACT] If the model calls an unknown tool callback, the executor throws
  `LlmToolCallbackException` ([BenchmarkCaseExecutor.java](../src/main/java/org/benchmark/exec/BenchmarkCaseExecutor.java#L142-L146)).
- [FACT] The simulator rejects unknown commands, unmet preconditions, unknown options, and a
  missing/mismatched required option ([CliSimulator.java](../src/main/java/org/benchmark/exec/CliSimulator.java#L47-L145)).
- [FACT] The runtime accepts only a single option per command; a declared required option must
  equal the provided option ([CliSimulator.java](../src/main/java/org/benchmark/exec/CliSimulator.java#L137-L143)).
- [FACT] Numeric effects on non-numeric state default the current value to 0 with a warning
  ([CommandEffectApplier.java](../src/main/java/org/benchmark/exec/CommandEffectApplier.java#L94-L104)).
- [FACT] Repeated identical failing commands within an attempt are blocked
  ([BenchmarkToolExecutionPolicy.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionPolicy.java#L38-L47)); further commands after scenario completion are rejected ([lines 49-56](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionPolicy.java#L49-L56)).
- [FACT] Missing token-usage metadata degrades to a count of 0 with a warning ([LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L126-L129)); an empty assistant message is enriched with a tool-call summary or `<no assistant message>` placeholder ([lines 172-197](../src/main/java/org/benchmark/llm/LlmClient.java#L172-L197)).
- [FACT] Artifact-writing IO errors and JSON serialization errors are logged as warnings, not
  thrown ([BenchmarkRunArtifactWriter.java](../src/main/java/org/benchmark/runner/BenchmarkRunArtifactWriter.java#L26-L29), [BenchmarkEventLogger.java](../src/main/java/org/benchmark/logging/BenchmarkEventLogger.java#L63-L67)).
- [FACT] Trap generation self-heals: if no unique recovery command name can be created, the trap
  is discarded and the tool is rebuilt without a trap ([ScenarioToolGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/ScenarioToolGenerator.java#L111-L126)).
- [FACT] `averages` guard against division by zero when `iterations <= 0`
  ([BenchmarkRunSummaryAggregator.java](../src/main/java/org/benchmark/runner/BenchmarkRunSummaryAggregator.java#L90-L92)).
- [FACT] Semantic decoy count is capped at 2 and bounded by `distractorCount`
  ([BenchmarkCaseGenerator.java](../src/main/java/org/benchmark/gen/BenchmarkCaseGenerator.java#L141-L141)).

---

## 6. Open Questions

- UNCLEAR: `src/main/resources/application.yml` — unreadable in this pass (Copilot-ignored).
  Concrete runtime values (model name, base URL, iteration counts, guardrail text, actual
  `spring.ai.retry.*` values, and any observability exporter) could not be verified from
  source. Property *names* and in-code defaults are documented from
  [BenchmarkProperties.java](../src/main/java/org/benchmark/config/BenchmarkProperties.java#L17-L56) and [BenchmarkInfrastructureConfig.java](../src/main/java/org/benchmark/config/BenchmarkInfrastructureConfig.java#L91-L95).
- [FACT] Dead/unused code: `BenchmarkToolEventPublisher.publishToolCatalogListed`,
  `publishToolDocumentationRead`, and `publishStateRead` have no callers in `src` (verified by
  workspace search); only `publishExecution` and `publishCommandRejection` are invoked from
  [BenchmarkToolExecutionService.java](../src/main/java/org/benchmark/tools/server/BenchmarkToolExecutionService.java#L74-L100). UNCLEAR: whether these are placeholders for a planned MCP-style tool catalog surface.
- UNCLEAR: The README states the project "does not currently publish Spring AI Observation
  telemetry," but `LlmClient` contains active Micrometer Observation instrumentation
  ([LlmClient.java](../src/main/java/org/benchmark/llm/LlmClient.java#L108-L160)). Whether telemetry is effectively published depends on registry/handler
  configuration not visible in `src` (see the `application.yml` note above).
- [INFERENCE] The `DecoyKind` categories `SAME_DOMAIN_WRONG_LIFECYCLE`,
  `VALID_TOOL_IRRELEVANT_GOAL`, and `RANDOM_DISTRACTOR` are declared
  ([DecoyKind.java](../src/main/java/org/benchmark/gen/spec/DecoyKind.java#L6-L12)) but the current `DecoyPlan.currentDefault` only ever assigns
  `SIMILAR_INTENT_WRONG_RESOURCE` and `SIMILAR_COMMANDS_WRONG_STATE_PATH`
  ([DecoyPlan.java](../src/main/java/org/benchmark/gen/spec/DecoyPlan.java#L30-L42)). The remaining kinds appear reserved for future use. UNCLEAR: intended
  activation path.
- [FACT] `CommandObject` supports `INCREMENT`/`DECREMENT`/`DELETE` effect ops and an `$OPTION`
  value reference, but scenario-derived target steps only ever emit `ASSIGN`
  ([ScenarioToolGenerator.java](../src/main/java/org/benchmark/gen/tool_generator/ScenarioToolGenerator.java#L233-L239)); `INCREMENT` is used only for numeric filler effects. UNCLEAR: whether
  `DECREMENT`/`DELETE`/`$OPTION` are exercised by any scenario in practice.
- Test coverage exists under `src/test/java/org/benchmark` for the executor-adjacent
  components (e.g., `CliSimulatorTest`, `CommandEffectApplierTest`, `SessionStateManagerTest`,
  `BenchmarkCaseGeneratorTest`, `DocumentationGeneratorTest`, `ScenarioLoaderTest`,
  `LlmClientTest`, `OllamaConnectionTest`, `BenchmarkAttemptFeedbackBuilderTest`,
  `BenchmarkPromptBuilderTest`, `BenchmarkRunArtifactWriterTest`, `BenchmarkScorerTest`,
  `BenchmarkCaseToolCallbackFactoryTest`, `BenchmarkToolServiceTest`). Their internal
  assertions were not read in this pass and are therefore not described here.
