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

**2026-10-28.** Claude Code (Opus 5.5) implemented W1: schema changesets, email-code authentication, auth screens; a subagent drafted `VIEWS.md`.

| Date | Tool | Intent | Error | Detected by | Fix | Commit |
|---|---|---|---|---|---|---|
| 2026-10-28 | Claude Opus 5.5 | DB design | Radar `n = count(*)` counted ratings, not students: a professor with lecture + lab reached k with fewer than k students | implementation review | `count(DISTINCT key_hash)`, DATABASE.md §8 | — |
| 2026-10-28 | Claude Opus 5.5 | DB design | Query catalogue missed the invite-use count, leaving `membership_request(invite_id)` unindexed | implementation review | Q17 + index | — |
| 2026-10-28 | Claude Opus 5.5 | Auth | Wrote the session context into `SecurityContextHolder` as well as the session; the thread-local leaked between MockMvc requests | test | Save to the session repository only | — |
| 2026-10-28 | Claude Opus 5.5 | Liquibase | `includeAll` tried to parse `fixtures/.gitkeep` | app start | Removed it; `errorIfMissingOrEmpty: false` | — |
| 2026-10-28 | Claude Opus 5.5 | Auth | Re-signing up replaced an unverified account's password, so an attacker could plant one and let the student verify it | code-review agent | Verify requires the password (ARCHITECTURE §2), test `reSigningUpCannotPlantAPasswordOnSomeoneElsesAccount` | — |
| 2026-10-28 | Claude Opus 5.5 | Auth | Disabled accounts could still verify; a quoted local part passed validation and hit the DB CHECK (500); mail sent inside the transaction | code-review agent | Disabled check in verify, pattern matches the CHECK, mail after commit | — |
| 2026-10-28 | Claude Opus 5.5 | DB design | Audit UPDATE joined old/new rows on `user_id` alone for composite-key tables, multiplying rows | code-review agent | Trigger pairs on the full primary key (changeset 011) | — |
| 2026-10-28 | Claude Opus 5.5 | DB design | Read `TG_ARGV[1]` as the first trigger argument; `TG_ARGV` is zero-based | test | 1-based slice `TG_ARGV[0:TG_NARGS - 1]` | — |

**2026-10-31.** Claude Code (Opus 5.5) implemented W2 access control and the group workflow.

| Date | Tool | Intent | Error | Detected by | Fix | Commit |
|---|---|---|---|---|---|---|
| 2026-10-31 | Claude Opus 5.5 | RLS | Setting `app.user_id` in the transaction manager read the security context, which loaded the JDBC session through the same manager and recursed until the pool was exhausted | test (pool timeout) | Separate `@SpringSessionTransactionManager` | — |
| 2026-10-31 | Claude Opus 5.5 | Groups | The invite cap counted `membership_request` rows under RLS, so a student saw only their own and the cap never held | design review | `SECURITY DEFINER` `core.invite_uses()` | — |
| 2026-10-31 | Claude Opus 5.5 | Groups | A student calling approve on their own request matched zero rows under RLS and got a silent success | design review | Leader check before the CTE; zero inserted rows → `already_decided` | — |
| 2026-10-31 | Claude Opus 5.5 | Curriculum seed | "Limba engleză" appears in years 1 and 2 of one plan; one subject per name broke UQ(plan, subject) | test (Liquibase at startup) | Same name twice in a plan becomes "(anul N)" | — |
| 2026-10-31 | Claude Opus 5.5 | Curriculum seed | The PDF library printed a hint to stdout, which landed in the generated SQL | review of the output | Parsing runs with stdout redirected to stderr | — |
