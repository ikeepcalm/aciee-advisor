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

**Update (027).** Each component now has its own criteria plus four shared grading ones: 24 columns, of which a rating fills only its component's and the shared ones. The pillar of an axis is its component code or `grading`. The radar is grouped per professor × subject × component, and each criterion needs k answers of its own, because quick mode leaves most criteria empty and the row's `n` would otherwise expose a single student's score. Old ratings kept the criteria that ask the same thing; the rest were dropped.

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
- Students without a group request to join a led group from the picker and the leader approves. An expiring, capped invite link is the leader's approval given in advance: opening it joins at once.
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

## ADR-026 — Docker Compose behind the host's reverse proxy instead of Caddy
**Context.** The plan was Docker Compose plus Caddy for HTTPS on one VPS. The server already runs a reverse proxy that issues certificates and routes domains for other services.

**Decision.** Deploy each environment as one Docker Compose project (`infra/docker-compose.prod.yml`). An nginx container serves the SPA and proxies `/api` and `/anon` to the backend, so one domain covers both and there is still no CORS. Only that container publishes a port, on `127.0.0.1`, for the host's proxy. The Postgres image bakes in the role script instead of bind-mounting it from the repository. A `backup` service runs `pg_dump` daily.

**Consequences.** No Caddyfile. The compose file runs with plain `docker compose` given the same environment variables. nginx passes on the host proxy's `X-Forwarded-Proto`, which Spring trusts from private addresses, so secure cookies work behind two proxies.

## ADR-027 — Pitch-day shortcuts on the anonymous side
**Context.** The project is presented at pitch day. Students need every view working; several protections in ARCHITECTURE §3 cost more than they are worth before launch.

**Decision.**
- No separate anon pool. `aciee_ident` reads and writes `anon.submission`, `rating`, `post`, `post_version` and `post_report` (changeset `016`). `/anon/**` calls still carry no session or CSRF token, and no table links a submission to an account.
- Submissions are sent when the student presses "Trimite", not at a random time the next day.
- Approved texts are published at once; `anon.published_post` drops the weekly release.
- Aggregates refresh hourly (`AggregateRefreshTask`), not nightly.

**Consequences.** Someone with the app's database login can now read anonymous data, and issuance and redemption can sit next to each other in time, so the ordering risk in ARCHITECTURE §3 is larger. To restore the separation: a second `DataSource` as `aciee_anon` for `/anon/**` and the anon reads, then revoke the `016` grants and bring back `AccessTests.identPoolCannotReadAnonymousData`.

## ADR-028 — Reports through a `report` schema and their own pool
**Context.** Stage 5 needs PDF, CSV and Excel exports from database queries. ARCHITECTURE §4 says `aciee_report` reads only report views, but it was granted the bare materialised views, which hold ids and no names, so a useful report needed `core` access the role must not have.

**Decision.**
- Changeset `017` adds schema `report` with `prof_subject` and `course_survival`: the two aggregates with professor, department and subject names and each root subject's specialisations. The views run with their owner's rights, so `aciee_report` gets SELECT on them and loses its grants on `anon`.
- A third pool (`app.report-datasource`, 2 connections, read-only) logs in as `aciee_report`, so the 60 s `statement_timeout` and the grants apply to every report query. It is a non-default bean, so the auto-configured ident pool stays primary.
- Formats: CSV with a BOM, `.xlsx` through fastexcel (small, no Apache POI), PDF through OpenPDF with the Liberation fonts embedded for ș and ț. One `ReportTable` (columns, rows, pillar sections) feeds all three writers.
- Reports are admin-only. A cancelled statement (`57014`) maps to `503 timeout`.

**Consequences.** A SQL injection in a report query could read only aggregates that are already public above k. A new report is one view, one `ReportType` entry and its grant.

## ADR-029 — Request-path fixes found by the Stage 4 captures
**Context.** Run on the full fixture volumes, the professor page's comment query took 4.3 s and the course page's 2.9 s, close to the 5 s `statement_timeout`. `anon.published_post` used `DISTINCT ON (p.id)`, which the planner cannot push a filter through, so every call built all 268k published versions first. Its `kind = 'tip' OR EXISTS (k check)` was turned into a hashed subplan over every assignment. On top of that, high cost estimates switched on JIT, which spent 200–290 ms compiling queries that run in 30 ms.

**Decision.**
- `anon.published_post` (changeset `018`) takes each post's latest approved version with `LATERAL (… ORDER BY version_no DESC LIMIT 1)`, the backward PK scan DATABASE §6 Q9 always described. `OFFSET 0` in the k-check `EXISTS` keeps it a per-row subplan.
- `jit = off` for `aciee_ident` and `aciee_anon`. `aciee_report` keeps it.
- The `/rate` list keeps its `EXISTS … OR EXISTS` form: without JIT it runs in 27 ms, the same as a `UNION` rewrite.

