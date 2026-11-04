# Architectural Decision Records

Format: context → decision → consequences. Never rewrite an accepted ADR; supersede it with a new one.

---

## ADR-001 — PostgreSQL as the single DBMS
**Context.** The course requires one relational DBMS and covers MVCC, indexing, RLS, partitioning and PL/pgSQL.

**Decision.** PostgreSQL 17. Use Liquibase with **formatted SQL** changelogs (`--liquibase formatted sql`), so every changeset is plain SQL. Fixture changesets carry `context:fixtures` and run only when `spring.liquibase.contexts` includes `fixtures`.

**Consequences.** Everything can be explained in SQL during the defence. Each changeset can have a rollback. Fixtures are generated in-database with `generate_series`, which keeps seeding to seconds rather than minutes.

## ADR-002 — Blind-signature tokens for rating anonymity
**Context.** Ratings must be restricted to enrolled students, with one per student per course, and editable. They must also be unlinkable to accounts, even for someone with full database access. Checking eligibility and storing the rating in the same request links them: shared `xmin`, adjacent xids, ctids and WAL positions, and the request logs. Coarse timestamps do not remove that link. Separating the two without cryptography removes the proof of eligibility.

**Decision.** Use RFC 9474 blind RSA with one key per course offering.
- Tokens for all of a student's eligible offerings are issued in one authenticated request.
- Ratings are submitted anonymously with a token.
- `SHA-256(token)` is the primary key of the submission.

**Consequences.**
- The database has no join key between accounts and submissions.
- Revoking a student after issuance is impossible.
- A lost recovery code means the submission can no longer be edited.
- Residual risks (modified client code, network logs, operator-minted ballots) are documented in `ARCHITECTURE.md` §3.

## ADR-003 — 14 axes as columns, not EAV
**Context.** A rating has 14 optional 0–10 scores.

**Decision.** Store them as 14 nullable `smallint` columns with CHECK constraints. Labels go in an `axis` lookup table.

**Consequences.** Aggregation is a single scan, constraints are trivial, and NULL semantics come for free. Adding an axis means `ALTER TABLE ADD COLUMN`.

## ADR-004 — k = 5, pooled per professor × subject, refreshed nightly
**Context.** Cohorts are 12–100 students. Showing results per group would rarely reach any threshold. Live aggregates let anyone recover a single rating by comparing the averages before and after it was added.

**Decision.** Use a materialised view grouped by professor × subject across groups and semesters, with `HAVING count(*) >= 5`. Values are rounded and the view is refreshed concurrently each night.

**Consequences.** New ratings appear the next day. Small courses may stay locked; the UI explains why.

## ADR-005 — Rating opens after the semester ends
**Context.** Grading-related axes can only be judged after exams. Issuing tokens once rosters are final also limits the "removed student keeps a token" risk.

**Decision.** `semester.rating_opens_at` gates token issuance. Past semesters are always open through historical claims.

**Consequences.** At launch every real rating comes from past semesters, which makes historical claims a must-have feature.

## ADR-006 — Written content: course tips and short teaching comments
**Context.** Free text about named people carries the most legal and reputational risk. Fixed-criteria, averaged ratings from verified students are the lowest-risk form.

**Decision.**
- Course tips: up to 1,000 characters, about the course.
- Teaching comments: up to 280 characters, about how the teaching works, never about character.
- Both are pre-moderated with reason codes and released in weekly batches.
- Comments become visible only after the professor × subject pair passes k.
- Every published item has a Report button.

**Consequences.** The moderation queue is the bottleneck. If it falls behind, text is disabled and ratings continue.

## ADR-007 — Axis 14 wording
**Context.** The original wording, "Professional behaviour & academic integrity", implies misconduct when averaged low.

**Decision.** Rename it to "Respectful treatment of students" (*Atitudinea respectuoasă față de studenți*).

**Consequences.** It measures observable behaviour and no longer implies an accusation.

## ADR-008 — Authentication
**Context.** Student email addresses don't reveal faculty or group, and there is no university API for enrolment. Student mail is hosted on Microsoft 365.

**Decision.**
- Email + Argon2id password + emailed 6-digit code as the baseline, restricted to `@student.ugal.ro`.
- Microsoft (Entra OIDC) sign-in as an alternative.
- Server-side sessions stored in Postgres (Spring Session JDBC), with cookie-based CSRF protection.
- No JWT.

**Consequences.**
- Sessions can be revoked instantly.
- No refresh-token logic is needed, because the SPA and API share an origin.
- Email deliverability (SPF, DKIM, DMARC) is a launch prerequisite.

## ADR-009 — Group-leader web of trust
**Context.** Group membership can't be verified automatically.

