# ACIEE Advisor — Roadmap

## Course stages

| Stage | Weight | Deliverable | Where |
|---|---|---|---|
| 0 | 10% | Problem domain + spec for every view | `docs/SPECIFICATION.md` |
| 1 | 10% | ≥ 6 tables with PK/FK/integrity rules + fixtures seeded on startup at a volume where indexes matter | `backend/src/main/resources/db/` |
| 2 | 10% | CRUD for all core entities | backend + frontend |
| 3 | 10% | Authentication explained + ≥ 3 roles with granular access (RLS/DCL) | `docs/ARCHITECTURE.md` §2, §4 |
| 4 | 10% | Indexes justified with `EXPLAIN ANALYZE` before/after + API load test | `docs/explain-plans/`, `loadtest/` |
| 5 | 10% | PDF/CSV/Excel reports from database queries | backend |
| 6 | 40% | Live defence: explain and modify any component by hand | — |

Also required throughout the semester: consistent git history, `docs/DECISIONS.md` and `docs/AI_LOG.md`.

## Scope tiers

**A — MVP, feature-complete by Nov 22**
- Real curriculum seed: Calculatoare plus 1–2 more programmes.
- Authentication, groups, leader approval.
- Historical claims. The current semester can only be rated once it ends, so at launch all real ratings come from past semesters.
- Blind tokens, the 14-axis rating, the k-locked radar.
- Survival metadata, course tips, teaching comments, the moderation queue, the Report button.

**After the MVP (Nov 23 – Dec 13)**
- Stage 4 evidence and Stage 5 reports (aggregate-only).
- Elective comparator.
- Microsoft sign-in.

**C — cut**
- Professor accounts, appeals, university sync, AI moderation.

## Timeline

| Week | Dates | Work | Stage |
|---|---|---|---|
| W1 | Oct 26 – Nov 1 | View specs (Stage 0). Repo, compose, CI. Full Liquibase schema. Authentication with emailed codes. Register our own Entra app and test sign-in with a student account. Recruit group leaders. | 0 |
| W2 | Nov 2 – 8 | Real curriculum seed, synthetic fixtures. Admin CRUD, roles, RLS. Group claim, invite and approval. First deploy. | 1–3 |
| W3 | Nov 9 – 15 | Blind tokens end to end (RFC test vectors first), historical claims, rating flow, materialised view and k-lock. | 2 |
| W4 | Nov 16 – 22 | Survival metadata, tips, comments, moderation, Report button. Course and professor pages at 360px. **MVP done Nov 22.** | — |
| W5 | Nov 23 – 29 | Soft launch with own group and 2–3 leaders, then upperclassmen push. EXPLAIN captures, k6 runs. | 4 |
| W6 | Nov 30 – Dec 6 | Reports. Elective comparator, Microsoft sign-in. User push continues. | 5 |
| W7 | Dec 7 – 13 | Freeze Dec 7. Fixes, demo environment, rehearsal, backup video. | — |

**Cut lines**
- If blind tokens don't pass the RFC test vectors by **Nov 12**, stop adding features and finish tokens first. They are the core of the product.
- If moderation isn't done by **Nov 22**, launch with ratings and structured survival data only, text disabled. Turn text on after launch.

**Traction target by Dec 6:** about 60 real users and at least 8 professor × subject pairs above k.

## Status

**W1 (Oct 28).** Done: view specs (`docs/VIEWS.md`), the full Liquibase schema (`002`–`010`), sign-up / emailed code / sign-in / sign-out with JDBC sessions, and the auth screens. Mailpit in compose for local mail. Still open: registering our own Entra app and testing a student sign-in, and recruiting group leaders. Moved to W2 with the rest of access control: database roles, grants, RLS, per-role timeouts and the two pools.

**W2 (Oct 31), in progress.** Done: login roles (`infra/postgres/init-roles.sh`), grants, RLS on memberships, requests and issuance, per-role timeouts (changeset `012`); the app runs as `aciee_ident` with `app.user_id` set per transaction. Group leader claims (admin approval, which also admits the claimant to an empty group), invite links with caps, join requests and leader approval with the `max_size` lock (changeset `013`), with screens. Still open: real curriculum seed, synthetic fixtures, admin CRUD for curriculum, calendar, groups, professors and assignments, the leader's subgroup → assistant mapping, first deploy. The anon pool moves to W3 with redemption.

## Checked so far

- **Email delivery:** mail from an outside personal address reaches a `@student.ugal.ro` inbox. Still to test: mail from our own sending domain.
- **Microsoft sign-in:** a student account can sign in to a third-party app. That app may be pre-approved by the tenant admin, so the W1 test with our own app registration is the real check.
- **Shared lectures:** one lecture can cover several groups. It is modelled as one assignment row per group, pooled by professor × subject.

## Open questions
- Exact check-in dates for each stage.
- Whether the faculty publishes per-course descriptions (fișele disciplinelor) for ACIEE.