**Consequences.** The professor-page comments take 37 ms instead of 4.3 s, with the same rows (`docs/explain-plans/09-published-post.txt`). Removing `OFFSET 0` silently brings back the hashed subplan; the changeset says so in a one-line comment.

## ADR-030 — Microsoft sign-in behind a profile, linked by address
**Context.** ADR-008 planned Entra OIDC as an alternative sign-in. The app registration is ours to create and may need the university's consent, so the code has to ship before the registration exists. Students who already signed up with a password will also use the Microsoft button.

**Decision.**
- Spring Security `oauth2Login`, enabled only when client registrations exist, which happens only with the `microsoft` profile (`application-microsoft.yml`). `GET /api/auth/providers` tells the SPA whether to show the button.
- Endpoints stay under `/api/auth/oauth2/…`, so nginx and the Vite proxy need no new routes.
- The issuer is UGAL's tenant (`445e5699-60fa-4f59-8e7f-778197a4d740`, a public value), so Spring rejects tokens from other tenants. `MicrosoftSignInHandler` checks `tid` and the `@student.ugal.ro` suffix again, then replaces the OIDC principal with the same `userId` session a password sign-in creates. On any refusal it invalidates the session the OAuth2 filter had already saved.
- Linking by address follows the rule in ARCHITECTURE §2: an unverified account loses its password, and an address linked to another `oid` is refused.

**Consequences.** Nothing changes until the profile and two secrets are set (DEPLOY.md). A student keeps their password after linking and can use either method. A student whose UPN changes gets a new `oid`, which is refused for the old address; an admin has to clear `entra_oid` by hand.


## ADR-031 — Teachers imported from the timetable, corrected by group leaders
**Context.** Entering every professor and teaching assignment by hand was one admin's job, and prod still had no professors. The faculty timetable we already sync every hour names the teacher of each lecture, seminar, lab and project, but loosely. The 478 lessons carry 233 different strings for about 90 people (`B. Dumitrașcu`, `Dumitrascu Bogdan`, `Ș.l.dr.ing. Bogdan Dumitrașcu`), with name order swapped, typos (`Dimitivevici`, `N.Ttigau`) and "prof." used for any teacher.

**Decision.**
- `TeacherImportService` runs after each timetable refresh. It matches a lesson to an offering by group code (`study_group.code` equals the timetable column label) and the folded course name against `course_offering.title_snapshot` of the current academic year. It maps C/S/L/P to lecture/seminar/lab/project and writes only the group-level slot (`subgroup_id IS NULL`), and only when the curriculum has hours for that component.
- `TeacherNames` groups spellings of one person: two full names match when one's words contain the other's; a short form (`A. Cocu`) matches a full name by surname and first initial, with the surname taken from the first or last word; two short forms match with the same initial and a surname at most one edit apart (two from 9 letters). A slot whose lessons name two different people is skipped.
- An unknown teacher is created with the most complete spelling, the title from a prefix that is reliable (`conf`, `ș.l.`, `lect`, `as`/`drd`, or `prof` only together with `dr`; otherwise the new `cadru` title), department `Nespecificat` and `name_confirmed = false`.
- `core.timetable_import` stores what the importer last applied per slot. It fills an empty slot or replaces its own earlier choice only when the timetable's teacher text changes; a slot a leader or admin set, or a leader emptied, is left alone. The stored texts also act as aliases, so a renamed professor is still found.
- Group leaders may now set lecture teachers, add a missing professor (deduplicated on `core.name_key`), and fix the name of an unconfirmed professor who teaches their group this year. Any admin create or edit of a professor sets `name_confirmed = true`, which locks the name against leaders. The slug never changes, so links stay valid. All writes go through the audit triggers.

**Consequences.** About half the lessons match an offering by name; the rest need an admin to align titles or a leader to pick the teacher. Typos beyond one edit and two people with the same surname and initial still produce duplicate or merged professors that only an admin can fix (there is no merge tool yet). Imported assignments start on the import day, like a leader's.

## ADR-032 — Demo environment and live show replaced by recordings
**Context.** The demo environment (`demo.acieeadvisor.com`, `demo` Liquibase context) existed to run the phone-steered pitch show (`/show`, `/show/remote`, an SSE relay under the `show` Spring profile) on 2026-11-22. After the pitch it costs a second app, database and nginx site, and its code (about 30 files) had to keep up with every page it drove. The owner wants a shareable link that shows the product in action on social media.

