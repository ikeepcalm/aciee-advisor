# Pitch show

A self-playing walkthrough of the real site for live presentations. The laptop on the projector plays scripted beats over the real pages; the presenter walks around and advances them from a phone. Built for the 2026-11-22 pitch; runs on https://demo.acieeadvisor.com.

## Running it

| Device | URL |
|---|---|
| Laptop (projector, full screen) | `https://demo.acieeadvisor.com/show?room=<room>` (add `&lang=ro` to start in Romanian) |
| Phone | `https://demo.acieeadvisor.com/show/remote?room=<room>` |

`<room>` is any 4–40 characters of `a-z`, `0-9` and `-`, the same on both devices. With an invalid name (too short, capitals, other characters) the laptop silently joins a random room the phone cannot reach, so check the name if the phone never shows "ready".

1. Open the laptop page. A curtain covers the screen while the show warms up (about 10 s: it plays every beat at full speed so every page, chunk and response is cached), then it holds a white ACIEE Advisor screen until the first Next.
2. Open the phone page. The dot turns blue and the status reads "ready" once the laptop is warm.
3. The phone shows a small live preview of the big screen, the current beat with its speaker note, and the next beat with its note. Next plays the next beat. Each beat runs by itself and holds on its last frame. Back plays the previous beat, Replay restarts the current one, EN/RO switches the language and replays the beat. The timer counts down 5:00 from the first Next; tap it to reset. The phone screen is kept awake.

Laptop keys, also what a presentation clicker sends: → / Space / PageDown / Enter next, ← / PageUp back, `r` replay, Escape twice leaves show mode.

The show signs in a fictional student (Ioana Marin) on its own, forces the light theme and hides the mouse pointer and scrollbars. Leaving show mode (Escape twice) or closing the tab ends it; the room is kept in `sessionStorage` for that tab only.

## How it works

```
phone /show/remote ──POST /api/show/{room}──▶ backend relay ──SSE /api/show/{room}/events──▶ laptop /show
        ▲                                                                                        │
        └──────────────────────────── SSE (laptop reports beat, playing, ready, lang) ◀──────────┘
```

