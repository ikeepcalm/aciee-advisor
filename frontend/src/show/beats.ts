import { defineAsyncComponent } from 'vue'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { rateDraftPrefix, removeStoredStartingWith } from '@/storage'
import type { Director } from './director'
import { SHOW_GROUP, SHOW_OFFERING, SHOW_PROFESSOR } from './showcase'

export interface Beat {
  title: string
  note: string
  run(d: Director): Promise<void>
}

const HookScene = defineAsyncComponent(() => import('./scenes/HookScene.vue'))
const AnonymityScene = defineAsyncComponent(() => import('./scenes/AnonymityScene.vue'))
const PreviewScene = defineAsyncComponent(() => import('./scenes/PreviewScene.vue'))
const MobileScene = defineAsyncComponent(() => import('./scenes/MobileScene.vue'))
const ArchitectureScene = defineAsyncComponent(() => import('./scenes/ArchitectureScene.vue'))
const CloseScene = defineAsyncComponent(() => import('./scenes/CloseScene.vue'))

const t = (key: string) => i18n.global.t(key)

function say(ro: string, en: string) {
  return i18n.global.locale.value === 'ro' ? ro : en
}

const score = (field: string, value: number) =>
  `[aria-labelledby$="-${field}"] label:nth-child(${value + 1})`

const MICROSOFT = 'a[href="/api/auth/oauth2/microsoft"]'

const timetable = `/timetable?g=${SHOW_GROUP.group}`

function forgetSeenChanges() {
  try {
    localStorage.removeItem(`timetable.changesSeen.${SHOW_GROUP.program}.${SHOW_GROUP.group}`)
    localStorage.setItem('timetable.group', JSON.stringify(SHOW_GROUP.group))
  } catch {
    // storage blocked: the note shows anyway on a fresh tab
  }
}

export const opening =
  "I consider myself a practical person. If I build something, I'd rather keep it small and have it solve one real problem. I hope today's project counts as one.\n\nWhen I was admitted here, I came as a foreigner. No connections, nobody to explain anything. Where's my timetable? Which labs matter? Which building is this? What is this professor like?"

