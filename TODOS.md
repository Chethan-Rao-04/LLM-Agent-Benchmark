1. Only 1 command should be executed per attempt, and say give a total of 7 attempts, 
so the recovery and retry can be done in a max of 4 steps.
2. Test for other document complexities.
3. Test with RAG
4. Improve logging, the Logs should show each command executed in each attempt


TODOS for the report-

1. Explain why we did not choose MCP
2. Start with the Methodology


============================================================
==================== BENCHMARK SUMMARY ====================
Model:                gpt-oss:20b-cloud
Documentation Type:   CLEAN
============================================================


============================================================
Run 1: Please verify the valve.
tool=MAN-SENSOR-960 | scenario=diagnose_repair_verify  | steps=[diag_vlv{options=[]} -> rpr_vlv{options=[--retry]} -> vfy_vlv{options=[--interactive, --debug]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=18541, tokens=51621
[EXEC 1.1] SUCCESS tool=MAN-SENSOR-960 command=diag_vlv option=<none> message=OK: diag_vlv
[EXEC 1.2] SUCCESS tool=MAN-SENSOR-960 command=rpr_vlv option=<none> message=OK: rpr_vlv
[EXEC 1.3] SUCCESS tool=MAN-SENSOR-960 command=vfy_vlv option=--interactive message=OK: vfy_vlv
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 2: Please verify the valve.
tool=MAN-JIG-668 | scenario=diagnose_repair_verify  | steps=[diag_vlv{options=[--interactive]} -> rpr_vlv{options=[]} -> vfy_vlv{options=[]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=18170, tokens=45180
[EXEC 1.1] SUCCESS tool=MAN-JIG-668 command=diag_vlv option=<none> message=OK: diag_vlv
[EXEC 1.2] SUCCESS tool=MAN-JIG-668 command=rpr_vlv option=<none> message=OK: rpr_vlv
[EXEC 1.3] SUCCESS tool=MAN-JIG-668 command=vfy_vlv option=<none> message=OK: vfy_vlv
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 3: Please deploy the configuration.
tool=MAN-COMPRESSOR-453 | scenario=auth_provision_deploy  | steps=[auth_hmi{options=[--verbose, --dry-run]} -> prov_cfgr{options=[--interactive, --retry]} -> dpl_cfgr{options=[]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=15492, tokens=52508
[EXEC 1.1] SUCCESS tool=MAN-COMPRESSOR-453 command=auth_hmi option=<none> message=OK: auth_hmi
[EXEC 1.2] SUCCESS tool=MAN-COMPRESSOR-453 command=prov_cfgr option=<none> message=OK: prov_cfgr
[EXEC 1.3] SUCCESS tool=MAN-COMPRESSOR-453 command=dpl_cfgr option=<none> message=OK: dpl_cfgr
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 4: Please verify the turbine.
tool=MAN-COMPRESSOR-348 | scenario=diagnose_repair_verify  | steps=[diag_trb{options=[--simulate, --retry]} -> rpr_trb{options=[--output, --interactive, --debug]} -> vfy_trb{options=[--debug, --simulate, --version]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=17600, tokens=53507
[EXEC 1.1] SUCCESS tool=MAN-COMPRESSOR-348 command=diag_trb option=<none> message=OK: diag_trb
[EXEC 1.2] SUCCESS tool=MAN-COMPRESSOR-348 command=rpr_trb option=--debug message=OK: rpr_trb
[EXEC 1.3] SUCCESS tool=MAN-COMPRESSOR-348 command=vfy_trb option=<none> message=OK: vfy_trb
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 5: Help me deploy the program.
tool=MAN-PROCESS-484 | scenario=auth_provision_deploy  | steps=[auth_ctrl{options=[--version, --quiet, --dry-run]} -> prov_prg_n{options=[--timeout]} -> dpl_prg_n{options=[--quiet, --verbose, --confirm]}] | tools=7 | decoys=2 | distractors=4
[TRAP] cmd=auth_ctrl | claims: {auth_token ASSIGN=valid} | actually: {auth_token ASSIGN=valid_UNSTABLE} | recovery=recalibrate_ctrl
============================================================

[ATTEMPT 1] latency=29359, tokens=66789
[EXEC 1.1] SUCCESS tool=MAN-MACHINE-371 command=auth_scd option=<none> message=OK: auth_scd
[EXEC 1.2] ERROR tool=MAN-OPERATOR-977 command=prov_cfgr option=<none> message=Precondition not met for command: prov_cfgr. Required: {auth_token=valid}, actual: {auth_token=null}
[EXEC 1.3] SUCCESS tool=MAN-PROCESS-484 command=auth_ctrl option=<none> message=OK: auth_ctrl
[EXEC 1.4] SUCCESS tool=MAN-PROCESS-484 command=recalibrate_ctrl option=<none> message=OK: recalibrate_ctrl
[EXEC 1.5] SUCCESS tool=MAN-PROCESS-484 command=prov_prg_n option=<none> message=OK: prov_prg_n
[EXEC 1.6] SUCCESS tool=MAN-PROCESS-484 command=dpl_prg_n option=--quiet message=OK: dpl_prg_n
[CASE RESULT]
Status:        SUCCESS
Recovery:      true
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    6 total
Scores:        composite=0.906, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=0.77, precision=0.67

============================================================
Run 6: Help me verify the valve.
tool=MAN-COMPRESSOR-731 | scenario=diagnose_repair_verify  | steps=[diag_vlv{options=[]} -> rpr_vlv{options=[--dry-run, --force, --help]} -> vfy_vlv{options=[--quiet]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=177035, tokens=58110
[EXEC 1.1] ERROR tool=MAN-COMPRESSOR-731 command=diag_vlv option=none message=Unknown option none for command diag_vlv
[EXEC 1.2] ERROR tool=MAN-COMPRESSOR-731 command=diag_vlv option=- message=Unknown option - for command diag_vlv
[EXEC 1.3] SUCCESS tool=MAN-COMPRESSOR-731 command=diag_vlv option=<none> message=OK: diag_vlv
[EXEC 1.4] SUCCESS tool=MAN-COMPRESSOR-731 command=rpr_vlv option=<none> message=OK: rpr_vlv
[EXEC 1.5] SUCCESS tool=MAN-COMPRESSOR-731 command=vfy_vlv option=<none> message=OK: vfy_vlv
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    5 total
Scores:        composite=0.917, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=0.50, precision=1.00

============================================================
Run 7: Help me verify the turbine.
tool=MAN-OPERATOR-486 | scenario=diagnose_repair_verify  | steps=[diag_trb{options=[]} -> rpr_trb{options=[--dry-run, --output, --debug]} -> vfy_trb{options=[--force, --version]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=38400, tokens=51287
[EXEC 1.1] SUCCESS tool=MAN-OPERATOR-486 command=diag_trb option=<none> message=OK: diag_trb
[EXEC 1.2] SUCCESS tool=MAN-OPERATOR-486 command=rpr_trb option=<none> message=OK: rpr_trb
[EXEC 1.3] SUCCESS tool=MAN-OPERATOR-486 command=vfy_trb option=--force message=OK: vfy_trb
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 8: Can you deploy the configuration?
tool=MAN-ALARM-761 | scenario=auth_provision_deploy  | steps=[auth_plc{options=[--retry, --dry-run]} -> prov_cfgr{options=[--verbose]} -> dpl_cfgr{options=[--interactive, --debug]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=18355, tokens=50333
[EXEC 1.1] SUCCESS tool=MAN-ALARM-761 command=auth_plc option=<none> message=OK: auth_plc
[EXEC 1.2] SUCCESS tool=MAN-ALARM-761 command=prov_cfgr option=--verbose message=OK: prov_cfgr
[EXEC 1.3] SUCCESS tool=MAN-ALARM-761 command=dpl_cfgr option=--interactive message=OK: dpl_cfgr
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 9: Can you deploy the program?
tool=MAN-MATERIAL-877 | scenario=auth_provision_deploy  | steps=[auth_hmi{options=[--retry, --debug]} -> prov_prg_n{options=[]} -> dpl_prg_n{options=[]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=33232, tokens=59075
[EXEC 1.1] SUCCESS tool=MAN-MATERIAL-877 command=auth_hmi option=<none> message=OK: auth_hmi
[EXEC 1.2] SUCCESS tool=MAN-MATERIAL-877 command=prov_prg_n option=<none> message=OK: prov_prg_n
[EXEC 1.3] SUCCESS tool=MAN-MATERIAL-877 command=dpl_prg_n option=<none> message=OK: dpl_prg_n
[EXEC 1.4] ERROR tool=MAN-PARTS-705 command=auth_ctrl option=<none> message=REJECTED: Scenario already completed. No further commands are allowed for this session.
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    4 total
Scores:        composite=0.903, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=0.67, precision=0.75

============================================================
Run 10: Help me calibrate the turbine.
tool=MAN-PROCESS-755 | scenario=init_configure_execute  | steps=[ini_sys{options=[]} -> enb_si{options=[--interactive]} -> cal_trb{options=[]}] | tools=7 | decoys=2 | distractors=4
[TRAP] cmd=ini_sys | claims: {system_status ASSIGN=RUNNING} | actually: {system_status ASSIGN=RUNNING_UNSTABLE} | recovery=stabilize_sys
============================================================

[ATTEMPT 1] latency=29105, tokens=55258
[EXEC 1.1] SUCCESS tool=MAN-PROCESS-755 command=ini_sys option=<none> message=OK: ini_sys
[EXEC 1.2] SUCCESS tool=MAN-PROCESS-755 command=stabilize_sys option=<none> message=OK: stabilize_sys
[EXEC 1.3] SUCCESS tool=MAN-PROCESS-755 command=enb_si option=--interactive message=OK: enb_si
[EXEC 1.4] SUCCESS tool=MAN-PROCESS-755 command=cal_trb option=<none> message=OK: cal_trb
[CASE RESULT]
Status:        SUCCESS
Recovery:      true
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    4 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 11: Can you measure the turbine?
tool=MAN-PARTS-734 | scenario=init_configure_execute  | steps=[ini_sys{options=[--help, --timeout]} -> enb_si{options=[]} -> msr_trb{options=[--quiet]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=22735, tokens=47045
[EXEC 1.1] SUCCESS tool=MAN-PARTS-734 command=ini_sys option=<none> message=OK: ini_sys
[EXEC 1.2] SUCCESS tool=MAN-PARTS-734 command=enb_si option=<none> message=OK: enb_si
[EXEC 1.3] SUCCESS tool=MAN-PARTS-734 command=msr_trb option=--quiet message=OK: msr_trb
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 12: Can you verify the valve?
tool=MAN-WORKSTATION-857 | scenario=diagnose_repair_verify  | steps=[diag_vlv{options=[--confirm, --version, --force]} -> rpr_vlv{options=[]} -> vfy_vlv{options=[--force, --debug, --quiet]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=18340, tokens=53351
[EXEC 1.1] SUCCESS tool=MAN-WORKSTATION-857 command=diag_vlv option=<none> message=OK: diag_vlv
[EXEC 1.2] SUCCESS tool=MAN-WORKSTATION-857 command=rpr_vlv option=<none> message=OK: rpr_vlv
[EXEC 1.3] SUCCESS tool=MAN-WORKSTATION-857 command=vfy_vlv option=<none> message=OK: vfy_vlv
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 13: Can you verify the conveyor?
tool=MAN-SENSOR-467 | scenario=diagnose_repair_verify  | steps=[diag_cnv{options=[--force, --output]} -> rpr_cnv{options=[--help]} -> vfy_cnv{options=[--quiet]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=16796, tokens=48180
[EXEC 1.1] SUCCESS tool=MAN-SENSOR-467 command=diag_cnv option=<none> message=OK: diag_cnv
[EXEC 1.2] SUCCESS tool=MAN-SENSOR-467 command=rpr_cnv option=<none> message=OK: rpr_cnv
[EXEC 1.3] SUCCESS tool=MAN-SENSOR-467 command=vfy_cnv option=--quiet message=OK: vfy_cnv
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 14: I need to verify the conveyor.
tool=MAN-DYE-956 | scenario=diagnose_repair_verify  | steps=[diag_cnv{options=[--output]} -> rpr_cnv{options=[--help, --interactive]} -> vfy_cnv{options=[--interactive]}] | tools=7 | decoys=2 | distractors=4
============================================================

[ATTEMPT 1] latency=15760, tokens=44103
[EXEC 1.1] SUCCESS tool=MAN-DYE-956 command=diag_cnv option=<none> message=OK: diag_cnv
[EXEC 1.2] SUCCESS tool=MAN-DYE-956 command=rpr_cnv option=--interactive message=OK: rpr_cnv
[EXEC 1.3] SUCCESS tool=MAN-DYE-956 command=vfy_cnv option=--interactive message=OK: vfy_cnv
[CASE RESULT]
Status:        SUCCESS
Recovery:      false
Attempts Used: 1/5
Discovery:     8 call(s)
Executions:    3 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

============================================================
Run 15: Help me deploy the configuration.
tool=MAN-COMPRESSOR-436 | scenario=auth_provision_deploy  | steps=[auth_ctrl{options=[]} -> prov_cfgr{options=[--interactive, --timeout]} -> dpl_cfgr{options=[--dry-run, --retry, --force]}] | tools=7 | decoys=2 | distractors=4
[TRAP] cmd=auth_ctrl | claims: {auth_token ASSIGN=valid} | actually: {auth_token ASSIGN=valid_PENDING} | recovery=stabilize_ctrl
============================================================

[ATTEMPT 1] latency=25622, tokens=59133
[EXEC 1.1] SUCCESS tool=MAN-COMPRESSOR-436 command=auth_ctrl option=<none> message=OK: auth_ctrl
[EXEC 1.2] SUCCESS tool=MAN-COMPRESSOR-436 command=stabilize_ctrl option=--retry message=OK: stabilize_ctrl
[EXEC 1.3] SUCCESS tool=MAN-COMPRESSOR-436 command=prov_cfgr option=--interactive message=OK: prov_cfgr
[EXEC 1.4] SUCCESS tool=MAN-COMPRESSOR-436 command=dpl_cfgr option=--force message=OK: dpl_cfgr
[CASE RESULT]
Status:        SUCCESS
Recovery:      true
Attempts Used: 1/5
Discovery:     9 call(s)
Executions:    4 total
Scores:        composite=1.000, tool=1.0, steps=1.00, ordering=1.00, state=1.00, efficiency=1.00, precision=1.00

==================== BENCHMARK SUMMARY ====================
Model:                gpt-oss:20b-cloud
Documentation Type:   CLEAN
Iterations:           15
Distractors / Case:   6
Max Retries / Case:   5
Successes:            15/15
Autonomous Recoveries: 3
Sessions Using Discovery:   15/15
Discovery Calls:      128
Benchmark Executions: 53
Average Composite:    0.982
Average Tool Select:  1.00
Average Step Compl:   1.00
Average Ordering:     1.00
Average State Acc:    1.00
Average Efficiency:   0.93
Average Cmd Precision:0.96
================================END===========================



============================================================
==================== BENCHMARK SUMMARY ====================
Model:                gpt-oss:20b-cloud
Documentation Type:   GIBBERISH_NOISE
Iterations:           20
Distractors / Case:   6
Max Retries / Case:   5
Successes:            19/20
Autonomous Recoveries: 5
Sessions Using Discovery:   20/20
Discovery Calls:      209
Benchmark Executions: 106
Average Composite:    0.914
Average Tool Select:  1.00
Average Step Compl:   0.97
Average Ordering:     0.97
Average State Acc:    0.98
Average Efficiency:   0.75
Average Cmd Precision:0.83


=================== BENCHMARK SUMMARY ====================
Model:                gpt-oss:20b-cloud
Documentation Type:   CONTEXTUAL_NOISE
Iterations:           20
Distractors / Case:   6
Max Retries / Case:   5
Successes:            19/20
Autonomous Recoveries: 5
Sessions Using Discovery:   20/20
Discovery Calls:      214
Benchmark Executions: 107
Average Composite:    0.889
Average Tool Select:  0.95
Average Step Compl:   0.95
Average Ordering:     0.95
Average State Acc:    0.95
Average Efficiency:   0.69
Average Cmd Precision:0.85
=================================================