- **Relay** (`backend/.../domain/show`): an in-memory server-sent-events room per name, active only with the Spring profile `show` (demo sets `SPRING_PROFILES_ACTIVE=show`; prod has no relay). Every message is fanned out to everyone in the room and the last one is replayed to new listeners; a heartbeat every 20 s keeps proxies from closing the stream. Limits: 20 rooms, 6 listeners per room, 2000-character messages. The host nginx proxies `/api/show/` without buffering (DEPLOY.md).
- **Show layer** (`frontend/src/show/ShowLayer.vue`): mounted by `App.vue` only while a room is active, loaded lazily so normal visitors never download it. It runs the beats, listens to the relay and the keyboard, and draws the overlay: the drawn cursor, the caption, the spotlight (`ShowSpot.vue`), the full-screen scenes and the warm-up curtain. A command that arrives within 1.5 s of (re)connecting is ignored, since that is the relay replaying an old press.
- **Fetch cache** (`fetchCache.ts`): in show mode every same-origin `GET /api/…` and `/anon/…` is answered once and then from memory, so beats never wait for the server. `cannedResponse(pattern, body)` answers a URL without the network.
- **Showcase data** (`showcase.ts`, `data/`): the show does not use the demo database. It serves a timetable snapshot (`data/timetable.json`, prod's timetable on 2026-11-17), the lookups (`data/lookups.json`), a fictional professor (Elena Dobrescu and eight others), four rateable courses and the rating form, a "timetable changed" note for group 22C31A, and the signed-in student. The layer signs that student in again before every beat; the Microsoft sign-in beat signs out for itself to reach `/signin`, and `/api/auth/providers` is canned so the Microsoft button shows even where the demo has no Microsoft profile. `RateFormView` gets a stand-in token in show mode (`showToken` in `state.ts`), so the wizard opens without blind-signature keys; the show never submits.
- **Remote** (`frontend/src/views/ShowRemoteView.vue`, route `/show/remote`, no app header or footer). Its preview is an iframe of `/show?room=<room>&mirror=1` drawn at 1440×810 and scaled down: a mirror runs the same beats on the phone and follows the beat, replay and language the laptop reports, instead of streaming pixels. A mirror never reports, ignores remote commands and keys, and does not touch `sessionStorage`, which the iframe shares with the remote page.

## Editing beats

Beats live in `frontend/src/show/beats.ts`, in order. Each one is:

```ts
{
  title: 'Professors',            // shown on the phone
  note: 'Every professor gets…',  // speaker note on the phone
  async run(d) { … },             // what the laptop does
}
```

A beat must bring the page into its own starting state (usually `await d.go(path)`), because the presenter can jump to it from any other beat. Before each beat the layer clears the caption and spotlight and closes any open sheet.

On-screen text goes through `say(ro, en)`, which picks the current language. Notes on the phone are English only.

The director `d` (`director.ts`):

| Call | Does |
|---|---|
| `await d.go('/path')` | navigates, waits for the page's requests, scrolls to the top |
| `await d.click(selectorOrElement)` | glides the cursor there (scrolling it into view), presses, clicks |
| `await d.point(selector)` | glides the cursor there without clicking |
| `await d.type(selector, text)` | clicks the field and types character by character |
| `await d.scrollTo(selectorOrY, ms?)` | eased scroll to an element (below the header) or a y offset |
| `d.key('PageDown')` | sends a synthetic key to the page (the guide pages with it; the layer's own key handler ignores synthetic keys) |
| `await d.find(selector, text?)` | waits up to 6 s for the first visible match, optionally containing `text` |
| `d.say(title, body?, 'top'?)` / `d.hush()` | shows / hides the caption sticky note |
| `await d.spot(selector)` / `await d.spot(null)` | dims everything except the element |
| `d.scene(Component)` / `d.scene(null)` | shows / removes a full-screen scene |
| `d.cursor(false)` | hides the drawn cursor |
| `await d.wait(ms)` | pauses; all timings shrink to zero during the warm-up |

Prefer selectors that do not depend on the language: `aria-labelledby` suffixes, `href`s, class names, or `t('i18n.key')` for labels (see the existing beats). Rating chips are picked by value with `score(field, value)`.

When you press Next mid-beat, the running beat is cancelled at its next `wait`, `find` or cursor move and the next one starts; nothing has to be undone by hand.

### Adding a beat

1. Add an entry to `beats` in the order it should play. Phone numbering, Back and the warm-up follow automatically.
2. If it needs data the show does not serve yet, add a `cannedResponse` in `registerShowcase()` (`showcase.ts`); otherwise the warm-up fetches it from the demo backend and caches it.
3. Run it locally (below), watch it once, and check the browser console: a failing step logs `warm-up N Error: show: nothing matches …` during the warm-up.

## Scenes

Full-screen slides in `frontend/src/show/scenes/` (hook, anonymity, link preview, architecture, close). Contract: no props, the animation starts on mount and holds its last frame, an opaque paper background, CSS and SVG only. Text is kept in the component as `{ en, ro }` and chosen with `useI18n().locale`. Each scene's root uses the `.show-paper` class from `ShowLayer.vue`, which scales the whole scene with the viewport (1em = 16px on a 1440×810 stage) and switches to the final frame under reduced motion. A new scene is imported in `beats.ts` with `defineAsyncComponent` and shown with `d.scene(...)`; replaying a beat remounts its scene, so the animation restarts.

## Running locally

```sh
docker --context orbstack compose -f infra/docker-compose.yml up -d postgres
cd backend && SPRING_PROFILES_ACTIVE=show COOKIE_SECURE=false ./gradlew bootRun
cd frontend && npm run dev
```

Open `http://localhost:5173/show?room=test-room` and `http://localhost:5173/show/remote?room=test-room`. Saving a file in `src/show/` reloads the laptop page, which warms up again and waits on the home page.

## Deploying

Merging to `main` and redeploying the demo app in Coolify is enough; the show needs nothing from the demo database. The demo app needs `SPRING_PROFILES_ACTIVE=show`, and its host nginx block needs the unbuffered `/api/show/` location (DEPLOY.md). The demo database is seeded by the `demo` Liquibase context (`fixtures/002-demo.sql`): 16 fictional professors, realistic ratings and comments, and the demo accounts; it is what visitors see when they browse the demo outside the show.
