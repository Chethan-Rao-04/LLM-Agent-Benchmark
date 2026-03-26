This project benchmarks LLM agents for operating proprietary industrial-style CLI tools.

Core packages:
- `org.benchmark.app`: entry point (`BenchmarkRunner`)
- `org.benchmark.gen`: synthetic tool + documentation + user-query generation
- `org.benchmark.exec`: simulator, precondition checks, and state updates
- `org.benchmark.llm`: model client (`LlmClient`)
- `org.benchmark.mcp.client` / `org.benchmark.mcp.server`: loopback MCP wiring
- `org.benchmark.mcp.runtime`: session registry, execution records, and generated benchmark-tool callbacks
- `org.benchmark.utils`: unified event logging with optional CSV export
- `org.benchmark.model.enums`: enum types (`Domain`, `DocumentComplexity`, `ConditionOp`, `EffectOp`)

Main flow:
1. Generate benchmark cases (`BenchmarkCaseGenerator`).
2. Build tool documentation with distractors (`DocumentationGenerator`).
3. Expose documentation/state utilities through loopback MCP and generated benchmark tools through tool callbacks.
4. Let the model inspect documentation/state and execute one benchmark-tool command at a time.
5. Execute commands in the in-memory CLI simulator with precondition validation.
6. Apply command effects (`CommandEffectApplier`) to session state and log results through `RunEventLogger` (JSONL plus optional CSV export).

Current entry point:
- `org.benchmark.app.BenchmarkRunner`
