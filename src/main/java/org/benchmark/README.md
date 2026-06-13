This project benchmarks LLM agents on single-step tool selection under degraded documentation.

Core packages:
- `org.benchmark.app`: entry point and case execution
- `org.benchmark.config`: Spring configuration properties and infrastructure beans
- `org.benchmark.exec`: simulator, state management, and effect application
- `org.benchmark.gen`: synthetic tool, documentation, and user-query generation
- `org.benchmark.llm`: model client (`LlmClient`)
- `org.benchmark.tools.server`: benchmark tool service (discovery + execution)
- `org.benchmark.tools.runtime`: runtime tool callbacks bound to generated cases
- `org.benchmark.model.enums`: enum types (`Domain`, `DocumentComplexity`, `EffectOp`)
- `org.benchmark.model.objects`: immutable generated tool specifications
- `org.benchmark.utils`: utility classes

Main flow:
1. Generate single-step benchmark cases (`BenchmarkCaseGenerator`).
2. Build degraded documentation bundles for the target tool and distractors (`DocumentationGenerator`).
3. Expose discovery utilities and executable benchmark tools through Spring AI tool callbacks.
4. Let the model inspect documentation, choose a tool, and execute one command with an optional flag.
5. Run the command in the in-memory simulator and apply its effects to session state.
6. Score tool choice, command choice, option choice, state accuracy, efficiency, and recovery.

Current entry point:
- `org.benchmark.app.BenchmarkRunner`