**Decision.**
- The show was recorded headlessly at 1440×810 from Chrome's screencast frames (the built-in Playwright recorder was too soft for text), in English and Romanian. Each beat was held until the stage reported it had finished, plus time to read the caption. The output is a 29.5 s highlight loop and the full 2:17 tour per language, H.264 at 720p, in `frontend/public/preview/` (about 16 MB).
- `/preview` plays the loop and offers the full tour. Link-preview bots get English tags for it: `og:type video.other` and `og:video` pointing at the loop (Discord and some others play it inline), and a designed 1200×630 `og:image` for the platforms that show only a still (X and Facebook show only the first frame of a GIF, so a GIF would not have helped).
- The show code, the relay, the `show` profile, the `demo` fixture changeset and `docs/SHOW.md` are deleted (the last commit that had them is `4185a7e`). `frame-ancestors` goes back to `'none'`, since the remote's preview iframe was its only use.
- The demo app, its nginx site and its DNS record are removed by hand (TODO.md).

**Consequences.** One environment to run. The recordings show fictional data and the UI as of 2026-11-23; they go stale as the UI changes and have to be re-recorded from that commit if needed. Restore tests now use a scratch database instead of the demo.

## ADR-033 — Signing keys created automatically, build-time key pin dropped
**Context.** Ratings needed four manual steps per environment: call `POST /api/admin/semesters/{id}/token-keys`, hash `/anon/keys.json`, commit that hash to `frontend/src/crypto/pinned-keys.json`, redeploy. Any group added later meant repeating all four, and until then nobody could get tokens. The pin stops the server from handing one student a key of their own (which would link their ratings to them), but only for someone who also trusts the JavaScript bundle, which the same server ships; ARCHITECTURE §3 already lists modified JavaScript as an attack it cannot fully stop.

**Decision.**
- The hourly timetable sync, and an Admin "Sync from timetable" button, create this year's missing groups from the timetable's group codes (only programmes whose specialisation and plan are seeded), generate their courses, import teachers (ADR-031), and start a background job that creates a key for every current-year offering without one.
- The client no longer checks `keys.json` against a pinned hash. It still checks each key against its fingerprint and the issued signatures against the keys. `/rate` shows a short fingerprint of the whole `keys.json`, fetched without cookies, which is the same for everyone at a given time, so classmates can compare it and report a difference.

**Consequences.** No redeploys or console calls for ratings to open. Detection of a targeted key now relies on students comparing fingerprints and on the open-source bundle, not on a hash in the build. The fingerprint changes whenever keys are added (for example when a new group appears), so comparisons only hold between students who load `/rate` around the same time.

## ADR-034 — Timetable letters are subgroups, course names matched loosely
**Context.** The first timetable sync (ADR-033) made every timetable column a group, so `22C31A` and `22C31B` became two groups with two leader positions. They are the A and B subgroups of group `22C31`. Teachers were also missed when the timetable spells a course differently from the curriculum ("Grafica asistata pe calculator" for "Grafică asistată de calculator", "Lb. Engleza" for "Limba engleză (anul 1)").

**Decision.**
- A label ending in a letter after a digit is group + subgroup (`GroupLabel`). The sync creates the group once and a `core.subgroup` row per letter (max size 60).
- The importer fills the whole-group slot for lectures, and for a seminar, lab or project when one teacher covers every subgroup; otherwise it fills each subgroup's slot. `core.timetable_import` is keyed by (offering, component, subgroup).
- When the exact course name does not match, the importer picks the group's course whose significant words overlap most (Jaccard ≥ 0.6, ignoring "de/pe/și", roman numerals and "anul N"), and only when that match is strictly the best (`CourseTitles`).
- Changeset `023` deletes this year's letter-suffixed groups that have no submitted ratings, with their courses, keys, memberships and claims, so the next sync recreates them properly.

**Consequences.** Locally the semester-1 coverage went from 188 to 199 of 245 slots. The rest are not in the timetable at all (several labs, projects and electives) or sit in combined cells whose teachers are in the note ("SO S1-7 / POO S8-14"); leaders set those. Anyone who had joined or claimed a letter group before `023` has to do it again.

## ADR-035 — Past years rated through the current group
**Context.** Students in year 2 and above want to rate the courses they already finished, but the database only held this year's groups, and nothing records which group a student was in before. The faculty keeps past timetables online under `/files/studenti/orare/YYYY_YYYY/semN/`.

**Decision.**
- The timetable sync creates the earlier academic years, their two semesters (open for rating) and one group per earlier year for each current group, by changing the study-year digit of its code (22C31 → 22C21, 22C11). Each such group points at the current group through `study_group.successor_id`, and gets its courses from the same study plan.
- A member of a group may rate the courses of every group that points at it. No request or approval.
- Past teachers are imported once per app start from the archived PDFs of the matching programme and year, with the same importer as this year, assignments spanning the whole past year. Course-name matching also understands acronyms (POO, PCLP II) and numbered parts (I/II).

**Consequences.** Year 1 had more groups than year 3 for some cohorts, so a student may land on a group whose lab teachers they never had; the rating form's "did you have this teacher" step covers it, and an admin can correct the assignments. Leaders cannot yet edit past-year teachers. The first `/rate` visit after this change issues tokens for all past courses at once, so the past groups must not be restructured afterwards.
