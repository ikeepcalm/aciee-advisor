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

Each teaching assignment (a professor teaching one component — lecture, lab, seminar or project — of a course) is rated on the criteria of its component plus four shared grading criteria, each scored 0–10. A criterion the student did not experience is left empty (`NULL`). The four components work differently (a lecture is the professor presenting, a lab is students working while the professor checks and takes attendance, a seminar is solving problems together at the board, a project is work at home that the professor checks), so each asks its own questions. Quick mode asks the component's first criterion plus fair grading and respect.

**Shared, every component — Notare și comunicare**
1. Fair and clear grading (*Notare corectă și clară*)
2. Rules that stay the same all semester (*Reguli stabile tot semestrul*)
3. Grades visible during the semester (*Note vizibile pe parcurs*)
4. Respectful and easy to reach (*Respectuos și ușor de contactat*)

**Curs (lecture)**

5. Clear explanations · 6. Structure and pace · 7. Quality of course materials · 8. Up-to-date content · 9. Answers questions in class

**Laborator (lab)**

10. Clear lab instructions · 11. Help during the lab · 12. Linked to what the course teaches · 13. Fair checking of lab work · 14. Enough time for the work set · 15. Clear attendance and make-up rules

**Seminar**

16. Problems useful for the exam · 17. Solutions explained well · 18. Encourages taking part; mistakes are fine · 19. Fair grading of seminar activity

**Proiect (project)**

20. Clear requirements and deadlines · 21. Useful, relevant topic · 22. Guidance and feedback along the way · 23. Fair final presentation and evaluation · 24. Realistic amount of work

Romanian labels live in `ref.axis`. The server drops any score sent for a criterion outside the assignment's component.

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
- How the exam is given, tick any: written / multiple-choice test / oral / practical (at the computer) / project defence. What it covers, tick any: theory / problems. The course page shows the share of students who ticked each option.
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
