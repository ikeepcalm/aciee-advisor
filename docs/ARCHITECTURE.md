# ACIEE Advisor — Architecture

## 1. Stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Gradle, Spring Security, Spring Session JDBC, Liquibase (formatted SQL changelogs) |
| Database | PostgreSQL 18 (single DBMS) |
| Frontend | Vue 3, Vite, TypeScript, Pinia, PrimeVue, vue-i18n (`ro-RO`), Chart.js via vue-chartjs |
| Crypto | RFC 9474 blind RSA: Bouncy Castle `bcprov-jdk18on` ≥ 1.85.2 (server), `@cloudflare/blindrsa-ts` (browser), WebCrypto HKDF + AES-GCM |
| Infra | Docker Compose: postgres + app + Caddy (automatic HTTPS) on one VPS |
| Load test | k6 |
| CI | GitHub Actions + Testcontainers |

The SPA and `/api` are served from the same origin, so there is no CORS.

## 2. Authentication

- **Email + password + verification code.**
  - Code: 6 digits, 15-minute expiry, stored hashed (`sha256` in SQL), 5 attempts. Sent at sign-up and when an unverified account signs in; a new code is issued at most once a minute and resets the attempts. The mail goes out after the transaction commits, so no connection is held during SMTP.
  - **Verifying needs the code and the password.** Signing up again for an unverified address replaces its password (so a squatted address can be reclaimed), but the account only becomes verified for whoever knows both the code (owns the mailbox) and the password. Without this, anyone could plant a password on a student's unverified account and wait for the student to type the code.
  - Sign-up is idempotent: repeating it stores the latest password and keeps the code already sent (no new mail, no error).
  - Endpoints: `POST /api/auth/signup`, `/verify`, `/verify/resend`, `/signin`, `/signout`; `GET /api/auth/me`, `/csrf`. Errors are RFC 9457 problem details whose `detail` is a stable code (`unverified`, `code_invalid` with `attemptsLeft`, `code_expired`, …).
  - Passwords: Argon2id (`Argon2PasswordEncoder`).
- **Microsoft sign-in (Entra OIDC)** as an alternative. Accept only the UGAL tenant `tid` and the `@student.ugal.ro` suffix.
- **Email normalisation**: lowercase, strip `+tag`, unique index on the normalised value.
- **Sessions**: server-side, Spring Session JDBC.
  - Cookie: `HttpOnly; Secure; SameSite=Lax`.
  - CSRF: `csrf.spa()` (Spring Security 7), i.e. `CookieCsrfTokenRepository.withHttpOnlyFalse()`; the SPA echoes the `XSRF-TOKEN` cookie in `X-XSRF-TOKEN`.
- **Mail**: sent from our own domain with SPF, DKIM and DMARC. Plain text, code only, no links.

## 3. Anonymity protocol

### Goal
- Only students enrolled in a course offering can rate it.
- One submission per student per offering.
- Students can edit and delete their submission.
- The database holds **no link** between a submission and an account.

### Why not "check eligibility and store the review in one request"
Rows written in one transaction share `xmin`. Rows written in back-to-back transactions get adjacent xids, ctids, sequence values and WAL positions, and request logs pair them too. Coarse timestamps don't remove any of these links. If the check and the storage are separated without cryptography, nothing proves the reviewer was eligible.

### Blind signatures (RFC 9474, `RSABSSA-SHA384-PSSZERO-Deterministic`)

**Unit of eligibility.** The unit is the **course offering** (subject × group × semester). One token buys one submission: survival metadata, plus one rating per teaching assignment of that offering, plus an optional tip and comments.

**Keys.** One RSA-2048 key pair per offering.
- Private keys live in a keystore on the app host, never in the database.
- Public keys are published in `keys.json`, and its hash is committed to the repository.
- The client rejects any key that is not in that list. This prevents per-user tagging keys.

**Flow:**
1. **Client setup.** The client generates a 128-bit `master` secret and shows it once as a recovery code, then stores it in IndexedDB. The message for each offering is `msg = HMAC(master, "aciee-v1" || offering_id)`.
2. **Issue** (authenticated, opens when the semester ends; past semesters open on membership approval).
   - The client blinds `msg` for **every** eligible offering not yet issued and sends them in one request. It stores its blinding factors first, so a retry can reuse them.
   - The server rejects partial sets, checks eligibility (memberships and single-course enrolments), records one `token_issuance(user_id, offering_id)` row per offering with the blinded message and blind signature, and returns the signatures.
   - A retry after a lost response gets the stored signatures back. An offering that becomes eligible later (a historical claim, a repeated course) is issued in a later request.
   - The client verifies the signatures and uploads an encrypted `token_vault` (AES-GCM under `HKDF(master)`) for multi-device recovery.
3. **Redeem** (anonymous: `credentials: 'omit'`, `/anon/*`, access logging off).
   - The client redeems no earlier than the day after issuance, at a random time, so issuance and redemption are not adjacent.
   - The request carries `offering_id, prepared_msg, sig, payload`.
   - The server verifies the RSASSA-PSS signature, then stores `key_hash = SHA-256(prepared_msg)`. `key_hash` is the primary key, which enforces one submission per token.
