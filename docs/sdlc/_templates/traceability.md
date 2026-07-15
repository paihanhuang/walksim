# Traceability — <feature>

Date: <YYYY-MM-DD>   ·   Owner: orchestrator

Living matrix linking each requirement to its test(s) and proof(s). Must be complete before Gate 3.

| Req (AC) | Task | Test(s) | Proof file | Aspect | Status |
|---|---|---|---|---|---|
| AC-1 | T1 | test_login_rejects_bad_pw | auth_unit_function_pass_<sha>.xml | function | pass |
| AC-2 | T2 | bench_search_latency | auth_system_quality_pass_after.json | quality | pass |

Every AC must trace to at least one passing test + proof. Unmapped ACs block final acceptance.
