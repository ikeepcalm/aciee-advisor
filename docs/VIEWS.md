# ACIEE Advisor — View Specifications

Stage 0 deliverable: one section per view from [SPECIFICATION §6](SPECIFICATION.md#6-views), plus the authentication views. Each section gives the route, who can see it, a mobile wireframe, the data it reads and the actions it offers.

Conventions:
- Wireframes are drawn at 40 characters, about a 360px phone. UI text is Romanian (`ro-RO`, vue-i18n); this document is English.
- Table and query names come from [DATABASE.md](DATABASE.md); `Q1`..`Q16` refer to its §6 query catalogue.
- Endpoint paths are provisional names for Stage 2. `/api/*` is authenticated (session cookie, CSRF header) and runs on the ident pool. `/anon/*` is called with `credentials: 'omit'`, has access logging off and runs on the anon pool (ARCHITECTURE §3).
- `k` is `ref.setting.k_threshold` (5 today). Every locked figure reads `n` from the same materialised-view row (ADR-022); nothing is counted live.

## 0. Common layout and roles

| Role | How it is known | Note |
|---|---|---|
| Visitor | no session | |
| Student | session; every account is `@student.ugal.ro` | |
| Group leader | `group_leader` row whose membership is current (`valid_during @> current_date`) | scoped to one group and year |
| Moderator | `user_role.role_code = 'moderator'` | also a student |
| Admin | `user_role.role_code = 'admin'` | also a student |

Routes for a role the user lacks redirect: a visitor goes to `/signin?next=<route>`, a signed-in user without the role gets a 403 page ("Nu ai acces la această pagină").

```text
+--------------------------------------+
| ACIEE Advisor              [Meniu ≡] |
+--------------------------------------+
| Meniu (vizitator):                   |
|   Cursuri                            |
|   Autentificare | Creează cont       |
| Meniu (student), în plus:            |
|   Evaluează                          |
|   Contribuțiile mele                 |
|   Grupa mea                          |
|   Cod de recuperare                  |
|   Moderare      (doar moderator)     |
|   Administrare  (doar admin)         |
|   Deconectare                        |
+--------------------------------------+
```

- Sign out: `POST /api/auth/signout`, invalidates the Spring session. Local IndexedDB (master secret, tokens) is kept; it belongs to the device, not the session.

## 1. Sign up

**Route** `/signup`. Visitors only; a signed-in user is redirected to `/`.

```text
+--------------------------------------+
| ACIEE Advisor                        |
+--------------------------------------+
| Creează cont                         |
|                                      |
| E-mail instituțional                 |
| [ prenume.nume@student.ugal.ro     ] |
| Parolă                               |
| [ ••••••••••                       ] |
| Confirmă parola                      |
| [ ••••••••••                       ] |
|                                      |
| [        Creează cont            ]   |
|                                      |
| ---------------- sau --------------- |
| [  Continuă cu Microsoft         ]   |
|                                      |
| Ai deja cont? Autentifică-te         |
+--------------------------------------+
```

**Data written.** `core.user_account` (`email`, generated `email_normalized`, Argon2id `password_hash`), `core.email_verification` (`code_hash`, `expires_at = now + 15 min`, `attempts = 0`).

**Actions**

| Control | Calls | Validation |
|---|---|---|
| Creează cont | `POST /api/auth/signup` → redirect to `/verify` | Client and server: address ends in `@student.ugal.ro` (also a CHECK on `user_account`); passwords match; password policy (see Open questions). Server normalises (lowercase, strip `+tag`) and relies on the unique index on `email_normalized` (Q15). |
| Continuă cu Microsoft | `GET /api/auth/oauth2/microsoft` (Entra OIDC, PKCE), callback `/api/auth/oauth2/code/microsoft` | Shown when `GET /api/auth/providers` answers `microsoft: true`. Server accepts only the UGAL tenant `tid` and the `@student.ugal.ro` suffix, then redirects to `/`; a refusal redirects to `/signin?error=<code>` (`microsoft_tenant`, `microsoft_domain`, `microsoft_conflict`, `microsoft_failed`, `disabled`). |

**States**
- Wrong domain: "Folosește adresa ta @student.ugal.ro."
- Passwords differ: "Parolele nu coincid."
- Mail send failure: "Ceva nu a mers. Încearcă din nou." The sign-up transaction rolls back, so the user simply signs up again.
- Address already verified: 409, "Există deja un cont cu această adresă. Autentifică-te." An unverified address can be signed up again; the new password replaces the old one.

## 2. Sign in

**Route** `/signin?next=<route>`. Visitors only.

```text
+--------------------------------------+
| ACIEE Advisor                        |
+--------------------------------------+
| Autentificare                        |
|                                      |
| E-mail instituțional                 |
| [ prenume.nume@student.ugal.ro     ] |
| Parolă                               |
| [ ••••••••••                       ] |
|                                      |
| [        Autentifică-te          ]   |
|                                      |
| ---------------- sau --------------- |
| [  Continuă cu Microsoft         ]   |
|                                      |
| Nu ai cont? Creează cont             |
+--------------------------------------+
```

**Data read.** `core.user_account` by `email_normalized` or `entra_oid` (Q15), `core.user_role`, `core.group_leader` for the menu.

**Actions**

| Control | Calls | Result |
|---|---|---|
| Autentifică-te | `POST /api/auth/signin` | Success → session created (Spring Session JDBC), redirect to `next` or `/`. Account not yet verified → a fresh code is sent and the user goes to `/verify`. |
| Continuă cu Microsoft | `GET /api/auth/oauth2/microsoft` | Same as on Sign up. An Entra account has no password; `entra_oid` identifies it. A first Microsoft sign-in with the address of an existing account links the two (ARCHITECTURE §2). |

**States**
- Wrong email or password, one message for both: "E-mail sau parolă greșită."
- `disabled_at` set: "Contul este dezactivat."

## 3. Verify email code

**Route** `/verify`. Reached only from Sign up or Sign in, with a pending `email_verification` row.

```text
+--------------------------------------+
| ACIEE Advisor                        |
+--------------------------------------+
| Verifică adresa de e-mail            |
|                                      |
| Am trimis un cod de 6 cifre la       |
| prenume.nume@student.ugal.ro.        |
| Codul expiră în 15 minute.           |
|                                      |
|  [ _ ][ _ ][ _ ][ _ ][ _ ][ _ ]      |
|                                      |
| [         Verifică               ]   |
|                                      |
| Nu ai primit codul? Retrimite        |
| Încercări rămase: 5                  |
+--------------------------------------+
```

**Data.** `core.email_verification(user_id, code_hash, expires_at, attempts)`. One pending code per user.

**Actions**

| Control | Calls | Validation and result |
|---|---|---|
| Verifică | `POST /api/auth/verify` | Exactly 6 digits, plus the password (kept in memory from Sign up / Sign in; a password field appears if the page was reloaded). Server increments `attempts`, then checks the code and the password. Both correct → row deleted, session created, redirect to `next` or `/`. |
| Retrimite | `POST /api/auth/verify/resend` | Replaces the pending row with a new code, new 15-minute expiry and `attempts = 0`, at most once a minute (429 otherwise). Answers 202 for unknown addresses too. The mail is plain text, code only, no link. |

**States**
- Wrong code: "Cod greșit. Încercări rămase: N."
- 5 wrong attempts: the code is void. "Ai depășit numărul de încercări. Cere un cod nou."
- Expired: "Codul a expirat. Cere un cod nou."

## 4. Landing / catalogue

**Route** `/`, with `?spec=<code>&plan=<id>&year=<n>` kept in the URL so a filtered list can be shared.

**Who.** Everyone sees the same page. It shows only curriculum, never ratings.

```text
+--------------------------------------+
| ACIEE Advisor              [Meniu ≡] |
+--------------------------------------+
| Ghidul cursurilor ACIEE              |
| Află ce cere fiecare curs, de la     |
| studenții din anii mai mari.         |
|                                      |
| Specializare                         |
| [ Calculatoare (BSc)             ▾ ] |
| Plan de învățământ                   |
| [ Admiși 2024–2026               ▾ ] |
|                                      |
| ( An I ) ( An II ) (An III) ( An IV )|
|                                      |
| Semestrul 1                          |
| +----------------------------------+ |
| | Programarea calculatoarelor   >  | |
| | Lab 40% · Examen 60%             | |
| +----------------------------------+ |
| | Analiză matematică            >  | |
| | Examen 70% · Proiect 30%         | |
| +----------------------------------+ |
| Pachet opțional 1                    |
| [ Compară opționalele ]  (student)   |
| +----------------------------------+ |
| | Rețele neuronale              >  | |
| | Grafică pe calculator         >  | |
| +----------------------------------+ |
| Semestrul 2                          |
| ...                                  |
+--------------------------------------+
```

**Data read**
- `core.specialization`, `core.study_plan` (default: the plan whose `intake` contains the current intake year).
- `core.curriculum_item` joined to `core.subject` for the plan, year and semester (Q1).
- Elective packages: items sharing (`plan_id`, `study_year`, `semester_no`, `elective_slot`), shown together under "Pachet opțional N".

**Actions**
- Change specialisation, plan or year: reloads Q1, no server write.
- Tap a course → `/courses/:itemId`.
- "Compară opționalele" → `/electives/compare?items=<ids>` with the package's items. Shown to students only; a visitor tapping it goes to `/signin`.

**States**
- Plan with no items for that year: "Nu există cursuri pentru acest an în planul selectat."
- Specialisation with no seeded plans: "Planul de învățământ nu a fost încă introdus."

## 5. Course page

**Route** `/courses/:itemId` (a `curriculum_item`, so the page knows plan, year and semester).

**Who**

| Role | Sees |
|---|---|
| Visitor | Header, list of teaching assignments (professor, component). A sign-in prompt replaces survival data, radars and tips. |
| Student and above | Everything below. |

```text
+--------------------------------------+
| < Înapoi                   [Meniu ≡] |
+--------------------------------------+
| Programarea calculatoarelor          |
| Calculatoare · An I · Sem. 1         |
|                                      |
| Cum se trece (din 23 evaluări)       |
| Dificultate examen        6.8 / 10   |
| Cât ajută laboratorul     7.4 / 10   |
| Prezența la curs     utilă parțial   |
| Ore pe săptămână          5.2        |
| Format examen        practic         |
| Strictețe prezență        8.1 / 10   |
|                                      |
| Profesori                            |
| An universitar [ 2025–2026       ▾ ] |
| +----------------------------------+ |
| | Curs: Conf. dr. Ion Popescu    > | |
| |  [ radar 14 axe ]   n = 23       | |
| |  Comentarii (3)              ▾   | |
| +----------------------------------+ |
| | Lab: As. dr. Ana Ionescu       > | |
| |  Blocat: mai sunt necesare       | |
| |  2 evaluări                      | |
| +----------------------------------+ |
|                                      |
| Sfaturi de la colegi (4)             |
| +----------------------------------+ |
| | "Examenul e pe calculator, se    | |
| | lucrează cu pointeri..."         | |
| | Săpt. 12 oct. 2026  [Raportează] | |
| +----------------------------------+ |
|                                      |
| [ Evaluează acest curs ]  (eligibil) |
+--------------------------------------+
```

Visitor variant of the middle part:

```text
| Cum se trece                         |
| +----------------------------------+ |
| | Autentifică-te cu adresa         | |
| | @student.ugal.ro ca să vezi      | |
| | evaluările și sfaturile.         | |
| | [ Autentificare ]                | |
| +----------------------------------+ |
```

**Data read**
- Header: `core.curriculum_item`, `core.subject`, `core.study_plan`, `core.specialization`.
- Offerings of the subject (Q2) through `core.offering_v`, filtered by the chosen academic year; default the latest year with an offering.
- Assignments of those offerings (Q3): `core.teaching_assignment` → `core.professor` → `ref.academic_title`, `ref.component`. Lab and seminar rows show the subgroup label. Professors repeated across groups (ADR-010) appear once per component.
- Survival block: `anon.course_survival` for the root subject. Weights stay the seeded ones from `curriculum_item`.
- Radar per professor: `anon.prof_subject_radar` row for (`professor_id`, root subject) (Q4). Axis labels from `ref.axis`, grouped by `ref.pillar`.
- Tips: `anon.published_post` where `kind = 'tip'` for the subject's offerings (Q9). Comments: `anon.published_post` where `kind = 'comment'` for the professor's assignments (Q9); the view already hides comments whose pair is below k.

**Actions**

| Control | Calls | Note |
|---|---|---|
| Academic year selector | reloads Q2/Q3 | Radars and survival data do not change: they are pooled across semesters. |
| Professor row → | `/professors/:slug` | students only |
| Comentarii ▾ | expands the comments already loaded | |
| Raportează | `POST /anon/posts/:id/reports` with a reason from `ref.report_reason` | Writes `anon.post_report` (`status = 'open'`, `reported_week`). No reporter id is stored. Confirmation: "Mulțumim. Un moderator va verifica." |
| Evaluează acest curs | `/rate/:offeringId` | Shown only when the student has an eligible, not yet submitted offering of this subject (from the Rate view's local state). |

**States**
- Radar locked (`n < k`): "Blocat: mai sunt necesare {k − n} evaluări". With `n = 0`: "Încă nu există evaluări."
- Survival locked: same rule and wording on `anon.course_survival.n`.
- No published tips: "Încă nu există sfaturi publicate."
- Values are as of the last nightly refresh. Footer note: "Actualizat zilnic."

## 6. Professor page

**Route** `/professors/:slug` (`professor.slug` is unique). Students and above; visitors are redirected to `/signin`.

```text
+--------------------------------------+
| < Înapoi                   [Meniu ≡] |
+--------------------------------------+
| Conf. dr. Ion Popescu                |
| Departamentul Calculatoare și        |
| Tehnologia Informației               |
|                                      |
| Discipline predate                   |
| +----------------------------------+ |
| | Programarea calculatoarelor    > | |
| | Curs, Lab · n = 23               | |
| |  [ radar 14 axe ]                | |
| |  Predare și organizare     7.9   | |
| |  Laboratoare               8.2   | |
| |  Notare și comunicare      6.5   | |
| |  Comentarii (3)              ▾   | |
| |  "Cursurile sunt structurate pe  | |
| |  săptămâni, materialele..."      | |
| |  Săpt. 5 oct. 2026 [Raportează]  | |
| +----------------------------------+ |
| | Structuri de date              > | |
| | Curs · n = 3                     | |
| |  Blocat: mai sunt necesare       | |
| |  2 evaluări                      | |
| +----------------------------------+ |
+--------------------------------------+
```

**Data read**
- `core.professor`, `ref.academic_title`, `core.department`.
- Subjects taught: `core.teaching_assignment` by `professor_id` (Q16 index) → `core.offering_v`, root subject.
- Radars: `anon.prof_subject_radar` by `professor_id` (Q4). Pillar averages are shown under the radar for screen readers and small screens.
- Comments: `anon.published_post` for the professor's assignments (Q9). The set of k-passed pairs is computed once in a `MATERIALIZED` CTE shared by the radar and comment queries (DATABASE §10).

**Actions**
- Subject row → `/courses/:itemId` (the item in the viewer's own plan when there is one, else the latest).
- Raportează on a comment: as on the course page.

**States**
- Pair locked: "Blocat: mai sunt necesare {k − n} evaluări". Comments for that pair are not shown at all.
- No assignments: "Acest profesor nu are discipline înregistrate."
- Archived professor (`archived_at` set): see Open questions.

## 7. Elective comparator

**Route** `/electives/compare?items=<itemId>,<itemId>[,<itemId>]`. Students and above. Post-MVP (ROADMAP).

```text
+--------------------------------------+
| < Înapoi                   [Meniu ≡] |
+--------------------------------------+
| Compară opționalele                  |
| Calculatoare · An III · Sem. 1       |
| Pachet opțional 1                    |
|                                      |
| [x] Rețele neuronale     (A) ━━━     |
|     Profesor [ I. Popescu        ▾ ] |
| [x] Grafică pe calculator (B) ┅┅┅    |
|     Profesor [ M. Stan           ▾ ] |
| [ ] Sisteme încorporate  (C) ···     |
|                                      |
|        [ radar suprapus A, B ]       |
|                                      |
|                      A      B        |
| Dificultate ex.    7.1     5.4       |
| Ajutor lab         6.0     8.3       |
| Ore / săptămână    4.0     6.5       |
| Format examen   teorie   proiect     |
| Strictețe prez.    5.0     3.2       |
|                                      |
| (C) Blocat: mai sunt necesare        |
|     4 evaluări                       |
+--------------------------------------+
```

**Data read**
- Package options: `core.curriculum_item` sharing (`plan_id`, `study_year`, `semester_no`, `elective_slot`) (Q1).
- Per option: professors of its lecture and other components (Q2, Q3); radar from `anon.prof_subject_radar` for the chosen professor (Q4); survival from `anon.course_survival`.

**Actions**
- Tick 2–3 options; a 4th tick is disabled ("Poți compara cel mult 3 opționale").
- Professor selector per option: default is the lecture professor of the most recent offering. Changing it swaps that option's radar.
- Both are client-side; the URL is updated so the comparison can be shared.

**States**
- Fewer than 2 options in the package: "Acest pachet are o singură opțiune."
- An option locked: drawn without a radar line, labelled "Blocat: mai sunt necesare {k − n} evaluări". Its survival column shows "—".
- Items from different packages in the URL: rejected with "Opțiunile nu fac parte din același pachet."

## 8. Rate

**Routes** `/rate` (list of eligible offerings) and `/rate/:offeringId` (the form). Students and above.

Before the list loads, the client needs a master secret in IndexedDB. Without one it sends the user to `/me/recovery`: to restore it if `ident.token_vault` exists for the account, or to create one if not.

### 8.1 Eligible offerings

```text
+--------------------------------------+
| Evaluează                  [Meniu ≡] |
+--------------------------------------+
| Cursurile pe care le poți evalua     |
|                                      |
| 2025–2026 · Semestrul 2              |
| +----------------------------------+ |
| | Baze de date                     | |
| | Deschis din 30 iun. 2026         | |
| | [ Evaluează ]                    | |
| +----------------------------------+ |
| | Rețele de calculatoare           | |
| | Trimis · programat               | |
| +----------------------------------+ |
| 2026–2027 · Semestrul 1              |
| +----------------------------------+ |
| | Sisteme de operare               | |
| | Se deschide pe 15 feb. 2027      | |
| +----------------------------------+ |
|                                      |
| Lipsesc cursuri din anii trecuți?    |
| Cere acces la grupa de atunci.       |
+--------------------------------------+
```

**Data read**
- Q5 on the ident pool: the student's `core.group_membership` rows (temporal key GiST) and `core.offering_enrolment` → `core.course_offering` → `core.offering_v` with `semester.rating_opens_at`, anti-joined with `NOT EXISTS` against `ident.token_issuance`. RLS limits both to the student's own rows.
- Already issued offerings: `ident.token_issuance` (own rows).
- Local state per offering from IndexedDB: token, blinding factors, outbox entry, submitted or not.

**Actions**

| Control | Calls | Note |
|---|---|---|
| (on load) issue tokens | `POST /api/tokens/issue` with one blinded message per eligible, not yet issued offering whose window is open | Blinding factors are saved to IndexedDB before the request. The server rejects a partial set; a retry returns the stored signatures (ADR-021). The client verifies each signature against `keys.json`, then uploads the encrypted vault: `PUT /api/vault`. |
| Evaluează | `/rate/:offeringId` | |
| "Cere acces la grupa de atunci" | `/group` (historical claim) | |

**States**
- No membership: "Nu faci parte din nicio grupă. Alătură-te grupei tale ca să poți evalua." → `/group`.
- Membership request pending: "Cererea ta de înscriere așteaptă aprobarea șefului de grupă."
- Window not open (`rating_opens_at` in the future): "Se deschide pe {date}". No token is issued for it yet.
- Issuance failed (network): "Nu am putut obține tokenurile. Reîncearcă." The retry reuses the stored blinding factors.
- Signature fails verification or key not in `keys.json`: "Cheie de semnare necunoscută. Nu trimite evaluarea și anunță administratorul." Nothing is stored.
- All done: "Ai evaluat toate cursurile disponibile."

### 8.2 Rating form

```text
+--------------------------------------+
| < Evaluează                          |
+--------------------------------------+
| Baze de date · 2025–2026 Sem. 2      |
| Evaluarea este anonimă.              |
|                                      |
| 1. Cum se trece                      |
| Dificultate examen   [====|-----] 6  |
| Cât ajută lab.       [=======|--] 8  |
| Prezența la curs                     |
|  ( ) da  (•) parțial  ( ) nu         |
| Ore pe săptămână     [ 5   ]         |
| Format examen        [ practic   ▾ ] |
| Strictețe prezență   [========|-] 9  |
|                                      |
| 2. Profesori                         |
| ▾ Curs: Conf. dr. I. Popescu         |
|   Predare și organizare              |
|   1 Platforme digitale  [--|---] N/A |
|   2 Tehnologii actuale  [=====|] 9   |
|   ...                                |
|   14 Atitudine respectuoasă [===|] 8 |
|   Comentariu (opțional, 280)         |
|   [                              ]   |
| ▸ Lab: As. dr. A. Ionescu (gr. 2)    |
| ▸ Lab: As. M. Stan (gr. 1) · nu eval.|
|                                      |
| 3. Sfat pentru colegi (opțional)     |
| [                                  ] |
| 0 / 1000                             |
|                                      |
| [          Trimite               ]   |
+--------------------------------------+
```

**Data read**
- Assignments of the offering (Q3), via `/anon/offerings/:id/assignments` so the fetch carries no session. Subgroup labels let the student expand the teachers they actually had; the rest stay collapsed and unrated.
- Labels: `ref.axis`, `ref.pillar`, `ref.exam_option`, `ref.attendance_usefulness`.

**Validation** (client, then the database CHECKs on redeem)
- Axes: 0–10 or empty (`score` domain). An untouched axis is sent as `NULL` ("N/A").
- A teacher is included only if at least one axis is set (`CHECK (num_nonnulls(a01..a14) >= 1)`).
- Survival fields: scales 0–10, weekly hours a non-negative number, exam options (any number, grouped by how it is given and what it covers) and attendance from their lookup tables. All optional.
- Comment ≤ 280 characters, tip ≤ 1,000 (CHECK on `post_version`). At most one tip and one comment per teacher.
- Under the comment box: "Scrie despre cum se predă, nu despre persoană. Fără nume, date personale sau acuzații."

**Trimite**
- The payload is put in a local outbox. The client sends it as `POST /anon/submissions` (`offering_id, prepared_msg, sig, payload`) at a random time no earlier than the day after issuance (ARCHITECTURE §3), while the app is open.
- The server verifies the signature and inserts `anon.submission` (PK `key_hash`), its `anon.rating` rows and any `anon.post` + `anon.post_version` (`status = 'pending'`, `submitted_week`) (Q6, Q7).
- Confirmation: "Evaluarea a fost salvată și va fi trimisă anonim în următoarele zile. Rezultatele apar după actualizarea de noapte, când există cel puțin 5 evaluări."

**States**
- Duplicate token, HTTP 409 (ADR-011): "Acest curs a fost deja evaluat de pe acest cont." Offers "Editează" → `/me/submissions`.
- Signature rejected: "Tokenul nu este valid." The outbox entry is kept for inspection.
- Moderation paused (ADR-006 fallback): the comment and tip fields are hidden with "Textele sunt dezactivate temporar. Evaluarea numerică funcționează."

## 9. My submissions

**Route** `/me/submissions`. Students and above. Everything is driven by tokens in IndexedDB; the server never lists a user's submissions.

```text
+--------------------------------------+
| Contribuțiile mele         [Meniu ≡] |
+--------------------------------------+
| Vizibile doar pe acest dispozitiv    |
| (sau după restaurarea codului).      |
|                                      |
| +----------------------------------+ |
| | Baze de date · 2025–2026 S2      | |
| | Evaluare: trimisă                | |
| | Sfat: publicat                   | |
| | Comentariu Popescu: în moderare  | |
| | [ Editează ]  [ Șterge ]         | |
| +----------------------------------+ |
| | Rețele de calculatoare           | |
| | Evaluare: programată (trimitere  | |
| | după 29 oct.)                    | |
| | [ Editează ]  [ Anulează ]       | |
| +----------------------------------+ |
| | Analiză matematică · 2024–2025   | |
| | Comentariu Stan: respins         | |
| | Motiv: date personale            | |
| | [ Editează și retrimite ]        | |
| +----------------------------------+ |
+--------------------------------------+
```

**Data read**
- Local: tokens and outbox from IndexedDB.
- Server: `GET /anon/submissions/:offeringId` with `prepared_msg` as proof (Q6). Returns the `anon.submission`, its `anon.rating` rows, and each post's `anon.post_version` rows with `status`, `reason_code` (label from `ref.moderation_reason`) and `moderated_week`.

**Post status shown to the author**

| Condition | Label |
|---|---|
| latest version `pending` | "În moderare" |
| `approved`, `moderated_week` is the current week | "Aprobat · apare de luni" |
| `approved` and released | "Publicat" |
| comment approved, its pair below k | "Aprobat · ascuns până la 5 evaluări" |
| `rejected` | "Respins · Motiv: {reason}" |
| an approved version plus a newer pending one | "Publicat · modificare în moderare" |

**Actions**

| Control | Calls | Note |
|---|---|---|
| Editează | opens the 8.2 form prefilled; `PUT /anon/submissions/:offeringId` with `prepared_msg` | Ratings and survival are updated in place and show after the nightly refresh. A changed tip or comment adds a new `post_version` (`pending`); the approved version stays published until the new one is approved (ADR-018). |
| Șterge | confirm dialog, then `DELETE /anon/submissions/:offeringId` with `prepared_msg` | Cascades to ratings, posts, versions and reports (DATABASE §5). Text: "Ștergerea elimină evaluarea și textele tale. Nu poate fi anulată." |
| Anulează | removes the outbox entry locally | Only for not yet sent submissions. |

**States**
- No local tokens and a vault exists: "Restaurează codul de recuperare ca să îți vezi contribuțiile." → `/me/recovery`.
- No submissions: "Nu ai trimis încă nicio evaluare." → `/rate`.
- Submission not found on the server for a token marked sent: "Nu am găsit evaluarea. Poate a fost ștearsă de pe alt dispozitiv."

## 10. Recovery code

**Route** `/me/recovery`. Students and above. The 128-bit `master` secret never leaves the browser unencrypted.

```text
+--------------------------------------+
| Cod de recuperare          [Meniu ≡] |
+--------------------------------------+
| Codul tău de recuperare              |
|                                      |
|  +--------------------------------+  |
|  | K7QF-2MXA-91PD-ZT4R-HC8W-Q3NB  |  |
|  +--------------------------------+  |
|                                      |
| Notează-l. Cu el îți poți edita      |
| evaluările de pe alt dispozitiv.     |
| Nu îl putem recupera: dacă îl        |
| pierzi, evaluările rămân, dar nu le  |
| mai poți modifica.                   |
|                                      |
| [ Descarcă (.txt) ]  [ Copiază ]     |
| [x] L-am salvat într-un loc sigur    |
| [           Continuă             ]   |
|                                      |
| ---------- Alt dispozitiv ---------- |
| Ai deja un cod? Introdu-l:           |
| [ ____-____-____-____-____-____  ]   |
| [          Restaurează           ]   |
+--------------------------------------+
```

**Data**
- Local: `master` in IndexedDB.
- Server: `ident.token_vault(user_id, ciphertext, nonce, updated_at)`, read and written only by its owner.

**Modes**

| Mode | When | Behaviour |
|---|---|---|
| Create | no local master, no vault | The client generates `master`, shows it with Download and Copy. "Continuă" is enabled only after the checkbox. Then the user returns to `/rate`. |
| Show | local master exists | Shows the code from IndexedDB with Download and Copy (see Open questions). |
| Restore | no local master, vault exists | `GET /api/vault` → decrypt with AES-GCM under `HKDF(master)` in the browser → store master and tokens in IndexedDB. |

**Validation and states**
- Input is normalised (case, spaces, dashes) and must have the code's length and alphabet: "Codul are un format greșit."
- Decryption fails: "Cod incorect." There is no server-side attempt counter; the ciphertext is already the account's own data.
- Code lost: "Am pierdut codul" explains that there is no re-issue (ADR-002). Submissions stay published; tokens issued but not yet used are lost with the code. Starting over with a new code is in Open questions.

## 11. Group

**Routes** `/group` and `/join/:inviteToken` (invite link). Students and above. A group leader sees extra tabs.

### 11.1 Student view

```text
+--------------------------------------+
| Grupa mea                  [Meniu ≡] |
+--------------------------------------+
| 3211A · Calculatoare · An III        |
| Subgrupa 2 · membru din 1 oct. 2026  |
|                                      |
| Cine predă la grupa ta               |
| Rețele neuronale                     |
|   Curs  Conf. dr. I. Popescu         |
|   Lab   As. dr. A. Ionescu (sg. 2)   |
| Baze de date                         |
|   Curs  Prof. dr. M. Stan            |
|   Lab   (necompletat)                |
|                                      |
| Cererile mele                        |
| 3111A (2025–2026) · istoric          |
|   În așteptare                       |
| Baze de date cu 3212B · restanță     |
|   Aprobată                           |
|                                      |
| [ Cere acces la o grupă anterioară ] |
| [ Refac un curs cu altă grupă      ] |
| [ Sunt șeful acestei grupe         ] |
+--------------------------------------+
```

Join via invite link (`/join/:inviteToken`):

```text
+--------------------------------------+
| Alătură-te grupei                    |
+--------------------------------------+
| 3211A · Calculatoare · An III        |
| 2026–2027                            |
|                                      |
| Subgrupa                             |
| ( ) 1   (•) 2                        |
|                                      |
| [      Intră în grupă            ]   |
| Intri imediat, fără aprobare.        |
|                                      |
+--------------------------------------+
```

**Data read**
- Own memberships: `core.group_membership` → `core.study_group`, `core.subgroup` (RLS: own rows).
- Own requests: `core.membership_request` by `user_id` (Q11), kind label from `ref.membership_kind`.
- Who teaches: `core.teaching_assignment` for the group's offerings (Q3) with professor and subgroup.
- Invite: `core.group_invite` by `token_hash`.

**Actions**

| Control | Calls | Validation and states |
|---|---|---|
| Intră în grupă (invite) | `POST /api/groups/join` with the invite token and subgroup → `core.join_by_invite` | Joins at once: the invite is the leader's approval, so the function writes an approved request (`decided_by` = invite creator, or approves the student's pending one) and the membership. Invite unknown or `expires_at` passed: "Linkul de invitație a expirat." Uses ≥ `max_uses` (counted under `FOR NO KEY UPDATE`): "Linkul a atins numărul maxim de utilizări." Group full: "Grupa a atins numărul maxim de membri." Already in a group this period: "Studentul este deja membru al altei grupe în această perioadă." |
| Cere să intri în {grupă} (picker) | `POST /api/groups/:id/requests` with the subgroup | Only for a current group that has a leader ("Grupa nu are încă un șef care să aprobe cererea."), only for a student in no group ("Ești deja într-o grupă anul acesta."), one pending regular request per student across all groups ("Ai deja o cerere în așteptare."). The leader approves it in the requests list. |
| Cere acces la o grupă anterioară | form: academic year, group code, subgroup → `POST /api/groups/requests` with `kind` = historical claim | Approved by a leader (ARCHITECTURE §4). Approval yields tokens for that group's past offerings (ADR-005). |
| Refac un curs cu altă grupă | form: group, offering → `POST /api/groups/requests` with `offering_id` | On approval becomes an `offering_enrolment` for that offering only. |
| Sunt șeful acestei grupe | `POST /api/groups/:id/leader-claims` | Creates `core.group_leader_claim` (`pending`) for an admin. "Cererea a fost trimisă administratorului." |

**States**
- No group: the page offers a group picker to request to join a led group or claim leadership of an unled one, plus the historical and repeat buttons.
- Rejected request: "Respinsă" with no reason (none is stored).

### 11.2 Group leader view

Tabs: **Membri**, **Cereri (N)**, **Invitații**, **Profesori pe subgrupe**.

```text
+--------------------------------------+
| Grupa 3211A                [Meniu ≡] |
+--------------------------------------+
| [Membri] [Cereri 3] [Invit.] [Prof.] |
|                                      |
| Cereri în așteptare                  |
| +----------------------------------+ |
| | ana.pop@student.ugal.ro          | |
| | Membru · subgrupa 2              | |
| | [ Aprobă ]  [ Respinge ]         | |
| +----------------------------------+ |
| | dan.radu@student.ugal.ro         | |
| | Istoric · 3111A (2025–2026)      | |
| | [ Aprobă ]  [ Respinge ]         | |
| +----------------------------------+ |
| Locuri ocupate: 27 / 30              |
+--------------------------------------+
```

```text
+--------------------------------------+
| [Membri] [Cereri] [Invit.] [Prof.]   |
|                                      |
| Link nou                             |
| Expiră după  [ 7 zile          ▾ ]   |
| Utilizări    [ 30              ]     |
| [       Generează link           ]   |
|                                      |
| Active                               |
| …/join/••••  12/30 · până 4 nov.     |
|   [ Copiază ]  [ Dezactivează ]      |
+--------------------------------------+
```

```text
+--------------------------------------+
| [Membri] [Cereri] [Invit.] [Prof.]   |
|                                      |
| Baze de date · Lab                   |
| Subgrupa 1  [ As. M. Stan        ▾ ] |
| Subgrupa 2  [ As. dr. A. Ionescu ▾ ] |
| Rețele neuronale · Seminar           |
| Subgrupa 1  [ alege profesorul   ▾ ] |
| Subgrupa 2  [ alege profesorul   ▾ ] |
|                                      |
| [          Salvează              ]   |
+--------------------------------------+
```

**Data read**
- Roster: `core.group_membership` for the group, current rows, with `core.user_account.email` and subgroup (RLS: leader of this group while the leadership's membership is current).
- Pending requests: `core.membership_request` `WHERE status = 'pending'` (Q10).
- Invites: `core.group_invite` for the group; uses counted from `membership_request.invite_id`.
- Mapping: one slot per offering × non-lecture component with hours in `curriculum_item` × subgroup (`GET /api/groups/:id/teaching`), with the professor of the current `core.teaching_assignment`; professors from `core.professor` (not archived). Saving (`PUT`) closes the current assignment at today and starts a new one, or deletes it if it had not started yet.

**Actions**

| Control | Calls | Validation and states |
|---|---|---|
| Aprobă | `POST /api/groups/requests/:id/approve` | One data-modifying CTE: update the request `WHERE status = 'pending'`, insert `group_membership` or `offering_enrolment` (DATABASE §10). Group row locked `FOR NO KEY UPDATE` to check `max_size`. Zero rows: "Cererea a fost deja decisă." Group full: "Grupa a atins numărul maxim de membri." Student already in another group for that period (temporal UQ): "Studentul este deja membru al altei grupe în această perioadă." Audited. |
| Respinge | `POST /api/groups/requests/:id/reject` | Same `pending` guard. |
| Generează link | `POST /api/groups/:id/invites` | `expires_at` in the future; `max_uses` ≥ 1. The raw token is shown once; only `token_hash` is stored. |
| Dezactivează | `PATCH /api/groups/invites/:id` setting `expires_at = now()` | No delete: requests reference the invite. |
| Salvează (mapping) | `PUT /api/groups/:id/assignments` | Only `lab`, `seminar`, `project` components (`CHECK` forbids a subgroup on `lecture`). A changed teacher mid-semester closes the old row's `valid_during` and inserts a new row; a row already rated is never edited in place. |

**States**
- No pending requests: "Nu ai cereri noi."
- Leadership ended (membership no longer current): the leader tabs disappear; RLS returns no rows.

## 12. Moderation queue

**Route** `/moderation`. Moderators only. The role is checked on the ident side; the queue itself runs on the anon pool (DATABASE §2).

Tabs: **Texte** (pending versions) and **Raportări** (open reports).

```text
+--------------------------------------+
| Moderare                   [Meniu ≡] |
+--------------------------------------+
| [ Texte 42 ]  [ Raportări 3 ]        |
|                                      |
| Comentariu · 280                     |
| Baze de date                         |
| Trimis: săpt. 19 oct. 2026           |
| +----------------------------------+ |
| | "Laboratoarele se predau pe      | |
| | platformă, dar [0722 xxx xxx]    | |
| | răspunde doar la telefon."       | |
| +----------------------------------+ |
| ⚑ Posibile date personale (telefon)  |
|                                      |
| [       Aprobă                   ]   |
| Respinge pentru:                     |
| [Insultă] [Acuzație]                 |
| [Date personale] [În afara temei]    |
|                                      |
| Versiunea anterioară publicată ▾     |
|                     [ Următorul > ]  |
+--------------------------------------+
```

```text
+--------------------------------------+
| [ Texte ]  [ Raportări 3 ]           |
|                                      |
| Sfat · Analiză matematică            |
| Motiv raportare: date personale      |
| Raportat de 2 ori · săpt. 26 oct.    |
| +----------------------------------+ |
| | "Profesorul ... "                | |
| +----------------------------------+ |
| [ Respinge raportarea ]              |
| [ Confirmă raportarea ]              |
+--------------------------------------+
```

**Data read**
- `anon.moderation_queue` (no `key_hash`), oldest first, keyset on (`submitted_week`, `post_id`, `version_no`) (Q8). It shows the text, the kind and the course only: no professor, no author.
- Flags from the moderation filter (ARCHITECTURE §6): highlighted spans and a flag line. The filter never decides.
- Reasons: `ref.moderation_reason` (insult, accusation, personal data, off-topic).
- Reports: `anon.post_report` `WHERE status = 'open'` (Q12), grouped by post, with `ref.report_reason` labels and the published text.

**Actions**

| Control | Calls | Note |
|---|---|---|
| Aprobă | `POST /anon/moderation/:postId/:versionNo/approve` | `status = 'approved'`, `moderated_week` = this week. Published from next Monday by `anon.published_post` (weekly release). |
| Respinge (reason) | `.../reject` with `reason_code` | `CHECK ((status = 'rejected') = (reason_code IS NOT NULL))`: a reason is required. |
| Următorul | next keyset page | |
| Respinge raportarea | `PATCH /anon/reports/:id` `status = 'dismissed'` | |
| Confirmă raportarea | `PATCH /anon/reports/:id` `status = 'upheld'` | Effect on the post: see Open questions. |

**States**
- Queue empty: "Nu există texte de moderat."
- Version already decided by another moderator (zero rows updated): "Textul a fost deja moderat." and the item is skipped.

## 13. Admin

**Route** `/admin/:section`. Admins only. All writes use the ident pool and are audited by the statement-level trigger (ADR-023).

Sections: **Curriculum**, **Calendar**, **Grupe**, **Profesori**, **Repartizări**, **Șefi de grupă**, **Istoric**, **Rapoarte** (§14).

```text
+--------------------------------------+
| Administrare               [Meniu ≡] |
+--------------------------------------+
| Secțiune [ Șefi de grupă         ▾ ] |
|                                      |
| Cereri în așteptare                  |
| +----------------------------------+ |
| | ion.vasile@student.ugal.ro       | |
| | 3211A · 2026–2027                | |
| | Membru din 1 oct. 2026           | |
| | [ Aprobă ]  [ Respinge ]         | |
| +----------------------------------+ |
|                                      |
| Șefi activi                          |
| 3211A  maria.ene@...   [ Revocă ]    |
| 3212B  andrei.lup@...  [ Revocă ]    |
+--------------------------------------+
```

```text
+--------------------------------------+
| Secțiune [ Profesori             ▾ ] |
|                                      |
| [ + Adaugă profesor ]                |
| Departament [ Toate            ▾ ]   |
|                                      |
| Conf. dr. Ion Popescu                |
|   Calculatoare · 12 repartizări      |
|   [ Editează ]  [ Arhivează ]        |
| As. Mihai Stan                       |
|   Calculatoare · 0 repartizări       |
|   [ Editează ]  [ Șterge ]           |
+--------------------------------------+
```

| Section | Tables | Rules enforced (database first) |
|---|---|---|
| Curriculum | `faculty`, `department`, `specialization`, `subject` (with `replaced_by_id`), `study_plan`, `curriculum_item` | Plan `intake` ranges do not overlap; `study_year ≤ duration_years` (trigger); `replaced_by_id <> id`. |
| Calendar | `academic_year`, `semester` incl. `rating_opens_at` | Years and semesters do not overlap (EXCLUDE, temporal UQ); semester inside its year is checked by this form (DATABASE §3.3). |
| Grupe | `study_group`, `subgroup`, `course_offering` (with `title_snapshot`) | Group and offering share plan and year (pinned FK). "Trecere în anul următor" runs `rollover(ay)`. |
| Profesori | `professor` | "Șterge" only when no assignment exists (`EXISTS`, Q16); otherwise "Arhivează" sets `archived_at` and anonymises the name (ADR-012). |
| Repartizări | `teaching_assignment` | Component from `ref.component`; no subgroup on lectures; mid-semester change closes `valid_during` and adds a row. |
| Șefi de grupă | `group_leader_claim`, `group_leader` | Approve inserts `group_leader` keyed by the claimant's current membership. Revoke ends leadership; the admin can then undo that leader's approvals (ADR-009), listed from the audit log. |
| Istoric | `audit_log` | Per row (Q13) or by time range with keyset pages (Q14). Read only. |

**States**
- FK violation on delete (for example a subject still in a plan): "Nu se poate șterge: este folosit în {table}."
- Constraint violations are shown next to the field, mapped from the constraint name.
- `ref.*` and `ref.setting` (including k) are not editable here; only the migrator writes `ref` (ADR-015).

## 14. Reports

**Route** `/admin/reports`. Admins only. Queries run as `aciee_report`, which can read only the materialised views (DATABASE §2).

```text
+--------------------------------------+
| Rapoarte                   [Meniu ≡] |
+--------------------------------------+
| Tip raport                           |
| (•) Profesor × disciplină (radar)    |
| ( ) Supraviețuire pe disciplină      |
|                                      |
| Specializare [ Toate            ▾ ]  |
| Departament  [ Toate            ▾ ]  |
| [x] Include perechile blocate (n<5)  |
|                                      |
| Previzualizare (12 rânduri)          |
| Popescu I. | Baze de date | n=23     |
| Stan M.    | Rețele       | blocat   |
| ...                                  |
|                                      |
| [CSV] [Excel] [PDF]                  |
|                                      |
| Date din actualizarea de noapte,     |
| 28 oct. 2026.                        |
+--------------------------------------+
```

**Data read**
- `report.prof_subject` and `report.course_survival`: views over `anon.prof_subject_radar` (Q4) and `anon.course_survival` that add professor, department and subject names and each subject's specialisations. They are the only objects `aciee_report` can read.
- The filter lists (specialisations, departments) come from the ident pool, as for the other admin screens.

**Actions**

| Control | Calls | Note |
|---|---|---|
| Filters, preview | `GET /api/reports/filters`, `GET /api/reports/:type?filters` | `:type` is `radar` or `survival`; filters are `specialization`, `department` (radar only) and `locked`. |
| Descarcă CSV | `GET /api/reports/:type/csv?filters` | UTF-8 with BOM so Excel shows Romanian diacritics. |
| Descarcă Excel | `GET /api/reports/:type/xlsx?filters` | Real `.xlsx`, numbers as numbers, header row frozen. |
| Descarcă PDF | `GET /api/reports/:type/pdf?filters` | Same rows; the radar report has one table per pillar. Liberation Sans is embedded for ș, ț, ă. |

**States**
- Locked pairs: averages are `NULL` in the view, exported as empty cells and shown as "blocat"; `n` is exported (ADR-022).
- No rows for the filters: "Nu există date pentru filtrele alese."
- Report query exceeds the 60 s `statement_timeout`: "Raportul a durat prea mult. Restrânge filtrele."

## Open questions

1. *Decided in W1 (code only at sign-up and on unverified sign-in), change if wrong.* **Code on every sign-in?** ARCHITECTURE §2 says "password + verification code" but not whether the code is a one-time email check at sign-up or a second factor on every sign-in. §3 above assumes sign-up (and unverified sign-in) only.
2. *Length decided in W1: 10–128 characters.* **Password policy and reset.** No minimum length or "forgot password" flow is specified. A reset would reuse `email_verification`, but no doc says so.
3. *Decided in W1: sign-up reveals a verified address (409); resend does not.* **Account enumeration.** Should Sign up answer the same way when the email is already registered?
4. *Decided in W1: once a minute, and it resets the counter.* **Resend limits.** How often may "Retrimite" send a new code, and does it reset the 5-attempt counter (assumed yes above)?
5. **What visitors see on the course page.** SPECIFICATION §2 says visitors see the catalogue without ratings. This spec also hides survival data and tips from them; confirm.
6. **Showing the recovery code again.** ARCHITECTURE §3 says the code is shown once; SPECIFICATION §6 lists "Show". This spec allows re-showing from the device's own IndexedDB; confirm or remove.
7. **Starting over after losing the code.** Generating a new master would overwrite `token_vault` and orphan unused tokens (no re-issue, ADR-002). Allow it with a warning, or block it?
8. **Outbox delivery.** The random next-day redemption needs the app to be open. What happens if the student never comes back: send on next visit only, or warn after N days?
9. **Re-rating after delete.** Deleting a submission frees its `key_hash`, so the same token could redeem again. Intended?
10. **Report handling owner and effect.** Who handles `post_report` (assumed moderators), and what "upheld" does to the post: hide it, or reject the published version? `anon.published_post` does not filter on reports today.
11. **Reporting without a session.** `/anon` calls carry no session, so the Report button cannot be limited to signed-in students or rate-limited per user. Is per-IP limiting at the nginx in front of the app enough?
12. **Moderation queue order.** ARCHITECTURE §6 says the filter "sorts" the queue; Q8 is oldest first. Flagged first, or oldest first with flags shown?
13. **Historical claims: who approves.** A past group has no current leader. Is the claim routed to the leader of its rolled-over successor group, and how is that link found?
14. **Archived professors.** Should `/professors/:slug` for an archived professor return 404, or show the anonymised name with aggregates?
15. **Assigning the moderator role.** No view grants `user_role`. Seed by changeset, or add a section to Admin?
16. **Comparator radar.** A radar is per professor × subject; this spec makes the user pick a professor per elective. Is a subject-level radar wanted (it would need a new materialised view)?
17. *Decided: CSV, Excel (.xlsx) and PDF.* **Report formats.** ROADMAP Stage 5 says PDF/CSV/Excel; SPECIFICATION says PDF/CSV. Is CSV with BOM enough for "Excel"?
