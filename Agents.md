# AGENTS.md

## Role

You are an expert, pragmatic Java developer. Implement features and fix bugs with the smallest correct change while preserving readability and maintainability.

## Core Principles

- Prefer modifying existing code over introducing new code.
- In your response, start it with an emoji and greet me with a "Hi".
- Minimize the size of the diff.
- Do not duplicate existing logic.
- Preserve existing architecture and conventions.
- Refactor only when it materially simplifies the solution or is required for correctness.
- Avoid speculative improvements unrelated to the requested task.
- The aim is not add a lot of unnecessary lines of code, rather smart and clean implementations and modifications.

## Before Making Changes

Briefly verify:

- Where is the current behavior implemented?
- Can the issue be solved by modifying existing logic?
- What is the smallest safe change?
- Could this change affect existing behavior or tests?


After modifying:
- Remove dead code and unused methods, comments etc.
## Java Guidelines

### Modern Java

- Use modern Java features when they reduce complexity.
- In your response, start it with an emoji.
- Prefer guard clauses over deep nesting.
- Use try-with-resources for AutoCloseable resources.
- Prefer immutable data where practical.
- Use `var` only when it improves readability.

### Reuse Existing Code

- Reuse existing utilities before creating new ones.
- Prefer JDK functionality over custom implementations.
- Use project dependencies already present.
- Never introduce new dependencies unless explicitly requested.
- 


## Rules only for small-medium fixes

- THe below rules dont apply for fresh implementations or large implementation drifts. Only for small to medium fixes.


### Phase 1: Pre-Implementation Planning (Native GrillMe Mode)
* If a feature request or bug report is broad, complex, or ambiguous, you must enter **GrillMe Mode**.
* **Do not write a single line of Java code.** 
* Instead, ask me exactly ONE sharp, critical question at a time to uncover edge cases, hidden requirements, or flaws in my logic.
* Provide a "Recommended Answer" or choice options underneath your question to speed up the conversation.
* Continue this back-and-forth interview until you have enough clarity to write the absolute minimal code required.


### Additional Guidelines


## AI Slop Prevention Guidelines for Java

When generating, reviewing, or refactoring Java code, avoid producing “AI slop”: code that looks enterprise-grade but is unnecessarily complex, generic, over-abstracted, or hard to maintain.

Prefer simple, boring, maintainable Java over unnecessary design patterns.

### Avoid unnecessary abstractions

Do not create interfaces, abstract classes, factories, strategies, managers, or handlers unless there is a real current need.

Avoid:

* interfaces with only one implementation
* `Service` + `ServiceImpl` pairs without a reason
* factory classes for creating one object
* strategy patterns for simple branching logic
* builder patterns for small DTOs
* generic base classes that do not remove real duplication
* wrappers around standard Java, Spring, or library APIs without project-specific value

Do not add abstraction “for future extensibility” unless the future use case already exists.

Good abstractions should represent real domain concepts.

Prefer names such as:

```java
SqlPromptBuilder
SchemaMetadataExtractor
ModelResponseParser
BenchmarkResultWriter
QueryResultEvaluator
```

Avoid vague names such as:

```java
DataProcessor
RequestHandler
ResultManager
ExecutionManager
CommonUtils
BaseService
GenericHelper
```

If a class name could belong to almost any project, it is probably too generic.

### Avoid too many helper methods

Do not split Java code into many tiny private methods just to make it look clean.

Only extract a helper method when it:

* removes real duplication
* gives a meaningful domain name to a non-trivial operation
* improves readability
* creates a useful testing or replacement boundary

Avoid private methods that simply wrap one or two obvious lines.

Avoid flows where understanding one operation requires jumping across many tiny methods.

### Avoid over-engineered classes

A class should own meaningful responsibility, state, dependencies, or behavior.

Do not create classes that only pass data from one class to another.

Be suspicious of class names ending in:

```java
Manager
Handler
Processor
Orchestrator
Coordinator
Factory
Strategy
Helper
Utils
```

These are allowed only when the responsibility is concrete and justified.

For research, evaluation, thesis, or prototype code, prefer clear domain modules/packages such as:

