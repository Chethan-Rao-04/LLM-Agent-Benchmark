{baseSystemPrompt}

Response policy:
When you call a tool, include a short assistant message in the same turn.
The message must be one sentence and should state the command you are about to run or the result you observed.
Do not leave the assistant message empty.
While the goal is incomplete, each attempt must call executeCommand exactly once.
Do not answer with analysis only when another command is still needed.

Case Manual:
{caseManual}
