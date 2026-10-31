# ACIEE Advisor — Product Specification

## 1. Problem

Students entering the Faculty of Automatic Control, Computers, Electrical and Electronics Engineering (ACIEE), "Dunărea de Jos" University of Galați, have no structured way to learn from senior cohorts what a course actually demands: how exams are structured, how strict lab check-ins are, which materials help. The curriculum is mostly fixed (about one elective per semester), so the platform serves two needs:

- **Survival guides** for mandatory courses: how to pass, what to expect.
- **Informed elective choice**: side-by-side comparison of the elective options.

The faculty already runs an official, confidential end-of-semester teacher evaluation. ACIEE Advisor complements it: student-to-student guidance plus public, aggregated, anonymous signals.

## 2. Users and roles

| Role | Scope | Can |
|---|---|---|
| Visitor | — | See the public course catalogue (no ratings) |
| Student | global | Join a group, receive rating tokens, rate, write tips/comments, read everything published |
| Group leader | one group, one academic year | Approve/reject join requests, manage invite links, map which assistant teaches which subgroup |
| Moderator | global | Approve/reject tips and comments (sees text + course only) |
| Admin | global | Seed curriculum, professors, teaching assignments; approve group-leader claims; revoke leaders |

Students sign up with a `@student.ugal.ro` address (password + emailed code, or Microsoft sign-in).

## 3. Evaluation model

Each teaching assignment (a professor teaching one component — lecture, lab, seminar or project — of a course) is rated on 14 axes, scored 0–10. An axis the student did not experience is left empty (`NULL`).

**Pillar 1 — Teaching, organisation and technology**
1. Mastery of digital / remote learning platforms (*Stăpânirea platformelor digitale / online*)
2. Modernity and industry relevance of technologies taught (*Actualitatea tehnologiilor predate*)
3. Sufficiency and quality of asynchronous materials (*Calitatea suportului de curs și a materialelor asincrone*)
4. Class organisation and structure (*Organizarea și structura orelor*)

**Pillar 2 — Laboratories and practical work**

5. Alignment of laboratories with theory (*Corelarea laboratoarelor cu teoria*)
6. Clarity and convenience of lab check-ins (*Claritatea și confortul predării laboratoarelor*)
7. Relevance of practical/project work (*Relevanța lucrărilor practice / proiectelor*)
8. Convenience of practical/project defences (*Confortul predării lucrărilor practice / proiectelor*)

**Pillar 3 — Grading and communication**

9. Transparency of the grading system (*Transparența sistemului de notare*)
10. Fairness of the grading system (*Corectitudinea și relevanța sistemului de notare*)
11. Stability of grading rules during the semester (*Stabilitatea regulilor de notare pe parcursul semestrului*)
12. Accessibility and visibility of grades (*Accesibilitatea și vizibilitatea punctajelor pe parcurs*)
13. Openness to communication and availability (*Deschiderea la comunicare și disponibilitatea*)
14. Respectful treatment of students (*Atitudinea respectuoasă față de studenți*)

### Publication rules
- Results are aggregated per **professor × subject**, pooled across groups and semesters.
- Nothing is shown until **5 distinct ratings** exist (k-anonymity). Below that the UI shows "locked — N more ratings needed".
- Aggregates are recomputed **nightly** and rounded to 0.1. There is no live update, so a single new rating can't be recovered by comparing averages.
- **Rating window**: a semester's courses can be rated once that semester has ended. Past semesters are always open (upperclassmen rate courses they already took).

## 4. Written content

| Type | About | Limit | Shown |
|---|---|---|---|
| Course tip | The course: exam format, what to study, how labs are checked | 1,000 chars | After moderation |
| Teaching comment | One teaching assignment: how the teaching works | 280 chars | After moderation, and only once that professor × subject has passed k |

- Comments must describe teaching, never character. Rejection reasons: insult, accusation, personal data, off-topic.
- Everything is pre-moderated and released in weekly batches.
- Each published item has a **Report** button (notice-and-action).
- Authors see their item's status anonymously and may edit and resubmit. An edit creates a new pending version; the approved version stays visible until then.

## 5. Survival metadata (structured, per course)

Collected with each rating submission and shown on the course page:
- Exam difficulty (0–10).
- How much the labs help with the exam (0–10).
- Whether lecture attendance is useful (yes / partly / no).
- Weekly workload in hours.
- Exam format: theory / practical / oral / mixed.
- Attendance strictness (0–10).

## 6. Views

Full spec for each view (route, roles, wireframe, data, actions): [VIEWS.md](VIEWS.md).

| View | Role | Purpose |
|---|---|---|
| Landing / catalogue | all | Specialisations → years → courses |
| Course page | all (ratings: students) | Weights, survival metadata, tips, teaching assignments with radars |
| Professor page | students | Radar per subject, comments |
| Elective comparator | students | Overlay 2–3 elective radars |
| Rate | student | Pick an eligible course → 14 axes per teacher + survival fields + optional tip/comment |
| My submissions | student | Edit or delete own anonymous submissions (via the local token) |
| Recovery code | student | Show, download or restore the token-vault recovery code |
| Group | student, leader | Roster, join requests, invite link, subgroup → assistant mapping |
| Moderation queue | moderator | Pending text, reason codes |
| Admin | admin | Curriculum and assignment CRUD, leader claims, reports export |
| Reports | admin | PDF/CSV aggregate exports |

## 7. Out of scope (MVP)

- Automated sync with university systems (the schema reserves `group_membership.source = 'university_sync'`).
- Professor accounts and right of reply.
- Appeals beyond edit-and-resubmit.
- Automated (AI) moderation.