export const beats: Beat[] = [
  {
    title: 'The problem',
    note: "(Let the chat bubbles land.)\n\nYou could say: just ask in the group chat. In my experience, you'll rarely get a straight answer. So I built the thing I wish I'd had three years ago: ACIEE Advisor. It gives first-years the tools they need, and gives older students a way to help them.",
    async run(d) {
      d.hush()
      d.cursor(false)
      d.scene(HookScene)
      await d.wait(400)
    },
  },
  {
    title: 'Timetable',
    note: "Let's start with the timetable. Officially, it's a set of faculty PDFs, and at first they confused me a lot. ACIEE Advisor reads those PDFs every hour and turns them into your group's week, with odd and even weeks already worked out.\n\n(Moved class lights up.) If a class moves to another room, you know right away.",
    async run(d) {
      d.scene(null)
      forgetSeenChanges()
      await d.go(timetable)
      d.say(
        say('Orarul grupei tale, nu un PDF', 'Your group’s timetable, not a PDF'),
        say(
          'Citit din PDF-urile facultății la fiecare oră, cu săptămâna pară sau impară deja calculată.',
          'Read from the faculty PDFs every hour, odd and even weeks already worked out.',
        ),
      )
      await d.wait(2600)
      await d.click(`button[aria-label="${t('timetable.nextWeek')}"]`)
      await d.wait(1200)
      await d.click(`button[aria-label="${t('timetable.prevWeek')}"]`)
      await d.wait(600)
      await d.spot('.changed')
      d.say(say('S-a mutat o oră? Afli imediat.', 'A class moved? You know right away.'))
      await d.wait(1600)
      await d.click('.changed .open')
      await d.spot('[role="dialog"]')
      await d.wait(2800)
      await d.click('[role="dialog"] button.btn-icon')
      await d.spot(null)
      d.hush()
    },
  },
  {
    title: 'Catch-up finder',
    note: 'Missed a lab? Tap it, and you see the same lab, with the same teacher, in other groups. Anything that clashes with your own classes is greyed out. No messaging ten people, no digging through the PDF for a free slot.',
    async run(d) {
      d.scene(null)
      if (!location.pathname.startsWith('/timetable')) {
        forgetSeenChanges()
        await d.go(timetable)
      }
      d.say(say('Ai pierdut un laborator?', 'Missed a lab?'))
      await d.click('button[aria-label$=": PW"]')
      await d.spot('[role="dialog"]')
      d.say(
        say('Recuperezi la altă grupă', 'Catch up with another group'),
        say(
          'Aceeași materie, același profesor, fără suprapuneri cu orele tale.',
          'Same course, same teacher, nothing that clashes with your own classes.',
        ),
      )
    },
  },
  {
    title: 'First-year guide',
    note: "Next, the guide for first-years: credits, attendance, exams, scholarships, fees, all in one place. Everything is checked against the university rules, but written simply. It's the guide I wish I'd had in my first week.",
    async run(d) {
      d.scene(null)
      d.cursor(false)
      await d.go('/guide')
      d.say(
        say('Ghidul de anul I', 'The first-year guide'),
        say(
          'Regulamentul UGAL, explicat de un student din anii mari.',
          'The UGAL rules, explained by a senior student.',
        ),
      )
      await d.wait(2400)
      for (let step = 0; step < 3; step++) {
        d.key('PageDown')
        await d.wait(2600)
      }
    },
  },
  {
    title: 'Professors',
    note: "Then the question everyone asks sooner or later: what is this professor actually like?\n\n(Score lights up.) Every professor gets an overall score, three pillars and a radar, built from real students' ratings. It only appears after five ratings, so no single student can be singled out.\n\n(Comments open.) Below are the comments, and each one is moderated before it appears.",
    async run(d) {
      d.scene(null)
      await d.go('/professors')
      d.say(say('Caută un profesor', 'Look up a professor'))
      await d.type('main input[type="search"]', 'Dob')
      await d.wait(500)
      await d.click(`a[href="/professors/${SHOW_PROFESSOR}"]`)
      await d.spot('.score')
      d.say(
        say('Scor general, trei piloni, radar', 'Overall score, three pillars, a radar'),
        say(
          'Apare doar după 5 evaluări, ca nimeni să nu poată fi recunoscut.',
          'Shown only after 5 ratings, so no one can be singled out.',
        ),
      )
      await d.wait(3600)
      await d.spot(null)
      await d.scrollTo('main h2.h2.mt-8', 1600)
      await d.click('main article details summary')
      d.say(
        say('Ce spun studenții', 'What students say'),
        say(
          'Comentariile trec prin moderare înainte să apară.',
          'Comments go through moderation before they appear.',
        ),
      )
    },
  },
  {
    title: 'Microsoft sign-in',
    note: 'To rate, you need an account, and that takes one tap: your university Microsoft account. Only @student.ugal.ro addresses get in, so every rating comes from a real student.',
    async run(d) {
      d.scene(null)
      useAuthStore().user = null
      await d.go('/signin')
      d.say(
        say('Intri cu contul de student', 'Sign in with your student account'),
        say(
          'Un tap cu Microsoft, doar pentru adrese @student.ugal.ro.',
          'One tap with Microsoft, only for @student.ugal.ro addresses.',
        ),
      )
      await d.point(MICROSOFT)
      await d.spot(MICROSOFT)
      await d.wait(400)
    },
  },
  {
    title: 'Rating, anonymously',
    note: "Besides professors, you can rate the course itself, in about a minute: how hard the exam was, whether the labs help, how the teacher explains, plus a comment if you want.\n\n(Envelope scene.) The obvious question: if I'm signed in, can you see what I wrote? No. Your browser seals a random token in an envelope. The server checks that you're enrolled in the course and stamps the envelope without looking inside. This is called a blind signature. Your rating travels with that token. We can prove you're a student, but not which one. Not even me, with direct database access.",
    async run(d) {
      d.scene(null)
      removeStoredStartingWith(rateDraftPrefix)
      await d.go('/rate')
      d.say(say('Evaluezi în două minute', 'Rate in two minutes'))
      await d.click(`a[href="/rate/${SHOW_OFFERING}"]`)
      await d.click(score('examDifficulty', 7))
      await d.click('[aria-labelledby$="-examFormat"] label:nth-child(1)')
      await d.click(score('labsHelp', 9))
      await d.click('.action-bar .btn-primary')
      await d.click('[aria-labelledby$="-hadTeacher"] label:nth-child(1)')
      for (const [axis, value] of [
        ['a04', 9],
        ['a07', 8],
        ['a10', 9],
        ['a14', 10],
      ] as const) {
        await d.click(score(axis, value))
      }
      await d.type(
        'section textarea',
        say(
          'Explică foarte clar, laboratoarele chiar ajută.',
          'Explains very clearly, the labs really help.',
        ),
      )
      await d.wait(700)
      d.hush()
      d.cursor(false)
      d.scene(AnonymityScene)
    },
  },
  {
    title: 'Sharing',
    note: 'Of course, a tool only works if students actually use it. You can send your whole week to the group as an image.\n\n(Preview cards.) And every shared link shows a proper preview card in WhatsApp or Telegram.',
    async run(d) {
      d.scene(null)
      forgetSeenChanges()
      await d.go(timetable)
      d.say(say('Trimite săptămâna grupei', 'Send the week to your group'))
      await d.click('button.more')
      await d.click(await d.find('[role="menuitem"]', t('timetable.share.action')))
      await d.spot('[role="dialog"]')
      await d.wait(3200)
      await d.click('[role="dialog"] button.btn-icon')
      await d.spot(null)
      d.hush()
      d.cursor(false)
      d.scene(PreviewScene)
    },
  },
  {
    title: 'Mobile first',
    note: 'All of this was designed for the phone first. On the way to class, your phone is enough: your next room, your professor, a rating in a minute.',
    async run(d) {
      d.hush()
      d.cursor(false)
      d.scene(MobileScene)
      await d.wait(400)
    },
  },
  {
    title: 'How it is built',
    note: 'Under the hood: a Vue web app, with Spring Boot on Java behind it. The database is split in two: who you are, and what was said. The only bridge between them is that blind token.\n\nAI made months of work possible here. My part was planning, choosing the stack and reviewing every feature.',
    async run(d) {
      d.hush()
      d.cursor(false)
      d.scene(ArchitectureScene)
      await d.wait(400)
    },
  },
  {
    title: 'Close',
    note: "And it's live today, at acieeadvisor.com. Next on my roadmap: a 3D room finder, and AI pre-moderation, so polite comments publish right away and rude ones go to a human.\n\nThe goal is simple: no first-year should have to figure it all out alone. If you know a first-year, or you're in a group chat, scan the code and share it. I'd really appreciate it.\n\nThat's all from me. Thank you for your attention!",
    async run(d) {
      d.hush()
      d.cursor(false)
      d.scene(CloseScene)
      await d.wait(400)
    },
  },
]
