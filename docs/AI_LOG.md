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
| 2026-10-31 | Claude Opus 5.5 | Curriculum seed | The AIA 2025 plan lists a course's project as a second row with the same name; both mapped to one subject and broke UQ(plan, subject) | test (Liquibase at startup) | Rows with the same name in one semester merge, adding their hours | — |
| 2026-10-31 | Claude Opus 5.5 | Rollover test | Asserted on `membership_request` through the RLS-bound test connection, which sees no rows without `app.user_id` | test | Read the requests through the API as admin | — |
| 2026-10-31 | Claude Opus 5.5 | Fixtures | Left `spring.liquibase.contexts` empty by default; Liquibase then runs every context, so production would have loaded fixtures | design review | Default context `prod` | — |
| 2026-10-31 | Claude Opus 5.5 | Rollover | Leadership, roster and the size cap looked only at memberships active today, so claims on a rolled-over group could not be approved before the year started and its cap never held | code-review agent | Current-or-upcoming memberships (`&& daterange(current_date, NULL)`), `leads_group` in changeset 012 | — |
| 2026-10-31 | Claude Opus 5.5 | Teaching mapping | Listing slots per subgroup hid a group-wide assignment once subgroups existed, so a second teacher could overlap it | code-review agent | A "Toată grupa" slot is always listed | — |
| 2026-10-31 | Claude Opus 5.5 | Professor erasure | Archiving anonymised the professor row but the audit log kept the old name | code-review agent | `core.archive_professor` also strips the name and slug from that professor's audit rows | — |
| 2026-10-31 | Claude Opus 5.5 | Groups | Joining by invite did not refuse current members or ended years, wasting invite uses on requests that could never be approved | code-review agent | `already_member` / `year_ended` before the request | — |

**2026-11-07.** Claude Code (Opus 5.5) implemented Stage 5 reports (ADR-028).

| Date | Tool | Intent | Error | Detected by | Fix | Commit |
|---|---|---|---|---|---|---|
| 2026-11-07 | Claude Opus 5.5 | Report test | Asserted that the fixtures contain locked pairs; every fixture pair has n ≥ 300, so the test depended on data, not code | test | The test checks the filter (locked rows ⊇ unlocked rows, unlocked rows have n ≥ k) | — |
| 2026-11-07 | Claude Opus 5.5 | View design | `anon.published_post` (written in W3) used `DISTINCT ON`, so the professor page took 4.3 s on full data, near the 5 s timeout | `EXPLAIN ANALYZE` on fixtures-large (Stage 4) | `LATERAL … LIMIT 1` + `OFFSET 0` in the k check, changeset 018 (ADR-029) | — |
| 2026-11-07 | Claude Opus 5.5 | Stage 4 script | The first CTE demo joined `published_post`, whose cost (2.2 s) hid the CTE difference being shown | reading the capture | Second branch reads assignments instead; 22.8 vs 11.2 ms | — |
| 2026-11-07 | Claude Opus 5.5 | Stage 4 script | The audit-trigger demo targeted `core.user_account`, which has no audit trigger | script error | Uses `core.teaching_assignment`, plus a no-trigger baseline | — |
| 2026-11-07 | Claude Opus 5.5 | Microsoft sign-in test | Seeded `email_verification` with `expires_at`, a column changeset 011 replaced with `sent_at` | test | Inserts only `user_id` and `code_hash` | — |
| 2026-11-23 | Claude Opus 5.5 | Timetable teacher import | The first name matcher paired every word with every other, so `George Andrei Marin` swallowed `G. Mocanu` and `Mihaela Andrei` | ran it on the 233 real teacher strings from prod | Full names match only by word-set containment; short forms by surname + initial (ADR-031) | — |
| 2026-11-23 | Claude Opus 5.5 | Timetable teacher import | Changeset 022 created the `Nespecificat` department under faculty ACIEE, which the seed creates only after the schema changesets on a fresh database | integration test | The department is created on first use in `ProfessorDirectory` | — |
| 2026-11-23 | Claude Opus 5.5 | Admin professor table | Added `name_confirmed` as a required admin column, which broke creating a professor without it | `AdminTablesTests` | Derived column set to `true` on every admin write | — |
| 2026-11-23 | Claude Opus 5.5 | Show recording | Stubbed the footer stats in the show's canned responses, but the footer fetches them before the show layer registers them, so the first recording showed the local fixture counts as real numbers | contact sheet of the recording | Stubbed the request in the recorder (Playwright `route`) and re-recorded | — |
| 2026-11-23 | Claude Opus 5.5 | Timetable group sync | Treated each timetable column (`22C31A`, `22C31B`) as its own group, so one group had two leader positions; the letters are subgroups | the owner, on prod | Group + subgroup split, importer per subgroup, changeset 023 recreates the groups (ADR-034) | — |