4. **Edit / delete** (anonymous). The client proves ownership by sending `prepared_msg`, which only it knows.
5. **No re-issue.** A lost recovery code means the student can no longer edit; the submission stays.

### Residual risks

These are stated openly, not hidden:
- An operator who ships modified JavaScript, or who logs IPs and timing, can link submissions to students. This is mitigated by publishing the bundle hash and key list, but not eliminated.
- The operator holds the signing keys and could mint ballots. This is an integrity limit, not an anonymity one.
- A student removed after tokens are issued keeps their tokens.
- `token_issuance` reveals which offerings a student received tokens for (already derivable from memberships). It does not reveal what or whom they rated, or whether they rated at all.
- **Ordering.** Transaction ids (`xmin`) and WAL commit records are globally ordered and timestamped, whatever `track_commit_timestamp` says, and `VACUUM FREEZE` does not erase them. Someone with database access can see which issuance came shortly before which submission. The client's next-day random delay weakens this; it does not remove it.
- **Small offerings.** If only one student of an offering was issued a token, that offering's only submission is theirs. k gates what the app shows, not what the database operator can read.
- Writing style can identify an author. Moderators reject identifying details.

### Claim
> Submissions are stored with no user identifier and no join key to one: no table, column or foreign key connects a rating to an account. Against a database leak this holds except for the ordering and small-offering cases above. It does not hold against deliberately modified client code or network logging, which we reduce by publishing the bundle hash and signing-key list.

## 4. Database

The full design (tables, keys, normal forms, constraints, indexes, connections and query rules) is in [DATABASE.md](DATABASE.md). This section covers access control and test data.

### Roles

- The migrator (the cluster's bootstrap user, `aciee`) runs Liquibase at startup and owns all objects, so it bypasses RLS as the owner. There is no separate NOLOGIN owner: `REFRESH MATERIALIZED VIEW` requires ownership, so `refresh_aggregates()` would have to run as the owner anyway, and a migrator that is a member of the owner role has the same power.
- The login roles below and their passwords are created by `infra/postgres/init-roles.sh` (run once by the Postgres image, and by Testcontainers). Grants, RLS and per-role limits are schema, in changeset `012-access`.
- `aciee_ident` writes `core` and `ident` and has no privileges on `anon`.
- `aciee_anon` writes `anon` and has no privileges on `ident`.
- `aciee_report` is read-only and can only read the report views.

The app uses two DataSources, so a SQL injection on one side cannot read the other. **Status (W2):** the ident pool is live; the anon pool is added with redemption in W3, the first code that needs it.

### History edge cases

| Case | Handling |
|---|---|
| Rating past years | `historical_claim` membership approved by the current group leader → tokens for those offerings |
| Teacher changes mid-semester | Close `valid_during`, add a new assignment row |
| Co-teaching | Two assignment rows |
| Subject renamed or merged | `title_snapshot` keeps the old name; `replaced_by_id` lets reports pool |
| Curriculum changes for new cohorts | A new `study_plan` with its own `intake` range; older cohorts keep theirs |
| Repeating a year | Membership in the new group with `kind = 'repeat'` |
| Repeating one course with another group | `offering_enrolment` for that offering only |
| Professor erasure request | Archive and anonymise the name; FKs restrict, nothing cascades into ratings |
| Academic year rollover | PL/pgSQL `rollover(ay)` copies groups to `study_year + 1`; leaders re-confirm |

### Row-level security and grants

**Where RLS applies.** On the ident pool only, because that is where a user id exists:
- `group_membership` and `membership_request`: students see their own rows; leaders see their group's rows while their leadership's membership is current, with `WITH CHECK`; admins see all (to revoke a leader and undo approvals).
- `token_issuance`: own rows only.
- Only a student can file their own request (`WITH CHECK user_id = me AND status = 'pending'`); only a leader or admin can decide one or insert a membership.
- Helper functions: `core.app_user_id()`, `core.is_admin()`, `core.leads_group(group)`. `leads_group` is `SECURITY DEFINER`: it reads `group_membership`, and a policy on `group_membership` that queried `group_membership` under RLS would recurse. `core.invite_uses(invite)` is `SECURITY DEFINER` for the same reason: a student must not see other students' requests, but the invite cap counts them.

`FORCE ROW LEVEL SECURITY` is not used: it only affects the table owner, and no app role owns a table. FK checks bypass RLS by design; no policy here relies on hiding a row from an FK check.

**Ratings.** There is no user to filter on, so RLS doesn't apply. The app reads `rating` only by `key_hash` (to edit one's own submission, which needs the secret `prepared_msg`) or through the k-gated materialised views. `aciee_report` sees only the views.

**Moderators** work on the anon pool through the `anon.moderation_queue` view, which omits `key_hash`. The app checks the moderator role on the ident side first. The full grant matrix is in DATABASE.md §2.

