This project consists of a benchmark for evaluating llm-based agents as guidance for operating proprietary CLI-tools.

It uses Java and Spring Boot along with tools like `Picoli` (CLI support for Java ) , `LangChain4j` for integration support , and `Spring AI`.


Proposed Components for the Benchmark
1. The data model
2. Tool Factory
2. A Documentation generator( generates tool documentation of varying complexity/ quality of deterioration)
3. LLM (small sized and open source)
4. Evaluation toolkit


1. The Data Model
- Tool Specification
- Tool Complexity
- Command Dict
- Command Spec
- Argument Spec
- CommandEffect  (Pending)
- CommandPreConditions (Pending)
- Tool State Memory (Pending)
- Domain (new)
- Tool Description Generator (in Process)







-TODO
Improve  state, effects, arguments  and precondition, incommplete implemetaion )also foe eval
SOmtines two tools are having same commands, fix this pr add a way to check after llm responds 
Try to make it converational like a chatbot involving LLM-simulated user