# Benchmark LLM Agent

This project is the benchmark harness for the thesis:

Benchmarking LLMs for Autonomous Execution and Error Recovery in Proprietary CLI Environments: Evaluating Resilience Against Documentation Quality Degradation

The application is written in Java with Spring Boot and Spring AI. It generates synthetic proprietary CLI environments, asks an LLM to operate them autonomously, scores the outcome, and records each run locally through structured JSONL events, a human-readable Markdown report, a CSV summary, and console logs.

## What the benchmark does

For each benchmark case, the system:

- generates one target tool plus distractor tools
- generates documentation for each tool with a configurable quality profile
- creates a goal-oriented user request
- injects a full per-case manual into the prompt, then lets the model inspect state and execute commands through Spring AI tool callbacks
- simulates command execution in memory
- scores success, ordering, state accuracy, efficiency, precision, and recovery behavior

This lets you test how well an LLM can work in a proprietary CLI-style environment when documentation is clean, incomplete, unstructured, or logically conflicting.

## High level architecture

Main runtime flow:

`BenchmarkApplication` -> `BenchmarkRunner` -> `BenchmarkCaseGenerator` -> `BenchmarkCaseExecutor` -> `LlmClient` -> tool callbacks -> `BenchmarkToolService` -> `CliSimulator` -> scoring and local event logging

Key packages:

- `org.benchmark.runner`: benchmark lifecycle orchestration
- `org.benchmark.gen`: case, scenario, tool, query, and documentation generation
- `org.benchmark.exec`: retry loop, state management, and CLI simulation
- `org.benchmark.tools`: Spring AI tool callback wiring and tool-facing service layer
- `org.benchmark.prompt`: externalized prompt rendering and retry feedback
- `org.benchmark.scoring`: benchmark metrics and attempt evaluation
- `org.benchmark.logging`: console and JSONL event recording

Spring AI integration details:

- system and user prompts are rendered from `src/main/resources/prompts` through `PromptTemplate`
- runtime guardrails are appended through `BenchmarkGuardrailPromptAdvisor`
- execution-phase capabilities are exposed to the model through Spring AI `FunctionToolCallback` instances
- the project no longer uses Langfuse, and it does not currently publish Spring AI Observation telemetry

## Thesis alignment

The benchmark directly supports the thesis in three ways.

First, the environment is proprietary by construction. Tool names, commands, options, and state variables are generated for the benchmark, so the model cannot rely on prior memorized product knowledge.

Second, execution is autonomous. The model receives a user goal plus the generated case manual, then must reason about preconditions and effects, call tools, inspect state, and recover across retries.

Third, documentation quality degradation is a controlled independent variable. The benchmark can expose the same underlying task with different documentation profiles and compare how model behavior changes.

## Current stack

- Java 21
- Spring Boot 3.4.3
- Spring AI 1.1.2
- Maven
- Jackson JSON event logging

## Configuration

The main runtime settings live in [application.yml](/Users/Admin/LLM-Agent-Benchmark/Benchmark-LLM-Agent/src/main/resources/application.yml).

Important properties:

- `benchmark.iterations`: number of benchmark cases to execute
- `benchmark.distractor-count`: number of non-target tools per case
- `benchmark.max-execution-steps`: how many model attempts are allowed per case
- `benchmark.max-executions-per-attempt`: execution budget per attempt
- `benchmark.document-complexity`: `CLEAN`, `INCOMPLETE`, `UNSTRUCTURED`, or `LOGICAL_CONFLICT`
- `benchmark.trap-command`: enables misleading command behavior for recovery testing
- `benchmark.llm.model`: model name sent to the OpenAI-compatible endpoint
- `benchmark.llm.base-url`: model server base URL
- `benchmark.timeout-seconds`: HTTP timeout for model calls
- `benchmark.prompt.base-system-prompt`: baseline system instruction before session data is injected
- `benchmark.prompt.attempt-guardrails`: advisor-managed guardrail text appended to the system prompt
- `spring.ai.retry.*`: retry policy for transient model transport failures

## How to run the benchmark

### Prerequisites

- Java 21 installed
- Maven installed
- an OpenAI-compatible model endpoint reachable at the configured `benchmark.llm.base-url`

The project does not currently include `mvnw`, so the commands below use the system `mvn`.

### 1. Review configuration

Open [application.yml](/Users/Admin/LLM-Agent-Benchmark/Benchmark-LLM-Agent/src/main/resources/application.yml) and adjust the benchmark settings for the run you want.

At minimum, verify:

- `benchmark.llm.model`
- `benchmark.llm.base-url`
- `benchmark.iterations`
- `benchmark.document-complexity`
- `benchmark.trap-command`

If your model endpoint needs Basic Auth, set:

- `benchmark.llm.username`
- `benchmark.llm.password`

### 2. Compile the project

```bash
mvn -DskipTests compile
```

This verifies that the Spring Boot application, generator graph, and benchmark components compile before you start a run.

### 3. Run the benchmark

```bash
mvn spring-boot:run
```

What happens during startup:

- Spring Boot starts the application
- `BenchmarkRunner` begins a benchmark run
- cases are generated from `scenarios.yaml`
- a session is created for each case
- the LLM is allowed to call `getCurrentState` and `executeCommand` through Spring AI function callbacks
- command execution is simulated in memory
- each attempt is scored
- a final summary is printed after all iterations complete

### 4. Inspect outputs

Local outputs:

- console logs with case progress and final summary
- `logs/benchmark-run-<runId>.jsonl` with structured event data for each benchmark run
- `logs/benchmark-run-<runId>.md` with a human-readable per-case report
- `logs/benchmark-run-<runId>.csv` with one summary row per case for spreadsheet comparison

The JSONL file remains the raw audit stream. The Markdown report is the primary file for humans, and the CSV is the compact comparison artifact.

The JSONL file records:

- benchmark run start and completion
- case metadata, expected state, and trap details
- full rendered system and user prompts for each attempt
- state inspections and command execution results
- execution results, state snapshots, attempt feedback, and final case scores

Useful local analysis commands:

```bash
rg '"eventType":"case_completed"' logs/benchmark-run-*.jsonl
rg '"eventType":"attempt_completed"' logs/benchmark-run-*.jsonl
rg '"success":false' logs/benchmark-run-*.jsonl
```

## Useful benchmark dimensions

If you want to compare model behavior systematically, the most meaningful knobs are:

- documentation quality
- distractor count
- retry budget
- execution budget per attempt
- trap command enabled or disabled
- domain selection
- model choice

## Testing

Run the test suite with:

```bash
mvn test
```

## Notes

- The application is a command-line benchmark runner, not a REST service.
- Tool execution is simulated in memory. The benchmark does not invoke external proprietary CLIs.
- Prompt templates are externalized under `src/main/resources/prompts`, and guardrail text is appended through a Spring AI advisor.
- The current runtime focuses on local JSONL logging. Spring AI Observation instrumentation is not wired in yet.