**Pool safety** (the per-request user id must never leak to the next request on a pooled connection):
- Every request is `@Transactional`, with `open-in-view=false`.
- The user id is set with `set_config('app.user_id', ?, true)`. The `true` makes it transaction-local, so it never survives in the pool.
- `UserTransactionManager` does this in `prepareTransactionalConnection`, right after `BEGIN`, so every `@Transactional` method gets it and the audit trigger records the actor.
- Spring Session gets its own plain transaction manager. Reading the security context inside `BEGIN` lazily loads the session from JDBC; through the same manager that load would open another transaction, read the context again, and recurse until the pool is empty.
- Policies read it as `nullif(current_setting('app.user_id', true), '')::integer`, wrapped in `(SELECT …)` so it is evaluated once per query, not once per row.

**Limit.** A superuser bypasses all of this. Protection against the operator comes from the protocol in §3.

### PL/pgSQL
- `rollover(ay)`.
- A generic statement-level audit trigger with transition tables (`to_jsonb` of each OLD/NEW row + `app.user_id`) on core tables.
- `ensure_audit_partitions(months_ahead)` and `retire_audit_partitions(keep_months)`.
- `anon.refresh_aggregates()`.
- The `study_year ≤ duration_years` trigger (DATABASE.md §5).
- Constraints, not triggers, enforce everything else.

### Fixtures
- Fixture changesets are formatted SQL under `db/changelog/fixtures/`. Two contexts share one parametrised script:
  - `fixtures`: about 2% of the volumes below, for development, CI and the demo environment.
  - `fixtures-large`: the full volumes, for Stage 4 only.
- They run only when `spring.liquibase.contexts` names one; prod names neither.
- Data comes from `generate_series` with `setseed`, followed by `ANALYZE` / `VACUUM`.
- Target volumes:

| Table | Rows |
|---|---|
| users | 60k |
| memberships | 220k |
| professors (with namesakes) | 600 |
| offerings | 17k |
| assignments | 60k |
| submissions | 1.5M |
| ratings | 3M |
| posts and versions | 300k |
| audit rows | 5M |

- Production never runs this profile.

## 5. Stage 4 evidence plan

**Query plans.** Capture `EXPLAIN (ANALYZE, BUFFERS)` before and after each index. Query numbers refer to DATABASE.md §6:
1. Radar for one assignment: no index, then `rating(teaching_assignment_id)`, then `INCLUDE (a01..a14)` to get an index-only scan (visibility map). The script creates and drops these; the schema has none of them.
2. Moderation queue (Q8): full vs. partial index `WHERE status = 'pending'` (compare sizes with `pg_relation_size`), and the same query with `status` as a bind parameter, where the partial index is not used.
3. "What can I still rate" (Q5): the temporal key's GiST index serving the lookup; `NOT EXISTS` vs. `NOT IN` vs. `LEFT JOIN … IS NULL`.
4. Audit history (Q13, Q14): run-time partition pruning, B-tree vs. BRIN size; per-row vs. statement-level audit trigger on a 10k-row update.
5. Materialised view vs. live aggregate (Q4).
6. Correlated columns: row estimates on `rating(teaching_assignment_id, offering_id)` before and after `CREATE STATISTICS`.
7. Pagination: `OFFSET` vs. keyset on the moderation queue.
8. Professor page: the shared CTE inlined vs. `MATERIALIZED`.

**Concurrency demo.** 200 virtual users redeem the **same token**, and exactly one succeeds. The demo progresses in four steps:
1. Naive check-then-insert, which lets duplicates through.
2. `FOR UPDATE`.
3. `SERIALIZABLE` with retries.
4. The primary key alone, which is the simplest correct fix.

**Load test.** k6 with `ramping-arrival-rate`, comparing p95 latency against HikariCP pool size.

## 6. Moderation filter

- **Normalise first:** lowercase, fold diacritics, map ş/ţ (cedilla) to ș/ț (comma), undo leetspeak and repeated letters.
- **Flag:** a hand-curated Romanian/English wordlist plus PII regexes (emails, phones, name pairs).
- The filter only flags and sorts the queue; a human always decides.

## 7. Repository layout

```
backend/     Spring Boot app; db/changelog/schema (schema), db/changelog/seed (real curriculum, every environment), db/changelog/fixtures (fixtures contexts only)
tools/       curriculum/plans_to_sql.py: study-plan PDFs → the seed changeset
frontend/    Vue 3 SPA; src/crypto holds all blind-RSA code + RFC 9474 test vectors
infra/       docker-compose.yml, Caddyfile, backup script
loadtest/    k6 scripts
docs/        specification, architecture, roadmap, decisions, AI log, explain-plan captures
```

## 8. Operations
- Two environments: `app.` holds real data with no fixtures; `demo.` is seeded with one account per role and reset nightly.
- A nightly `pg_dump` is copied off the server. The restore is tested before launch.
