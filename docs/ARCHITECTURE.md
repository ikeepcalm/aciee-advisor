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
  - Code: 6 digits, 15-minute expiry, stored hashed, 5 attempts.
  - Passwords: Argon2id (`Argon2PasswordEncoder`).
- **Microsoft sign-in (Entra OIDC)** as an alternative. Accept only the UGAL tenant `tid` and the `@student.ugal.ro` suffix.
- **Email normalisation**: lowercase, strip `+tag`, unique index on the normalised value.
- **Sessions**: server-side, Spring Session JDBC.
  - Cookie: `HttpOnly; Secure; SameSite=Lax`.
  - CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
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
   - The client blinds `msg` for **all** eligible offerings and sends them in one request.
   - The server rejects partial sets, checks memberships, records `token_issuance(user_id, semester_id)` and returns the blind signatures.
   - The client verifies the signatures and uploads an encrypted `token_vault` (AES-GCM under `HKDF(master)`) for multi-device recovery.
3. **Redeem** (anonymous: `credentials: 'omit'`, `/anon/*`, access logging off).
   - The request carries `offering_id, prepared_msg, sig, payload`.
   - The server verifies the RSASSA-PSS signature, then stores `key_hash = SHA-256(prepared_msg)`. `key_hash` is the primary key, which enforces one submission per token.
4. **Edit / delete** (anonymous). The client proves ownership by sending `prepared_msg`, which only it knows.
5. **No re-issue.** A lost recovery code means the student can no longer edit; the submission stays.

### Residual risks

These are stated openly, not hidden:
- An operator who ships modified JavaScript, or who logs IPs and timing, can link submissions to students. This is mitigated by publishing the bundle hash and key list, but not eliminated.
- The operator holds the signing keys and could mint ballots. This is an integrity limit, not an anonymity one.
- A student removed after tokens are issued keeps their tokens.
- `token_issuance` reveals that a student took part in a semester. It does not reveal what or whom they rated.
- Writing style can identify an author. Moderators reject identifying details.

