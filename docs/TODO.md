# To do

What is left for a person to do: things code cannot do, decisions only you can make, and checks nobody has run yet. Status as of 2026-11-07. Key dates: MVP Nov 22, traction target Dec 6, freeze Dec 7 (ROADMAP).

## Now

- [ ] Push the local commits (`6366943` reports, `150fc2a` Stage 4, `3ed0140` Microsoft sign-in). The remote is reachable only with the `ikeepcalm` gh account.
- [ ] Open `/admin/reports` and the sign-in / sign-up pages in a browser, including at 360 px. They were tested through the API and the build only, never looked at.
- [ ] Free local resources: `dropdb -h localhost -U aciee aciee_large` (full-volume Stage 4 data, several GB) and stop `infra` compose (`docker compose down`) when not developing.

## Deploy (DEPLOY.md)

- [ ] Buy the domain `acieeadvisor.ro`.
- [ ] Choose a mail provider and add SPF, DKIM and DMARC records, plus `A` records for `@`, `www` and `demo`.
- [ ] Create the deploy tool's resources for `acieeadvisor.ro` (context `prod`) and `demo.acieeadvisor.ro` (context `fixtures`) with every variable in DEPLOY.md, including `ACIEE_REPORT_PASSWORD`, which the app now uses for the report pool.
- [ ] First deploy, then grant yourself `admin` with the `psql` line in DEPLOY.md "First admin".
- [ ] Send a verification code from our own domain to a real `@student.ugal.ro` inbox and check it lands outside spam.
- [ ] Copy the `backups` **and** `token-keys` volumes off the server (S3 or Storage Box cron), then test a restore into the demo resource.
- [ ] Nightly demo reset (ARCHITECTURE §8): not built yet; decide between a deploy-tool scheduled redeploy with a fresh volume and a cron job that drops and recreates the demo database.

## Before ratings open on an environment

- [ ] `POST /api/admin/semesters/{id}/token-keys` for each semester to be rated.
- [ ] Put the base64 SHA-256 of that environment's `/anon/keys.json` into `frontend/src/crypto/pinned-keys.json` and redeploy the frontend. **The file is empty today, so no student on a deployed environment can get rating tokens until this is done.**
- [ ] Repeat both steps whenever a new batch of keys is created.

## Microsoft sign-in (code done, off by default; DEPLOY.md "Microsoft sign-in")

- [ ] Register the app in the UGAL Entra directory (single tenant) with the redirect URIs for prod, demo and localhost.
- [ ] Create a client secret; set `ENTRA_CLIENT_ID`, `ENTRA_CLIENT_SECRET` and `SPRING_PROFILES_ACTIVE=microsoft`.
- [ ] If students cannot consent themselves ("Need admin approval"), ask UGAL IT to grant consent once.
- [ ] Sign in with a real student account; also sign in with Microsoft to an existing password account and check the two link.
- [ ] Optional (ROADMAP "Checked so far"): check whether `/me/memberOf` exposes Teams groups without admin consent.

## Privacy and legal (ADR-012)

- [ ] Write `docs/LIA.md`, the legitimate-interest assessment ADR-012 refers to. It does not exist yet.
- [ ] Publish a contact route for objections and takedown requests (ADR-012); check whether the site has one.
- [ ] Have the university's DPO review the LIA before public launch.

## After pitch day: undo the ADR-027 shortcuts

- [ ] Second DataSource as `aciee_anon` for `/anon/**` and the anonymous reads; revoke the changeset `016` grants; restore `AccessTests.identPoolCannotReadAnonymousData`.
- [ ] Redeem at a random time the next day instead of on "Trimite".
- [ ] Weekly release of approved texts in `anon.published_post` (now the LATERAL view from changeset `018`).
- [ ] Nightly aggregate refresh instead of hourly.

## Launch and traction

- [ ] Recruit group leaders (open since W1) and approve their claims.
- [ ] Soft launch with your own group and 2–3 leaders, then the upperclassmen push (W5, Nov 23–29).
- [ ] Target by Dec 6: about 60 real users and at least 8 professor × subject pairs above k.

## Decisions waiting on you

From VIEWS.md "Open questions" (items 1–4 and 17 are decided):

- [ ] 5. What visitors see on the course page (survival data and tips hidden from them today).
- [ ] 6. Re-showing the recovery code from IndexedDB.
- [ ] 7. Starting over with a new recovery code after losing it.
- [ ] 8. Outbox delivery if the student never returns (matters once next-day redemption is back).
- [ ] 9. Re-rating after deleting a submission (the token can redeem again).
- [ ] 10. What an upheld report does to a post.
- [ ] 11. Rate limiting the Report button without a session (per-IP at nginx?).
- [ ] 12. Moderation queue order: flagged first or oldest first.
- [ ] 13. Who approves historical claims for a past group.
- [ ] 14. Archived professors: 404 or anonymised page.
- [ ] 15. Granting the moderator role: changeset or Admin section.
- [ ] 16. Comparator radar (comparator is dropped; close the question).

Timetable (`/timetable`, `/orar` redirects, synced hourly from aciee.ugal.ro/studenti/orare-licenta):

- [ ] Confirm with the secretariat or a group leader that the top half of a split slot in the PDF is the odd week. The page assumes so and says so in a footnote.
- [ ] `an_4_IETTI_EA.pdf` (linked from the semester II table) is an empty file on the faculty site; it is skipped with a warning until they replace it.

From ROADMAP.md: exact check-in dates per stage, and whether the faculty publishes the course descriptions (fișele disciplinelor).

## Course deliverables

- [ ] Fill the Commit column in `docs/AI_LOG.md` (all rows say "—").
- [ ] Stage 6 defence (40 %): rehearse explaining and changing any part by hand, especially the blind-signature flow, RLS, the Stage 4 numbers (`docs/explain-plans/README.md`) and the changeset `018` fix with its `OFFSET 0`.
- [ ] Freeze Dec 7, then demo environment check, rehearsal and a backup video (W7).
