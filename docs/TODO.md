# To do

What is left for a person to do: things code cannot do, decisions only you can make, and checks nobody has run yet. Status as of 2026-11-07. Key dates: MVP Nov 22, traction target Dec 6, freeze Dec 7 (ROADMAP).

## Now

- [ ] Push the local commits (`6366943` reports, `150fc2a` Stage 4, `3ed0140` Microsoft sign-in). The remote is reachable only with the `ikeepcalm` gh account.
- [ ] Open `/admin/reports` and the sign-in / sign-up pages in a browser, including at 360 px. They were tested through the API and the build only, never looked at.
- [ ] Free local resources: `dropdb -h localhost -U aciee aciee_large` (full-volume Stage 4 data, several GB) and stop `infra` compose (`docker compose down`) when not developing.

## After the Doodle redesign (2026-11-11)

- [ ] Have a native speaker read `uk.json` and `ru.json`. Flagged by the translators: the "(а)" / "(ла)" gender endings in the recovery messages, the odd/even week chips (НЕП/ПАР, НЕЧ/ЧЁТ), and "Survival" in the reports section rendered literally in Ukrainian.
- [ ] Subject names, titles, components and other `label_ro` values come from the database in Romanian only, so they stay Romanian in en/uk/ru. Decide whether that is acceptable or the reference tables need translated labels.
- [ ] Course and professor pages (ratings, comments, tips) are now public. Watch traffic; if scraping or load shows up, cache `radars()`/`comments()` per subject and year (identical for every viewer) or rate-limit `/api/catalog/courses/*` and `/api/professors/*`. Check that ADR-012 / the LIA covers public visibility of comments.
- [ ] Update `docs/DESIGN.md`: it still describes the old circuit-board style (Chakra Petch, blue board palette). The site now uses the paper style: Caveat 700 display, IBM Plex Sans body, ink/ballpoint/marker tokens in `frontend/src/app.css`.
- [ ] Look at the views never checked in the new style: Join, Verify, Recovery, Submissions, Moderation, Admin leaders/history/reports. Also try Safari, Firefox and a real phone, and the theme and language animations in Safari (view transitions).
- [ ] Optional: preload the Caveat latin woff2 in `index.html` to cut the heading font swap; restyle the timetable picker and day chips with the shared `.chip` / `.chip-on` classes instead of their local copies.

## Traffic features (agreed 2026-11-12)

Public tools that need no account and lead into ratings with one small link each. Build in this order:

- [x] Link previews: course and professor links shared in WhatsApp/Telegram show a card in the paper style with the key public metrics (same 5-rating rule as the page); other pages get a default card.
- [x] Calendar subscription: an ICS feed per group/subgroup; each event links to its course page, and a "rate your courses" event in the exam session.
- [x] Catch-up finder: for one of my classes, the other groups' slots with the same teacher, same class type and same course this and next week, with clashes greyed out; "show all groups" toggle.
- [x] Add to home screen (PWA) with the timetable available offline. Still to do: an install hint in the UI (Android install prompt, iOS "Share → Add to Home Screen" note).
- [ ] Timetable change alerts: backend done (`core.timetable_change`, `GET /api/timetable/changes`); the "Orarul s-a schimbat" note on the timetable page is still to build.
- [ ] Share my week as an image, from a "…" → Share menu (not a headline feature); the image names the site and the timetable's last change.
- [ ] First-year guide (restanță, credite, bursă, sesiune, attendance marks), after everything above.

Week parity comes from the semester row: the week holding the first day is week 1 (odd), weeks that are a break on all five weekdays are skipped (`core.semester.breaks`, a `datemultirange` such as `{[2026-12-22,2027-01-05)}`, edited in Admin → semester), and classes end the day before `rating_opens_at`. **Enter the real breaks for every semester**, or parity flips after the winter holiday. Without a semester row the timetable falls back to the old one-tap calibration and the calendar feed is unavailable. Known limits: a break that starts mid-week still lists that week's remaining classes. A timetable cell holding two courses by weeks ("SO S1-7 / POO S8-14") stays one class. Test real subscriptions in Google/Apple/Outlook and the WhatsApp/Telegram preview once deployed (needs public https; set `PUBLIC_URL`).

Deferred: "Where is room X?" (campus guide; 3D models from your campus knowledge), exam schedule (check whether the faculty publishes parseable PDFs first), free rooms (we only see ACIEE rooms, other faculties use them too), grade calculator (dropped).

AI review checking (plan agreed, not built): Gemini free tier; harsh-but-fair publishes, rude goes to human review; shadow mode first.

## Professors

- [x] Professor page shows an overall score (every answered axis weighted by its number of ratings) and the three pillar scores, the years each discipline was taught and an academic-year label on comments; `/professors` lists everyone with search.
- [x] CTI department CV profiles: 13 CVs from cti.ugal.ro/structura-departament-cti summarised (ro + en) into `seed/003-professor-profiles.sql`, 9 portraits cut from the PDFs into `frontend/public/professors/`. A profile matches a professor by `core.name_key(full_name)` (sorted tokens, no diacritics), so admins must type the same first and last names; middle names break the match.
- [ ] The other departments (automatica.ugal.ro, iesce.ugal.ro, etc.ugal.ro) were not looked at; repeat the same CV pass when their staff pages list CVs. The CTI page itself is from March 2024. Consider a yearly re-run, ideally with the Gemini key planned for moderation.

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

- [x] 5. What visitors see on the course page: everything public (decided in `c12b29d`, "make course and professor reviews public").
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
