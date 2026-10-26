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
