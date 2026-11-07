# Stage 4 evidence

Every number below comes from the captures in this folder, taken with `EXPLAIN (ANALYZE, BUFFERS)` on the `fixtures-large` data set (3.1M ratings, 1.45M submissions, 365k post versions, 5M audit rows, 230k teaching assignments) in the local PostgreSQL 18 container with default settings.

Reproduce:

```sh
createdb aciee_large   # then start the app once with LIQUIBASE_CONTEXTS=fixtures-large and DB_URL=.../aciee_large
loadtest/explain/run.sh                     # all nine scripts, overwrites the .txt captures here
loadtest/explain/run.sh 09-published-post.sql
```

Each script creates its demo-only indexes and drops them again (or works inside a transaction it rolls back), so the schema is unchanged afterwards. The captures run with `jit = off`, as the app roles do since changeset `018`.

## Query plans

| # | Case | Before | After | Capture |
|---|---|---|---|---|
| 1 | Radar for one assignment | no index: parallel seq scan of 3.1M rows, **39.3 ms** | B-tree on `rating(teaching_assignment_id)`: bitmap scan, **0.15 ms**, 26 MB. Covering `INCLUDE (a01..a14)` after `VACUUM`: index-only scan, 0 heap fetches, **0.057 ms**, 167 MB | `01-radar-index.txt` |
| 2 | Moderation queue (Q8) | no index: seq scan + top-N sort, **9.5 ms** | partial `WHERE status = 'pending'`: index-only scan, **0.010 ms**, **1.5 MB**. Full index on `(status, …)`: same plan, **20 MB**. Status as a bind parameter with a generic plan: the partial index is unusable, back to **10.0 ms** | `02-moderation-partial-index.txt` |
| 3 | What can I rate (Q5) | | membership lookup through the temporal key's GiST index (`group_membership_user_id_valid_during_key`). `NOT EXISTS` and `LEFT JOIN … IS NULL` give the same hash anti join (0.033 / 0.026 ms); `NOT IN` gives a hashed subplan filter (0.035 ms) and returns **0 rows instead of 2** as soon as the subquery yields one NULL | `03-rateable-offerings.txt` |
| 4 | Audit log (Q13, Q14) | | one row's history: one index probe per monthly partition, 0.5 ms. One week, paged: plan-time pruning (3 partitions removed), 0.040 ms; as a generic prepared plan: run-time pruning (15 removed), 0.024 ms. `created_at` index over 5M rows: **BRIN 384 kB vs B-tree 100 MB** | `04-audit-log.txt` |
| 4e | Audit trigger, 10k-row update | no trigger 67 ms; per-row trigger (10,000 calls) **203 ms** | statement-level trigger with transition tables (1 call) **183 ms**: trigger overhead 116 ms instead of 136 ms. On `UPDATE` the old/new pairing join eats most of the gain; inserts and deletes need no pairing | `04-audit-log.txt` |
| 5 | Radar, materialised vs live (Q4) | live from `anon.rating_v`: **92.5 ms** (parallel seq scan of all ratings) | `anon.prof_subject_radar` by its unique index: **0.018 ms**. One concurrent refresh of both views: 4.9 s, paid once an hour (ADR-027) instead of per page view | `05-materialized-radar.txt` |
| 6 | Correlated columns | without the statistic: estimated **1** row for `teaching_assignment_id = x AND offering_id = y` | with `rating_assignment_offering (dependencies)`: estimated **~20** (8 per worker), actual 28. `pg_stats_ext` shows assignment → offering at 1.0 | `06-extended-statistics.txt` |
| 7 | Pagination of the queue | `OFFSET 15361`: reads 15,381 index entries, **0.895 ms**, grows with the page number | keyset `(submitted_week, post_id, version_no) > (…)`: reads 20 entries, **0.016 ms** on every page | `07-keyset-pagination.txt` |
| 8 | Shared CTE on the professor page | `NOT MATERIALIZED`: the recursive root-subject chain runs in both branches, **22.8 ms** | `MATERIALIZED`: computed once, second read costs 0.003 ms, **11.2 ms** | `08-cte-materialized.txt` |
| 9 | Published comments (Q9) | `DISTINCT ON (p.id)` view (changeset `016`): every published version is built and sorted before the professor filter applies, **4,311 ms** (5 s `statement_timeout` for the app) | `LATERAL … ORDER BY version_no DESC LIMIT 1` (backward scan of the `post_version` PK) and the k check kept a per-row subplan with `OFFSET 0` (changeset `018`): **37 ms**. Same 268,518 rows either way | `09-published-post.txt` |
| 9c | JIT on request paths | with JIT, the same query: **253 ms**, of which ~215 ms compiling; the `/rate` list: 264 ms instead of 27 ms | `ALTER ROLE aciee_ident / aciee_anon SET jit = off` (changeset `018`); `aciee_report` keeps JIT for long report queries | `09-published-post.txt` |

Case 9 and 9c were found by these captures, not planned: the professor and course pages took 4.5 s and 2.9 s at full volume. See ADR-029.

## Concurrency: one submission per token

`loadtest/concurrency/run.sh` makes 64 pgbench clients redeem the same token at once against four versions of the same rule (`loadtest/results/concurrency.txt`):

| Version | Rows stored for one token | Notes |
|---|---|---|
| Check, then insert (READ COMMITTED) | **64** | every client sees "not taken" before anyone commits |
| `SELECT … FOR UPDATE` on the offering row, then check and insert | 1 | redemptions of the offering queue behind the lock: 108 ms average latency |
| `SERIALIZABLE`, check and insert | 1 | 63 of 64 transactions failed with `40001` and were retried |
| Primary key, `INSERT … ON CONFLICT (key_hash) DO NOTHING` | 1 | no lock, no retry, 12 ms average latency; this is what `SubmissionService.create` runs (ADR-011) |

The same check against the real endpoint: `loadtest/k6/run.sh redeem` mints one valid token (`mint-token.sh` signs with a fresh RSA key it registers for one offering) and has 200 k6 virtual users post it to `/anon/submissions` at once. Result (`loadtest/results/redeem-same-token.txt`): **1 × 201, 199 × 409, 0 other**.

## Load test

`loadtest/k6/browse.js`: a signed-in student opens course pages (60 %), professor pages (30 %) and catalogue years (10 %), under a `ramping-arrival-rate` from 20 to 300 requests/s over 30 s, then 30 s at 300/s. `loadtest/k6/pool-sizes.sh` repeats it for each HikariCP pool size (`loadtest/results/pool-sizes.csv`):

| Ident pool size | Course p95 | Professor p95 | Catalogue p95 | Requests served in 60 s |
|---|---|---|---|---|
| 2 | 5,160 ms | 5,176 ms | 4,548 ms | 5,004 (k6 dropped the rest: no free VU) |
| 5 | 2,129 ms | 2,139 ms | 1,830 ms | 10,607 |
| **10** | **48 ms** | **66 ms** | **24 ms** | 13,872 (the full schedule) |
| 20 | 48 ms | 72 ms | 22 ms | 13,872 |
| 40 | 244 ms | 272 ms | 201 ms | 13,836 |

No request failed at any size. Below 10, requests wait for a connection: Tomcat's 200 threads queue on Hikari. Above 20, the database itself slows down: 40 sessions compete for 10 cores, with more context switches and lock and buffer contention, so every query takes longer. 10 to 20 is the plateau, so the pool stays at 10 (ADR-020); the run is on a 10-core laptop with PostgreSQL and the app on the same machine, so a VPS with fewer cores would plateau lower.