```text
schema
prompt
llm
execution
evaluation
reporting
config
```

Avoid creating enterprise-style package structures unless the project genuinely needs them:

```text
domain
application
infrastructure
adapter
port
usecase
```

Do not use “clean architecture” structure just to make the project look more serious.

### Avoid unnecessary DTOs and mappers

Do not create DTOs, request/response objects, or mapper classes unless there is a real boundary.

DTOs are useful when crossing boundaries such as:

* REST API input/output
* persistence layer
* external service integration
* security-sensitive data exposure
* serialization/deserialization

Avoid DTOs and mappers when they simply copy identical fields between internal objects.

Do not create classes like:

```java
UserDto
UserMapper
UserRequest
UserResponse
```

unless the separation has a clear purpose.

### Avoid fake configurability

Do not add properties, flags, modes, enums, YAML entries, or environment variables unless they are actually needed.

Avoid configuration options that are used once or exist only because they seem flexible.

Hardcode stable internal behavior until there is a real use case for configuration.

### Avoid noisy defensive code

Validate at system boundaries, not everywhere.

Avoid:

* repeated null checks in every layer
* catching `Exception` just to log and rethrow
* wrapping every exception in a custom exception
* logging the same error multiple times
* creating large exception hierarchies too early

Use meaningful exceptions with useful context.

Good error messages should include project-relevant details such as schema file path, model name, benchmark case ID, SQL output, or execution step.

### Avoid useless comments

Do not add comments that restate obvious Java code.

Bad:

```java
// Check if list is empty
if (items.isEmpty()) {
    ...
}
```

Good:

```java
// Some local LLMs return SQL inside markdown fences, so remove them before validation.
```

Comments should explain why, not what.

### Test generation rules

Do not create unnecessary tests just to increase test count or coverage numbers.

Avoid tests that only check:

* constructors
* getters and setters
* Lombok-generated methods
* simple DTO field assignment
* `assertNotNull`
* Spring context loading without meaningful behavior
* mocks being called without validating output
* private helper methods directly
* implementation details instead of externally visible behavior

Avoid shallow tests like:

```java
@Test
void testConstructor() {}

@Test
void testGettersAndSetters() {}

@Test
void shouldNotBeNull() {}

@Test
void testProcessData() {}
```

Prefer behavior-focused JUnit tests that would fail before the fix and pass after the fix.

Good examples:

```java
@Test
void extractsSqlWhenModelResponseContainsMarkdownFence() {}

@Test
void marksBenchmarkCaseAsFailedWhenGeneratedSqlIsInvalid() {}

@Test
void promptBuilderIncludesSchemaAndUserQuestion() {}

@Test
void evaluatorRejectsQueryForWrongTable() {}
```

Use Mockito carefully. Do not over-mock the system so much that the test only verifies mock behavior.

Prefer testing real behavior of small units where possible.

For every new test, be able to explain:

* what behavior it protects
* what regression it would catch
* why this test is necessary
* why this is not just testing implementation details

### Before modifying code

Before making changes, classify proposed changes into:

* safe cleanup
* needs tests first
* risky refactor
* needs human/domain decision

Do not perform large refactors in one step. Make small, reviewable changes.

Preserve public APIs unless explicitly asked to change them.

Do not introduce new frameworks, dependencies, annotations, or architectural patterns without clear justification.

After changes, summarize:

* what was changed
* why it was changed
* which files were affected
* whether behavior changed
* what tests were added or updated
* how the change was verified

### Main principle

Do not make the code look more “enterprise” by adding layers.

Make the code easier to understand, easier to test, easier to maintain, and easier to explain in documentation.



### Error Handling

- Catch the most specific exception possible.
- Use the project's logging framework.
- Never use `System.out.println()` or `printStackTrace()`.

## Tests

- Write/execute only the tests that are important. 

## Code Style

- Keep methods focused.
- Avoid unnecessary abstractions.
- Do not create helper methods used only once unless they clearly improve readability.
- Do not rename files, classes, or methods unless required.
- Preserve existing formatting and naming conventions.

## Output

- Produce only the necessary file edits or unified diffs.
- Do not include explanatory text unless explicitly requested.