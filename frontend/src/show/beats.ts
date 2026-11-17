import { defineAsyncComponent } from 'vue'
import { i18n } from '@/i18n'
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
const CloseScene = defineAsyncComponent(() => import('./scenes/CloseScene.vue'))

const t = (key: string) => i18n.global.t(key)

function say(ro: string, en: string) {
  return i18n.global.locale.value === 'ro' ? ro : en
}

const score = (field: string, value: number) =>
  `[aria-labelledby$="-${field}"] label:nth-child(${value + 1})`

const timetable = `/timetable?g=${SHOW_GROUP.group}`

function forgetSeenChanges() {
  try {
    localStorage.removeItem(`timetable.changesSeen.${SHOW_GROUP.program}.${SHOW_GROUP.group}`)
    localStorage.setItem('timetable.group', JSON.stringify(SHOW_GROUP.group))
  } catch {
    // storage blocked: the note shows anyway on a fresh tab
  }
}

export const beats: Beat[] = [
  {
    title: 'The problem',
    note: 'Who do you pick, how hard is the exam? Today that lives in scattered group chats. ACIEE Advisor puts it in one place, anonymously.',
    async run(d) {
      d.hush()
      d.cursor(false)
      d.scene(HookScene)
      await d.wait(400)
    },
  },
  {
    title: 'Timetable',
    note: 'The faculty timetable is a set of PDFs. We read them every hour and show your group, your week, odd or even. When a room moves, you get a note.',
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
    note: 'Missed a lab? It finds the same lab with the same teacher at another group this week and next, and greys out what clashes with your own classes.',
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
    note: 'Credits, attendance, exams, scholarships, fees: checked against the university rules, written like a senior student would explain it.',
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
    note: 'Every professor gets a public page once five students have rated them: an overall score, three pillars, a radar, and comments.',
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
      await d.click('main details summary')
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
    title: 'Rating, anonymously',
    note: 'Rating takes a minute. The key point: the server signs a blinded token, so it knows you are a student in this course but can never link the rating back to you.',
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
    note: 'Growth is built in: share your week as an image, and every course or professor link shows a preview card in WhatsApp and Telegram.',
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
    title: 'Close',
    note: 'Live at acieeadvisor.com. Ask: group leaders to share it with their groups before the exam session.',
    async run(d) {
      d.hush()
      d.cursor(false)
      d.scene(CloseScene)
      await d.wait(400)
    },
  },
]