**Decision.**
- An admin approves group-leader claims.
- Leaders approve join requests that arrive through expiring, capped invite links.
- Groups have a size cap, and every approval is audited.
- Leadership is scoped to a group and an academic year.
- `group_membership.source` reserves `university_sync` for a future integration.

**Consequences.** Trust depends on the leaders. Admins can revoke a leader and undo their approvals.

## ADR-010 — Shared lectures as one assignment per group
**Context.** One lecture can be taught to several groups at once.

**Decision.** Each group's offering gets its own lecture assignment row. Aggregation pools by professor × subject.

**Consequences.** Some rows are duplicated, but there are no extra tables or joins. Revisit only if lectures are ever rated per series.

## ADR-011 — Concurrency guarantee: one submission per token
**Context.** Two requests can try to redeem the same token at the same moment, for example from two tabs or by retrying.

**Decision.** The `anon.submission` primary key (`key_hash`) is the only guard. There is no check-then-insert in application code. A duplicate key error (`23505`) maps to HTTP 409.

**Consequences.** The rule holds at READ COMMITTED with no locks or retries. The Stage 4 demo shows the naive check-then-insert approach failing, then `FOR UPDATE`, `SERIALIZABLE`, and finally the constraint as the simplest correct fix.

## ADR-012 — Lawful basis for processing professor data
**Context.** Ratings about a named professor are that professor's personal data under GDPR.

**Decision.**
- Lawful basis: Art. 6(1)(f), legitimate interest. A written assessment (purpose, necessity, balancing) is kept in `docs/LIA.md`.
- Data is minimised to name, title, department and teaching assignments.
- Objections and takedown requests go through a published contact form.

**Consequences.** The assessment should be reviewed by the university's data protection officer before public launch.

## ADR-013 — Java 25, Spring Boot 4.1, PostgreSQL 18
**Context.** The original stack named Java 21, Spring Boot 3 and PostgreSQL 17. By project start (October 2026) Spring Boot 3.5 was out of open-source support and no longer offered by Spring Initializr; Java 25 and PostgreSQL 18 were the current stable releases.

**Decision.** Java 25 (LTS), Spring Boot 4.1 with Gradle (Groovy DSL), PostgreSQL 18. Supersedes the version in ADR-001; the rest of ADR-001 stands.

**Consequences.**
- Supported versions for the life of the project.
- PostgreSQL 18 ships a native `uuidv7()`. It is still forbidden in `anon` tables because it embeds a timestamp.

## ADR-014 — 3NF, with redundancy only as FK-pinned key columns
**Context.** The first schema sketch carried derivable columns (`is_elective`, `semester_bucket`, `course_tip.offering_id`, `study_group.specialization_id`) and a few cross-table rules (a group's offerings come from its own plan; a token rates only its own offering's teachers) that are easiest to enforce with a copied column.

**Decision.**
- Every table is in 3NF; derivable columns are dropped.
- A copied column is allowed only as part of a composite foreign key to the table it copies from, so the database keeps it equal to its source.
- The full list of such columns is kept in `DATABASE.md` §4.

