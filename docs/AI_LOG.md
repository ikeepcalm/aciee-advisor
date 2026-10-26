# AI Usage Log

How AI tools were used in this project, and every AI error caught and corrected. Write each entry when the error is found, not afterwards.

## Entry format

| Field | Content |
|---|---|
| Date | YYYY-MM-DD |
| Tool | assistant / model |
| Intent | what I asked for |
| Error | what was wrong |
| Detected by | execution plan, test, docs, review… |
| Fix | what I changed |
| Commit | hash |

## Entries

**2026-10-26.** Claude Code (Opus 5.5) scaffolded `backend/` and `frontend/`, settled the stack (ADR-013) and drafted `DATABASE.md` with ADR-014–020. A second agent (Fable 5.1), asked to argue against the design, reviewed it; fixes in ADR-016, 019–023.

| Date | Tool | Intent | Error | Detected by | Fix | Commit |
|---|---|---|---|---|---|---|
| 2026-10-26 | Claude Opus 5.5 | DB design | Role grants left the anon pool unable to read `core`, so no page could load | review agent | Grant matrix, DATABASE.md §2 | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | Issuance keyed per semester: late-eligible offerings and lost responses lost their tokens | review agent | Per-offering issuance, stored blind signatures (ADR-021) | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | `HAVING count >= k` hid the count the "N more ratings" UI needs | review agent | MVs keep `n`, NULL averages below k (ADR-022) | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | Anonymity claim ignored xid/WAL ordering and single-student offerings | review agent | Residual risks and claim rewritten, ARCHITECTURE §3 | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | Recursive CTE on `replaced_by_id` had no cycle guard | review agent | `CYCLE` clause, DATABASE.md §8 | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | DEFAULT audit partition blocks adding new months; no maintenance | review agent | No DEFAULT, partition functions (ADR-023) | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | `FOR UPDATE` conflicts with FK `KEY SHARE` locks | review agent | `FOR NO KEY UPDATE`, DATABASE.md §11 | — |
| 2026-10-26 | Claude Opus 5.5 | DB design | Missing `teaching_assignment(offering_id)` index; extended stats on tiny tables | review agent | Lookup-first UQs; stats moved to large tables | — |
