# ACIEE Advisor — Design

One visual language for the whole SPA, grown out of the timetable page that the owner already liked. The subject is an engineering faculty (automation, computers, electrical and electronic engineering), so the interface borrows from the object every ACIEE student has held: a printed circuit board. Solder-mask green is the brand colour, copper is the accent, the board itself is the page background, and data is set in a monospaced face like silkscreen markings. The look is calm and legible on a phone held while walking across campus; the decoration is limited to one signature element.

## Direction

| Axis | Choice | Why |
|---|---|---|
| Metaphor | Circuit board: a blue solder mask, copper traces and pads, a dot grid of vias on a cool white bench | ACIEE's own material (every Arduino and dev board on a lab bench is blue + copper); not a stock "edu app" look |
| Primary target | Phone, 360 px wide, one hand | Students open it between classes |
| Tone | Technical but friendly; plain verbs, sentence case | A tool, not a brochure |
| Boldness budget | Spent on one element: the copper pad and trace | Everything else stays quiet |

Rejected on purpose: cream + terracotta serif, black + acid green, newspaper hairlines (the three current AI defaults), and the "Claymorphism / Baloo 2" suggestion the style database returned for "educational app" (toy-like, wrong for an engineering faculty and for dark mode).

### Light palette: the three candidates

The first pass used a green-tinted board (`#e9f0ec`) with a dark solder-mask green primary (`#1d5b45`). The owner's verdict: muddy and dated in light mode. Three replacements were rendered side by side on the catalog and the timetable (shots `pal-graphite-*`, `pal-bluemask-*`, `pal-emerald-*`):

| Candidate | Board / primary | Verdict |
|---|---|---|
| Graphite | `#f3f5f6` / `#242e34`, copper accent | Clean and premium, but a dark-neutral primary plus one warm accent is the current "tasteful default"; nothing in it says ACIEE |
| **Blue mask** | `#f3f6fa` / `#1f4fb8`, copper accent | Chosen. A blue solder mask with copper is literally what sits on a lab bench; the cool white base keeps it crisp, the blue primary reads as a confident product colour, and copper stays the signature |
| Emerald | `#f4f6f5` / `#0c7a59`, copper accent | Cleaner than the first pass, but still "the green" the owner reacted to, and the weakest contrast of the three (5.3:1 for white on the primary) |

Dark mode was not criticised, but the hue had to follow the light primary or the brand word would change colour between themes. It was retuned from green-black to the same board unlit: navy `#0b1420` with a light blue primary `#6ea0ff`; copper, kinds and structure are unchanged.

## Tokens

Tokens live in `frontend/src/app.css`: primitives on `:root` and `.dark`, mapped to Tailwind utilities through `@theme inline`. Use the utilities (`bg-panel`, `text-copper`, `border-line`, `font-mono`) or the component classes; never a raw hex in a component.

### Colour

| Token | Light | Dark | Role |
|---|---|---|---|
| `--board` | `#f3f6fa` | `#0b1420` | Page background, with an 18 px dot grid of `--line` |
| `--panel` | `#ffffff` | `#122033` | Cards, inputs, sheets, the bottom bar |
| `--ink` | `#111c2b` | `#e6ecf4` | Body text |
| `--muted` | `#5a6678` | `#92a1b5` | Secondary text (5.4:1 / 6.2:1 on panel) |
| `--line` | `#d3dbe6` | `#243650` | Borders, dividers, the via grid |
| `--mask` | `#1f4fb8` | `#6ea0ff` | Primary: buttons, links, selected chips, the brand word |
| `--on-mask` | `#ffffff` | `#0b1420` | Text on `--mask` (7.3:1 / 7.2:1) |
| `--copper` | `#a0531f` | `#e0904f` | Accent: eyebrow trace, active-tab bar, focus ring, "now", odd week |
| `--danger` | `#b4232c` | `#ff8a8a` | Errors and destructive actions |
| `--kind-c/s/l/p/x` | copper / `#0f7a73` / `#6a4bc4` / `#8a6a0c` / muted | copper / `#4fc3b8` / `#b39cf0` / `#e2c04a` / muted | Lesson kinds: lecture, seminar (teal, so it never fights the blue primary), lab, project, other |

All text pairs were checked at ≥ 4.5:1 in both themes (lowest: light copper on the board at 5.2:1, light seminar teal on white at 5.2:1). Dark mode follows the system and can be forced from the account sheet; the choice is stored in `localStorage.theme` and applied before first paint by a two-line script in `index.html`.

### Type

Self-hosted through `@fontsource` (ADR-012: no third-party requests). All three faces carry Latin Extended, so ș ț ă î â render in the same font.

| Role | Face | Weights | Used for |
|---|---|---|---|
| Display | Chakra Petch | 600 | `h1`–`h3`, the brand word, the group code, day names, roman year chips |
| Body | IBM Plex Sans | 400 / 500 / 600 | Everything else |
| Data | Martian Mono | 400 / 500 | Eyebrows, times, codes, counts, tags, table headers (`tabular-nums`) |

Scale: 11 (`tag`, `eyebrow`), 12, 14 (body on cards), 16 (base), 18, 20 (`h2`), `clamp(1.75rem, 6vw, 2.5rem)` (`h1`). Line height 1.5 body, 1.1 display.

### Space, radius, elevation, motion