### Claim
> Submissions are stored with no user identifier and no derivable link to one. The database (including a superuser's view, the WAL and backups) cannot connect a rating to an account. This holds against an honest-but-curious operator and a database leak. It does not hold against deliberately modified client code or network logging, which we reduce by publishing the bundle hash and signing-key list.

## 4. Database

### Schemas and roles

| Schema | Content | Writer role |
|---|---|---|
| `core` | users, structure, curriculum, professors, assignments, memberships, audit | `aciee_ident` |
| `ident` | `token_issuance`, `token_vault` | `aciee_ident` |
| `anon` | `offering_key`, `submission`, `rating`, `course_tip`, `teaching_comment` | `aciee_anon` |

Database roles:
- `aciee_owner` (NOLOGIN) owns all objects.
- `aciee_migrator` runs Liquibase and has BYPASSRLS.
- `aciee_ident` has no privileges on `anon`.
- `aciee_anon` has no privileges on `ident`.
- `aciee_report` is read-only and can only read the report views.

The app uses two DataSources, so a SQL injection on one side cannot read the other.

### Main tables

**Structure**
- `specialization(code UNIQUE, degree CHECK IN ('BSc','MSc'), duration_years)`
- `academic_year(during daterange, EXCLUDE gist (during WITH &&))`
- `semester(academic_year_id, no CHECK IN (1,2), during, rating_opens_at)`
- `study_group(specialization_id, academic_year_id, study_year, code, UNIQUE(academic_year_id, code))`
- `subgroup(group_id, label, UNIQUE(id, group_id))`

**Curriculum**
- `subject(code UNIQUE, name_ro, name_en, replaced_by_id)`
- `curriculum_item(specialization, subject, study_year, semester_no, is_elective, elective_package_id, lab_pct, exam_pct, project_pct, CHECK (lab_pct + exam_pct + project_pct = 100), valid_from_ay, valid_to_ay)`
- `course_offering(subject, semester, group, title_snapshot, UNIQUE(subject, semester, group))`

**Teaching**
- `professor(full_name, title, department, slug UNIQUE, archived_at)`
  - No unique constraint on the name, because namesakes exist.
- `teaching_assignment(offering_id, group_id, professor_id ON DELETE RESTRICT, component CHECK IN ('lecture','lab','seminar','project'), subgroup_id, valid_during)`
  - `CHECK (component <> 'lecture' OR subgroup_id IS NULL)`
  - A composite FK guarantees the subgroup belongs to the offering's group.
  - A lecture shared by several groups is one row per group's offering. Aggregation pools by professor × subject, so the duplicates don't distort results.

**Membership**
- `group_membership(user_id, group_id, subgroup_id, status, is_leader, kind CHECK IN ('regular','repeat','erasmus','historical_claim'), source, approved_by, valid_during)`
- `EXCLUDE USING gist (user_id WITH =, valid_during WITH &&) WHERE (status = 'approved')`
  - A student can't be in two groups at once, but transfers still work.

**Anonymous data**
- `anon.submission(key_hash PK, offering_id, semester_bucket, survival columns…)`
- `anon.rating(key_hash, teaching_assignment_id, a01..a14 smallint NULL CHECK (0..10), CHECK (num_nonnulls(a01..a14) >= 1))`
- `anon.course_tip(id uuid v4, key_hash, offering_id, body, status, flags, moderated_week)`
- `anon.teaching_comment(id uuid v4, key_hash, teaching_assignment_id, body CHECK (length(body) <= 280), status, flags, moderated_week)`

Rules for `anon` tables:
- No sequence ids and no `uuidv7()` (it embeds a timestamp).
- No exact timestamps.
- No audit trigger.
- `track_commit_timestamp = off`.

**The 14 axes are 14 columns, not an EAV table.**
- Aggregating them is one scan.
- Each axis gets a plain CHECK constraint.
- NULL handling matches SQL aggregate semantics.
- An `axis` lookup table holds the labels for the UI.

### History edge cases

| Case | Handling |
|---|---|
| Rating past years | `historical_claim` membership approved by the current group leader → tokens for those offerings |
| Teacher changes mid-semester | Close `valid_during`, add a new assignment row |
| Co-teaching | Two assignment rows |
| Subject renamed or merged | `title_snapshot` keeps the old name; `replaced_by_id` lets reports pool |
| Repeating a year or a course | Membership in another group with `kind='repeat'` |
| Professor erasure request | Archive and anonymise the name; FKs restrict, nothing cascades into ratings |
| Academic year rollover | PL/pgSQL `rollover(ay)` copies groups to `study_year + 1`; leaders re-confirm |

### Row-level security and grants

**Where RLS applies.** RLS with `FORCE ROW LEVEL SECURITY` is on:
- `group_membership`: students see their own rows; leaders see their group's rows, with `WITH CHECK`.
- `token_issuance`: own row only.
- Pending tips and comments: moderators only.

**Ratings.** There is no user to filter on, so RLS doesn't apply. Base tables are not readable by the app; it reads only the k-gated materialised view.

**Moderators** get column-level `SELECT` without `key_hash`.

**Pool safety** (the per-request user id must never leak to the next request on a pooled connection):
- Every request is `@Transactional`, with `open-in-view=false`.
- The user id is set with `set_config('app.user_id', ?, true)`. The `true` makes it transaction-local, so it never survives in the pool.
- Policies read it as `nullif(current_setting('app.user_id', true), '')::bigint`, wrapped in `(SELECT …)`.

**Limit.** A superuser bypasses all of this. Protection against the operator comes from the protocol in §3.

### Aggregates
- `MATERIALIZED VIEW prof_subject_radar`, grouped by `(professor_id, coalesce(replaced_by_id, subject_id))`.
- `HAVING count(*) >= 5`, values rounded.
- Has a unique index, so it can be refreshed with `REFRESH MATERIALIZED VIEW CONCURRENTLY` nightly.
- No trigger-maintained totals: they cause hot-row contention and leak a single rating when the average changes.

### Partitioning
- Only `audit_log` is partitioned: range by `created_at`, monthly, plus a DEFAULT partition, with PK `(id, created_at)`.
- **Why:** old months are retained or dropped by `DROP PARTITION`, and queries benefit from pruning.

### PL/pgSQL
- `rollover(ay)`.
- A generic audit trigger (`to_jsonb(OLD/NEW)` + `app.user_id`) on core tables.
- A trigger checking the MSc year bound.
- Constraints, not triggers, enforce uniqueness.

### Fixtures
- Fixture changesets are formatted SQL under `db/changelog/fixtures/`, tagged `context:fixtures`. They run only when `spring.liquibase.contexts` includes `fixtures`; prod leaves it out.
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
| tips and comments | 300k |
| audit rows | 5M |

- Production never runs this profile.

## 5. Stage 4 evidence plan

**Query plans.** Capture `EXPLAIN (ANALYZE, BUFFERS)` before and after each index:
1. Radar for one assignment: `INDEX ON anon.rating(teaching_assignment_id)`, then `INCLUDE (a01..a14)` to get an index-only scan (visibility map).
2. Moderation queue: partial index `WHERE status = 'pending'` (compare sizes with `pg_relation_size`).
3. "What can I still rate": partial index on approved memberships.
4. Audit history: run-time partition pruning.
5. Materialised view vs. live aggregate.

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
backend/     Spring Boot app; db/changelog/schema (schema), db/changelog/fixtures (context:fixtures only)
frontend/    Vue 3 SPA; src/crypto holds all blind-RSA code + RFC 9474 test vectors
infra/       docker-compose.yml, Caddyfile, backup script
loadtest/    k6 scripts
docs/        specification, architecture, roadmap, decisions, AI log, explain-plan captures
```

## 8. Operations
- Two environments: `app.` holds real data with no fixtures; `demo.` is seeded with one account per role and reset nightly.
- A nightly `pg_dump` is copied off the server. The restore is tested before launch.
