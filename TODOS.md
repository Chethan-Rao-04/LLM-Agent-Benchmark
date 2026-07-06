

TODOS for the report-

1. Explain why we did not choose MCP
2. Start with the Methodology
3. Explain Spring AI and the features we added.


1. Fix Option Eval- options are not being evaluated correctly
3. Imrpove query, commands, scenarios, and scoring - Tool descriptions are very simple and straightforward and hints the LLM largely. 
   Queries are simple and similar to tool descriptions, options are less and not evaluated correclty, and the  --required is confusing.
   Role of semantic decoys

9. Random Distractors vs nomral tools VS SEMANTIC DECOYS, what is the diff
10. LLM Must give reasoning for each tool call, stored as Assistant message

Remaining from static/dynamic catalog vertical slice-

1. Update README/docs so they describe tool-catalog.yaml as the default generator path, not scenarios.yaml.
2. Add an explicit prompt-leakage test that confirms the hidden full catalog is never rendered into the model prompt.
3. Add a prompt-size/shape check for the default 10 exposed tools setup.
4. Add stronger semantic-decoy validation:
   - decoys come from configured neighbor families
   - decoys remain plausible but do not satisfy the target final state
   - at least one curated sample per family is reviewed
5. Add generator smoke tests across all document complexity modes for catalog-generated cases.
6. Consider logging toolFamilyId and workflowId in case_started/case_completed events for easier audit.
7. Decide whether legacy scenario loader/resources stay as migration fallback or get removed after catalog tests are stable.





Next steps -  Multi-tool workflow support
              - Add a tool role/target index to workflow steps.
              - Generate multiple required target tools for one case.
              - Track expected final state per required tool.
              - Update scoring to validate all required target tool states.
              RAG

Final Eval-


5. COmpare RAG vs ICL
6. COmpare ICL in diff docs complexity
7. compare ICL for diff models
8. in the above three, u can club somtimes


ISSUES FOUND 06.07

1. Autonomous Recoveries metric wrong - it should count only when the agent recovers from a failure, not when all calls are SUCCESS.
2. What is the difference bw candidate tools and distractor tools -
   Example  - ´´´ 
   "candidateTools":["MAN-ARC-229","MAN-ARC-411","MAN-THE-594","MAN-BUS-879","MAN-POW-796","MAN-LOA-207","MAN-COO-186","MAN-LOA-643","MAN-THE-725","MAN-COO-682"],
   "semanticDecoys":["MAN-ARC-411"],
   "targetLikeWrongTools":[{"wrongTool":"MAN-ARC-411","targetTool":"MAN-ARC-229"}],
   "randomDistractors":["MAN-THE-594","MAN-BUS-879","MAN-POW-796","MAN-LOA-207","MAN-COO-186","MAN-LOA-643","MAN-THE-725","MAN-COO-682"]
   ´´´
3.   remove rejectedCommands , not needed
4. result: SUCCESS even when wrong tool and command is run
Case 3
sessionId: d5403cae-79e6-47fc-8fc4-9e4ec64b042a
query: Bring the affected compression workflow back to a verified state.
targetTool: MAN-CLO-438
scenario: seal_integrity_recovery
targetSteps:
  - isp_mld{options=[--force]}
  - aln_fix{options=[--channel-1]}
  - clp_asm{options=[--circuit-east]}
  - vfy_btc{options=[--force]}
availableTools: 12
targetLikeWrongTools:
  - MAN-CLO-438 -> MAN-CLO-258
randomDistractors: 10

Attempt 1 completed
latencyMs: 3547
tokens: 5954
executions:
1. result: SUCCESS
   tool: MAN-CLO-258
   command: isp_fix
   option: --force
   message: OK: isp_fix
rejectedCommands:
  none
