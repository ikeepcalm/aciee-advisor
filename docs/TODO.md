# To do

What is left for a person to do: things code cannot do, decisions only you can make, and checks nobody has run yet. Status as of 2026-11-07. Key dates: MVP Nov 22, traction target Dec 6, freeze Dec 7 (ROADMAP).

## Now

- [x] First-year guide (`/guide`) rebuilt 2026-11-13 as a scroll story: greeting, one step per topic with a doodle, senior-student tips, a one-step-per-scroll deck on desktop and Next buttons on mobile. Still to check on a real trackpad, Safari, Firefox and a phone; uk/ru tips need a native read.
- [ ] Check the "Orarul s-a schimbat" note yourself: insert two rows into `core.timetable_change` for your group (one `removed`, one `added` with another room), open `/timetable`, then delete them.
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
- [x] Timetable change alerts: a dismissible "Orarul s-a schimbat" note on the timetable page, with a sheet that pairs removed and added slots into "modified" lines. Seen state is per device (localStorage).
- [x] Share my week as an image from the timetable "…" menu (canvas PNG, native share sheet or download). The native share sheet and Cyrillic text in the image are untested on a real phone.
- [x] First-year guide at `/guide`, content in `frontend/src/guide/content.ts` (ro, en, uk, ru), checked 2026-11-12 against RAUS 2024, the 2026 scholarship rules, the 2026-2027 fees and calendar. Re-check every autumn; unconfirmed: free re-attempts, grade-improvement fee, scholarship amounts, reclassification criteria, student representatives.

Week parity comes from the semester row: the week holding the first day is week 1 (odd), weeks that are a break on all five weekdays are skipped (`core.semester.breaks`, a `datemultirange` such as `{[2026-12-22,2027-01-05)}`, edited in Admin → semester), and classes end the day before `rating_opens_at`. **Enter the real breaks for every semester**, or parity flips after the winter holiday. Without a semester row the timetable falls back to the old one-tap calibration and the calendar feed is unavailable. Known limits: a break that starts mid-week still lists that week's remaining classes. A timetable cell holding two courses by weeks ("SO S1-7 / POO S8-14") stays one class. Test real subscriptions in Google/Apple/Outlook and the WhatsApp/Telegram preview once deployed (needs public https; set `PUBLIC_URL`).

Deferred: "Where is room X?" (campus guide; 3D models from your campus knowledge), exam schedule (check whether the faculty publishes parseable PDFs first), free rooms (we only see ACIEE rooms, other faculties use them too), grade calculator (dropped).

AI review checking (plan agreed, not built): Gemini free tier; harsh-but-fair publishes, rude goes to human review; shadow mode first.

## Professors

- [x] Professor page shows an overall score (every answered axis weighted by its number of ratings) and the three pillar scores, the years each discipline was taught and an academic-year label on comments; `/professors` lists everyone with search.
- [x] CTI department CV profiles: 13 CVs from cti.ugal.ro/structura-departament-cti summarised (ro + en), 9 portraits cut from the PDFs. Kept out of the repository (DEPLOY.md "Professor profiles") and loaded on the server. A profile matches a professor by `core.name_key(full_name)` (sorted tokens, no diacritics), so admins must type the same first and last names; middle names break the match.
- [ ] The other departments (automatica.ugal.ro, iesce.ugal.ro, etc.ugal.ro) were not looked at; repeat the same CV pass when their staff pages list CVs. The CTI page itself is from March 2024. Consider a yearly re-run, ideally with the Gemini key planned for moderation.

## Deploy (DEPLOY.md)

- [x] Buy the domain: `acieeadvisor.com`; `.ro` later if registration as a non-resident works.
- [x] Choose a mail provider: Resend (SMTP 587, user `resend`, API key as password); DKIM and `send` records in DNS. Still to add: `_dmarc` TXT.
- [x] Create the environment for `acieeadvisor.com` (context `prod`) with every variable in DEPLOY.md: live since 2026-11-16 with Resend mail.
- [ ] Retire the demo (2026-11-23): stop and delete the Coolify app `aciee-advisor-demo`, remove the host nginx site `acieeadvisor-demo` and its certificate, and delete the `demo` DNS record. The show code is gone from the repo; the recordings are at `/preview`.
- [x] First deploy, then grant yourself `admin` with the `psql` line in DEPLOY.md "First admin".
- [x] Send a verification code from our own domain to a real `@student.ugal.ro` inbox (Resend, 2026-11-16: arrived).
- [ ] Copy the `backups` **and** `token-keys` volumes off the server (object storage or a cron sync to another machine), then test a restore into a scratch database.

## Before ratings open on an environment

- [ ] Enter the 2026-2027 semesters in Admin → semester (from the official calendar, HS 59/2026): semester 1 `during` `[2026-09-28,2027-02-15)`, `breaks` `{[2026-12-19,2027-01-11)}`, `rating_opens_at` 2027-01-23; semester 2 `during` `[2027-02-22,2027-07-31)`, `breaks` `{[2027-04-30,2027-05-10)}`, `rating_opens_at` 2027-06-05. Odd/even weeks and the calendar feed depend on these.
- [ ] Decide when semester 1 opens for ratings (`rating_opens_at`): now, or after the semester (2027-01-23). Past-year ratings are not possible yet (no historical-claim flow).
- [ ] After the semesters exist, press Admin → study groups → "Sync from timetable" (or wait for the hourly run): it creates this year's Calculatoare and Automatică groups, their courses, teachers and signing keys. Other programmes need their curriculum seeded first.
- [ ] Back up the `token-keys` volume once the keys exist.

## Microsoft sign-in (code done, off by default; DEPLOY.md "Microsoft sign-in")

- [ ] Register the app in the UGAL Entra directory (single tenant) with the redirect URIs for prod and localhost.
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

- [x] The top half of a split slot in the PDF is the odd week (confirmed by the owner from first-year experience, 2026-11-13).
- [ ] `an_4_IETTI_EA.pdf` (linked from the semester II table) is an empty file on the faculty site; it is skipped with a warning until they replace it.

From ROADMAP.md: exact check-in dates per stage, and whether the faculty publishes the course descriptions (fișele disciplinelor).

## Course deliverables

- [ ] Fill the Commit column in `docs/AI_LOG.md` (all rows say "—").
- [ ] Stage 6 defence (40 %): rehearse explaining and changing any part by hand, especially the blind-signature flow, RLS, the Stage 4 numbers (`docs/explain-plans/README.md`) and the changeset `018` fix with its `OFFSET 0`.
- [ ] Freeze Dec 7, then rehearsal (W7). The backup video exists: `/preview` (`frontend/public/preview/tour-*.mp4`).