**Consequences.** Cross-table rules hold without triggers. The few copied columns break 2NF/3NF on paper but cannot cause update anomalies. Some queries need one more join (for example, an offering's semester).

## ADR-015 — Lookup tables, CHECK constraints and domains instead of ENUM
**Context.** Many columns take values from small sets. PostgreSQL `ENUM` values cannot be removed or reordered without rewriting the type.

**Decision.**
- A lookup table in schema `ref` with a text natural key when the value has a UI label or the set can grow.
- A CHECK constraint when the set is closed and code branches on it (statuses, degree, semester number).
- Domains (`score`, `pct`, `sha256`, `week_start`) for types reused across tables.

**Consequences.** New components, reason codes or exam formats are data changes, not migrations. A text key means rows read without a join. `ref` is written only by the migrator.

## ADR-016 — Temporal keys; requests separate from memberships
**Context.** A student has one home group at a time but may move between groups, and may repeat a single course with another group. The first sketch kept requests and approved memberships in one table and needed an `EXCLUDE … WHERE (status = 'approved')`.

**Decision.**
- `membership_request` holds the workflow (pending, approved, rejected). `group_membership` holds only approved facts.
- `group_membership` has a surrogate `id` and the PostgreSQL 18 temporal key `UNIQUE (user_id, valid_during WITHOUT OVERLAPS)`, so a student can leave and re-join a group.
- A repeated single course is an `offering_enrolment`, not a second membership.
- Leadership is its own table (`group_leader`) keyed by the membership, so it ends when that membership ends.

**Consequences.** The overlap rule is a constraint; its GiST index also serves "what can I rate". Requires `btree_gist`. Membership rows have no status-dependent NULL columns. Temporal keys cannot be `ON CONFLICT DO UPDATE` arbiters, so memberships are never upserted.

## ADR-017 — Study plans own curriculum validity
**Context.** The curriculum changes for new cohorts while older cohorts finish on the old one. The first sketch put `valid_from_ay / valid_to_ay` on every curriculum item.

**Decision.** A `study_plan` per specialization and intake range (`UNIQUE (specialization_id, intake WITHOUT OVERLAPS)`). Curriculum items and study groups reference the plan; a course offering is a curriculum item given to a group.

**Consequences.** Validity is stated once per plan. A new curriculum is a new plan, and history stays intact. Offerings derive their subject and semester instead of storing them.

## ADR-018 — Tips and comments as versioned posts
**Context.** Tips and comments share moderation, editing, versioning and reporting. An edit must create a pending version while the approved one stays visible.

**Decision.** One `post` table (kind `tip` or `comment`, CHECK-bound to `teaching_assignment_id`) and one `post_version` table (body, status, reason, weeks). The published text is the highest approved version.

**Consequences.** One moderation queue, one partial index and one report table instead of two of each. The length limit is a CHECK on `post_version` that reads the pinned `kind`.

## ADR-019 — Index policy
**Context.** Indexes cost write time and storage, and PostgreSQL does not index foreign keys automatically. Stage 4 requires each index to be justified.

**Decision.**
- Each index answers a query in the `DATABASE.md` §6 catalogue or backs a constraint.
- Unindexed FKs are listed with the reason.
- Partial indexes for status-filtered queues (with the status as a literal in the SQL, never a bind parameter), BRIN on the append-only audit log, and extended statistics on the pinned column pairs of the large tables.
- Unique constraints are declared lookup column first, so one index serves both the FK and the query.
- Demo-only indexes (the covering radar index) are created and dropped by the Stage 4 scripts, not kept in the schema.
- Every index is evidenced with `EXPLAIN (ANALYZE, BUFFERS)` before and after.

**Consequences.** No index exists without a query that needs it. A new query adds its row to the catalogue along with its index.

## ADR-020 — Two connection pools with per-role limits
**Context.** The `ident` and `anon` sides must not share credentials (ARCHITECTURE §4). A slow query or a forgotten transaction must not hold connections or locks indefinitely.

**Decision.**
- Two HikariCP pools, one per database role, each named via `ApplicationName` and with its own transaction manager. Liquibase uses a third DataSource with the migrator role.
- Start at 10 connections each; Stage 4 measures and fixes the final size.
- `statement_timeout`, `lock_timeout` and `idle_in_transaction_session_timeout` set per role with `ALTER ROLE`. The migrator has no statement timeout but a short lock timeout. The nightly refresh runs in a `SECURITY DEFINER` function with its own timeout.
- No PgBouncer, no driver-level insert batching: request paths write a few rows each.

**Consequences.** Limits hold whichever code path issues the query. Every `@Transactional` must name its transaction manager. Revisit pooling only if the app runs more than one instance.

## ADR-021 — Tokens issued per offering, with stored blind signatures
**Context.** Issuance keyed per semester could not issue an offering that becomes eligible later (a historical claim, a repeated course), and a response lost after commit left the student with no tokens and no way to get them.

**Decision.**
- `ident.token_issuance(user_id, offering_id)` stores the blinded message and blind signature.
- A request must cover every eligible offering not yet issued. A retry returns the stored signatures.
- Issuance dates are kept to the week (`issued_week`).
- The client redeems no earlier than the day after issuance.

**Consequences.** Late eligibility and lost responses are handled. Stored blinded values cannot be linked to redeemed tokens (RFC 9474 blindness). The issuance–redemption ordering visible in transaction ids is reduced, not removed; it is listed in ARCHITECTURE §3.

## ADR-022 — Aggregates keep sub-k pairs; k is a setting
**Context.** ADR-004's `HAVING count(*) >= 5` drops locked pairs from the view, so the UI cannot show "N more ratings needed". Computing N live from `rating` is the leak ADR-004 forbids. k may change.

**Decision.** Partly supersedes ADR-004. The materialised views keep every pair with its count `n`; the averages are NULL until `n >= k`. `k` is read from `ref.setting`. Pooling, rounding and the nightly refresh from ADR-004 stand.

**Consequences.** Counts of locked pairs become visible, scores do not. Changing k is an `UPDATE`, visible after the next refresh.

## ADR-023 — Audit log written and maintained in batches
**Context.** `audit_log` is the only table that grows without bound (5M fixture rows). A row-level trigger costs one insert per changed row. A DEFAULT partition makes adding next month's partition scan it under an exclusive lock, and fail if any row already belongs to that month.

**Decision.**
- A statement-level trigger with transition tables writes all rows a statement changed in one `INSERT … SELECT`.
- Monthly partitions, no DEFAULT. `ensure_audit_partitions(n)` creates every missing month in one call, from a monthly scheduled job and from the creating changeset. `retire_audit_partitions(n)` detaches and drops expired months.
- Fixture rows are generated one month per statement.

**Consequences.** Bulk changes (rollover, imports) cost one audit insert per statement. An insert for a month with no partition fails loudly instead of landing in a catch-all, so the scheduled job must run; it creates several months ahead.

## ADR-024 — No official grading weights
**Context.** The schema stored a lab / exam / project split (summing to 100) for every curriculum item, shown on course pages as "official weights". The study plans list hours, credits and the evaluation form (exam or colloquium) but not this split. It lives in the per-course descriptions, which are not published for every course, and professors do not always announce it.

**Decision.** Drop `lab_pct`, `exam_pct`, `project_pct`, their CHECK and the `pct` domain. Supersedes the `pct` entry in ADR-015's domain list.

**Consequences.** Course pages no longer show a split we could only guess. Students describe how a course is passed through the survival metadata instead.

## ADR-025 — Admin CRUD driven by one table list; database errors mapped once
**Context.** The admin screens edit 13 tables (curriculum, calendar, groups, professors, assignments). Hand-written endpoints and forms per table would repeat the same list / create / update / delete code 13 times, and each would map constraint errors on its own.

**Decision.**
- `AdminTable` (an enum) lists the editable tables: columns with their SQL types, foreign keys to other entries, a label expression, and columns the server fills itself (a pinned `plan_id` or `group_id` derived from the chosen group or offering). Table and column names come only from the enum; values are bound parameters cast to the column type.
- One service builds the SQL; one Vue view renders any table from the metadata. Lists page by key (keyset), never `OFFSET`.
- Rules that are not constraints stay explicit: a semester must lie inside its year (DATABASE §3.3), archiving a professor anonymises the name (ADR-012), and "generate the group's courses" inserts one offering per curriculum item of the group's plan and year.
- `DatabaseErrorAdvice` turns PostgreSQL errors into problem details once, by SQLSTATE: FK → `foreign_key`, unique / exclusion → `duplicate`, CHECK → `check_failed`, NOT NULL → `required`, bad input → `invalid_value`, with the constraint and column names attached.

**Consequences.** A new table needs one enum entry and its labels. The database stays the only validator: the form shows the constraint the row broke. Domain screens (groups, ratings) keep their own endpoints and messages.

## ADR-026 — the deploy tool with Docker Compose instead of Caddy
**Context.** The plan was Docker Compose plus Caddy for HTTPS on one VPS. The server is a VPS that already runs the deploy tool, whose Traefik proxy issues certificates and routes domains to containers.

**Decision.** Deploy each environment as a deploy-tool Docker Compose resource (`infra/docker-compose.prod.yml`). An nginx container serves the SPA and proxies `/api` and `/anon` to the backend, so one domain covers both and there is still no CORS. The Postgres image bakes in the role script instead of bind-mounting it from the repository. A `backup` service runs `pg_dump` daily.

**Consequences.** No Caddyfile. The compose file also runs with plain `docker compose` given the same environment variables. nginx passes on Traefik's `X-Forwarded-Proto`, which Spring trusts from private addresses, so secure cookies work behind two proxies.

## ADR-027 — Pitch-day shortcuts on the anonymous side
**Context.** The project is presented at pitch day. Students need every view working; several protections in ARCHITECTURE §3 cost more than they are worth before launch.

**Decision.**
- No separate anon pool. `aciee_ident` reads and writes `anon.submission`, `rating`, `post`, `post_version` and `post_report` (changeset `016`). `/anon/**` calls still carry no session or CSRF token, and no table links a submission to an account.
- Submissions are sent when the student presses "Trimite", not at a random time the next day.
- Approved texts are published at once; `anon.published_post` drops the weekly release.
- Aggregates refresh hourly (`AggregateRefreshTask`), not nightly.

**Consequences.** Someone with the app's database login can now read anonymous data, and issuance and redemption can sit next to each other in time, so the ordering risk in ARCHITECTURE §3 is larger. To restore the separation: a second `DataSource` as `aciee_anon` for `/anon/**` and the anon reads, then revoke the `016` grants and bring back `AccessTests.identPoolCannotReadAnonymousData`.
