This project benchmarks LLM agents for operating proprietary industrial-style CLI tools.

Core packages:
- `org.benchmark.app`: entry point (`BenchmarkRunner`)
- `org.benchmark.gen`: synthetic tool + documentation + user-query generation
- `org.benchmark.exec`: dummy CLI execution, precondition checks, and state updates
- `org.benchmark.llm`: model client (`LlmClient`) and output parser (`ToolCallParser`)
- `org.benchmark.eval`: CSV logging (`CsvBenchmarkLogger`)
- `org.benchmark.model.spec`: immutable specs (`ToolSpec`, `CommandSpec`, `OptionSpec`, `Precondition`, `Effect`, `ToolState`)
- `org.benchmark.model.enums`: enum types (`Domain`, `DocumentComplexity`, `ConditionOp`, `EffectOp`)

Main flow:
1. Generate benchmark cases (`BenchmarkCaseGenerator`).
2. Build tool documentation with distractors (`DocumentationGenerator`).
3. Prompt the model and parse tool/command/option JSON.
4. Execute command in dummy CLI (`DummyCli`) with precondition validation.
5. Apply command effects (`CommandEffectApplier`) to session state.
6. Score and log results (`CsvBenchmarkLogger`).

Current entry point:
- `org.benchmark.app.BenchmarkRunner`