- Spacing is Tailwind's 4 px scale; pages use `px-4`, cards `p-4`, sections `mt-8`.
- Radius: `sm` 6 px (inputs in lists), `md` 10 px (buttons, inputs), `lg` 14 px (cards), `xl` 20 px (sheets), `full` (chips, tags).
- Elevation is almost entirely borders (`--line`). Only overlays cast a shadow: `shadow-sheet` for the bottom/side sheet, `shadow-modal` for centred dialogs and the combobox list.
- Motion: 150 ms colour transitions on buttons and chips; 250 ms ease-out slide for sheets; a 1.8 s pulse on the live timeline pad. `prefers-reduced-motion` collapses every animation and transition to 0.01 ms.
- Containers: `.page` (max-w-3xl) for reading pages, `.page-wide` (max-w-6xl) for the timetable grid and report tables.

### Icons

Lucide (`lucide-vue-next`), 20 px in navigation and 16 px inline. Icon-only buttons always carry `aria-label`. No emoji as icons.

## Signature element: the pad and trace

Every page title is introduced by an **eyebrow**: a copper via (ring) soldered to a short copper trace, then a small mono label in uppercase — `○— CURSURI`, `○— CALCULATOARE · AN 1 · SEM. 1`. The same copper trace runs vertically down the timetable's day view, with a pad at every lesson that fills and pulses while the lesson is running; the odd/even week picker is a square-wave clock signal drawn in copper; the brand mark is an IC package with that clock trace inside it. One idea, four places, nothing else decorative.

## Timetable controls

Lessons are the content, so they start at the top. A sticky bar under the header holds three pills: the **group** (`22C11A`, tap to change, the programme name appears on desktop), the **week** (`9–13 nov.`, tap to unfold the square-wave clock and pick another week) and the **parity** chip (`Impară` / `Pară`). One tap on the parity chip flips the stored calibration, so every week shifts; when the parity is still unknown the chip reads `?`, the clock panel opens by itself and asks the question once. The day strip sits in the same sticky block, so on a 360×740 phone the first lesson is visible at about 230 px. Desktop uses the same bar and leads with the five-day grid.

## Components

Defined once in `app.css` (`@layer components`) and reused with plain classes; Vue components exist only where behaviour is needed.

| Class / component | Notes |
|---|---|
| `.btn` + `.btn-primary` / `.btn-outline` / `.btn-ghost` / `.btn-danger`, `.btn-sm`, `.btn-icon` | 44 px minimum height; `btn-sm` is 36 px and only for secondary actions inside a card |
| `.input`, `select.input`, `textarea.input` | 44 px tall, copper border and ring on focus; native `<select>` on purpose (phones get the OS picker) |
| `.field` | Label + control stack |
| `.chip`, `.chip-on` | Pill toggles; `SegmentedChips.vue` wraps them as a radio group (optionally clearable, so a 0–10 score can go back to N/A) |
| `.card`, `.row-link` | Panel with `--line` border; `row-link` is a whole-card link with a chevron |
| `.eyebrow`, `.h1`, `.h2`, `.lede` | Page header pattern |
| `.tag`, `.kind-tag` | Mono uppercase labels; `kind-tag` takes its colour from `.k-C/S/L/P/X` |
| `.quote` | Copper left trace for tips and comments |
| `AppNotice.vue` | Inline status (`error` has `role="alert"`), left trace in the status colour |
| `AppSheet.vue` | reka-ui Dialog: bottom sheet on phones, right drawer on desktop, or a centred `modal` |
| `ComboSelect.vue` | reka-ui Combobox for long lists (groups, professors, admin references); `remote` delegates filtering to the server |
| `PasswordField.vue`, `BrandMark.vue`, `RadarChart.vue`, `LessonCard.vue`, `TimetablePicker.vue` | |

## App shell

- Header (sticky, blurred board): brand mark + "ACIEE **Advisor**", desktop nav links with a copper underline on the active one, the RO/EN pill, a sun/moon toggle, and on desktop the account button.
- Phones get a bottom tab bar instead of nav links: Courses, Timetable, then Rate and My group when signed in, and an Account tab. Active tab = mask colour + copper bar on top. Never more than five.
- The account sheet holds what does not deserve a tab: My contributions, Recovery code, Moderation, Admin, Sign out, plus Language and Theme. Visitors see Sign in / Create account there.
- Language: `navigator.language` → `ro` or `en` on first visit, remembered in `localStorage.locale`, `<html lang>` kept in sync. Both JSON files have identical keys (`en.json` mirrors `ro.json`).

## Do / don't

- Do start a page with `.eyebrow` + `.h1`; don't use a decorative numbered marker unless the content is a sequence (the rating form's 1–2–3 is one).
- Do set codes, times and counts in Martian Mono; don't set paragraphs in it.
- Do use `--mask` for the one primary action on a screen; don't put two primary buttons side by side.
- Do colour lesson kinds with `.k-*`; don't invent new hues per page.
- Do keep the dot grid on the board and plain `--panel` on cards; don't add gradients, glass or drop shadows to cards.
- Do keep 44 px targets and a visible copper focus ring; don't hide focus or rely on hover.
- Do write microcopy in the interface's voice ("Trimite cererea", "Rezultatele se actualizează în fiecare oră"); don't apologise or use filler.

## Stack decision

Tailwind CSS 4 (`@tailwindcss/vite`) for styling and **reka-ui** (the headless library shadcn-vue is built on) for the three primitives that need real accessibility work: Dialog, Combobox and Tabs. shadcn-vue's generated component files were not adopted: they would have added a CLI, a `cn()` utility and a dozen wrappers for a codebase that needs three primitives, and their default look would have pulled the site back toward the generic. PrimeVue and `@primeuix/themes` are removed, including the licence build argument.
