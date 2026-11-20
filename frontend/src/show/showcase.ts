import type { Account } from '@/stores/auth'
import type { ProfessorListItem, ProfessorPage, Timetable, TimetableChange } from '@/api'
import type { RateableOffering } from '@/crypto/tokens'
import { i18n } from '@/i18n'
import { cannedResponse, NOT_FOUND } from './fetchCache'
import timetable from './data/timetable.json'
import lookups from './data/lookups.json'

// Every name below is invented for the pitch; none of them teaches at ACIEE.
export const SHOW_GROUP = { program: 'an_3_CTI', group: '22C31A' }
export const SHOW_PROFESSOR = 'elena-dobrescu'
export const SHOW_OFFERING = 9101

export const SHOW_USER: Account = {
  id: 990001,
  email: 'ioana.marin@student.ugal.ro',
  displayName: 'Ioana Marin',
  roles: [],
}

const department = 'Calculatoare și Tehnologia Informației'

const professors: ProfessorListItem[] = [
  {
    slug: SHOW_PROFESSOR,
    fullName: 'Elena Dobrescu',
    title: 'Conf. dr. ing.',
    department,
    subjects: 3,
    ratings: 64,
    overall: 8.6,
    photo: null,
  },
  {
    slug: 'mihai-vasilescu',
    fullName: 'Mihai Vasilescu',
    title: 'Prof. univ. dr. ing.',
    department,
    subjects: 2,
    ratings: 41,
    overall: 7.9,
    photo: null,
  },
  {
    slug: 'andreea-moraru',
    fullName: 'Andreea Moraru',
    title: 'Șef lucr. dr. ing.',
    department,
    subjects: 4,
    ratings: 57,
    overall: 9.1,
    photo: null,
  },
  {
    slug: 'cristian-lungu',
    fullName: 'Cristian Lungu',
    title: 'Conf. dr. ing.',
    department,
    subjects: 2,
    ratings: 33,
    overall: 6.4,
    photo: null,
  },
  {
    slug: 'ioana-ciobanu',
    fullName: 'Ioana Ciobanu',
    title: 'Asist. dr. ing.',
    department,
    subjects: 3,
    ratings: 48,
    overall: 8.8,
    photo: null,
  },
  {
    slug: 'bogdan-neagu',
    fullName: 'Bogdan Neagu',
    title: 'Șef lucr. dr.',
    department: 'Automatică și Electrotehnică',
    subjects: 2,
    ratings: 22,
    overall: 7.2,
    photo: null,
  },
  {
    slug: 'raluca-stoica',
    fullName: 'Raluca Stoica',
    title: 'Conf. dr.',
    department: 'Electronică și Telecomunicații',
    subjects: 1,
    ratings: 12,
    overall: 8.1,
    photo: null,
  },
  {
    slug: 'sorin-marinescu',
    fullName: 'Sorin Marinescu',
    title: 'Asist. dr. ing.',
    department,
    subjects: 2,
    ratings: 39,
    overall: 8.3,
    photo: null,
  },
  {
    slug: 'daniela-toma',
    fullName: 'Daniela Toma',
    title: 'Lect. dr.',
    department,
    subjects: 1,
    ratings: 3,
    overall: null,
    photo: null,
  },
]

const english = () => i18n.global.locale.value !== 'ro'

const professor = (): ProfessorPage => ({
  fullName: 'Elena Dobrescu',
  title: 'Conf. dr. ing.',
  department,
  archived: false,
  threshold: 5,
  ratings: 64,
  overall: 8.6,
  teaching: 8.9,
  practice: 8.2,
  grading: 8.5,
  bioRo:
    'Predă structuri de date, programare orientată pe obiecte și inteligență artificială. Cercetează algoritmi de căutare și planificare.',
  bioEn:
    'Teaches data structures, object-oriented programming and artificial intelligence. Researches search and planning algorithms.',
  photo: null,
  cvUrl: null,
  radar: { n: 64, scores: [8.7, 9.2, 8.4, 9.3, 8.6, 8.1, 8.8, 7.6, 8.9, 8.7, 7.9, 8.3, 8.8, 8.4] },
  subjects: [
    {
      subjectId: 1,
      subjectName: 'Structuri de date și algoritmi',
      itemId: 0,
      components: 'Curs, Laborator',
      firstYear: 2019,
      lastYear: 2027,
      radar: {
        n: 31,
        scores: [8.9, 9.4, 8.6, 9.5, 8.8, 8.0, 9.0, 7.4, 9.1, 8.9, 8.0, 8.5, 9.0, 8.6],
      },
      comments: [
        {
          id: 'c1',
          body: english()
            ? 'Walks through every algorithm step by step with drawings on the board. The labs really prepare you for the exam.'
            : 'Explică algoritmii pas cu pas, cu desene pe tablă. Laboratoarele chiar te pregătesc pentru examen.',
          week: '2026-01-12',
        },
        {
          id: 'c2',
          body: english()
            ? 'Strict at the exam but fair: the questions are exactly what was covered in lectures. Don’t leave the homework for the last night.'
            : 'Exigentă la examen, dar corectă: subiectele sunt exact ce s-a făcut la curs. Nu lăsa temele pe ultima seară.',
          week: '2026-01-19',
        },
        {
          id: 'c3',
          body: english()
            ? 'Answers quickly on Teams and posts the marking scheme after every test.'
            : 'Răspunde repede pe Teams și publică baremul după fiecare test.',
          week: '2025-06-02',
        },
      ],
    },
    {
      subjectId: 2,
      subjectName: 'Programare orientată pe obiecte',
      itemId: 0,
      components: 'Curs',
      firstYear: 2021,
      lastYear: 2027,
      radar: {
        n: 22,
        scores: [8.5, 9.0, 8.1, 9.1, 8.4, 8.2, 8.6, 7.8, 8.7, 8.5, 7.7, 8.1, 8.6, 8.2],
      },
      comments: [
        {
          id: 'c4',
          body: english()
            ? 'Well-structured course; the final project is hard but you learn a lot from it.'
            : 'Cursul e bine structurat, proiectul final e greu, dar înveți mult din el.',
          week: '2025-06-09',
        },
      ],
    },
    {
      subjectId: 3,
      subjectName: 'Inteligență artificială',
      itemId: 0,
      components: 'Curs',
      firstYear: 2023,
      lastYear: 2027,
      radar: {
        n: 11,
        scores: [8.6, 9.1, 8.5, 9.2, 8.5, 8.0, 8.7, 7.7, 8.8, 8.6, 8.0, 8.2, 8.7, 8.3],
      },
      comments: [],
    },
  ],
})

const offerings: RateableOffering[] = [
  ['Structuri de date și algoritmi', SHOW_OFFERING],
  ['Baze de date II', 9102],
  ['Rețele de calculatoare II', 9103],
  ['Programare Web', 9104],
].map(([title, offeringId]) => ({
  offeringId: offeringId as number,
  title: title as string,
  academicYear: '2026–2027',
  semesterNo: 1,
  opensAt: '2026-11-01T00:00:00Z',
  open: true,
  fingerprint: 'show',
  issued: false,
}))

const offeringForm = {
  title: 'Structuri de date și algoritmi',
  academicYear: '2026–2027',
  semesterNo: 1,
  assignments: [
    {
      id: 1,
      componentLabel: 'Curs',
      professor: 'Elena Dobrescu',
      title: 'Conf. dr. ing.',
      subgroup: null,
    },
    {
      id: 2,
      componentLabel: 'Laborator',
      professor: 'Sorin Marinescu',
      title: 'Asist. dr. ing.',
      subgroup: 'A',
    },
  ],
}

function changes(): TimetableChange[] {
  const detectedAt = new Date(Date.now() - 3 * 3600e3).toISOString()
  const lab = {
    day: 4,
    startTime: '14:00',
    endTime: '15:50',
    weeks: 'ALL',
    kind: 'L',
    subject: 'BDII',
    teacher: 'C. Sandu',
  } as const
  return [
    { ...lab, change: 'removed', detectedAt, room: 'G408' },
    { ...lab, change: 'added', detectedAt, room: 'G406' },
  ]
}

export function registerShowcase() {
  cannedResponse(/^\/api\/timetable$/, () => ({
    ...(timetable as Timetable),
    checkedAt: new Date(Date.now() - 20 * 60e3).toISOString(),
  }))
  cannedResponse(/^\/api\/timetable\/changes\?/, (url) =>
    url.searchParams.get('group') === SHOW_GROUP.group ? changes() : [],
  )
  cannedResponse(/^\/api\/catalog\/lookups$/, () => lookups)
  cannedResponse(/^\/api\/catalog\/professors$/, () => professors)
  cannedResponse(/^\/api\/professors\//, (url) =>
    url.pathname.endsWith(SHOW_PROFESSOR) ? professor() : NOT_FOUND,
  )
  cannedResponse(/^\/api\/auth\/me$/, () => SHOW_USER)
  cannedResponse(/^\/api\/auth\/providers$/, () => ({ microsoft: true }))
  cannedResponse(/^\/api\/vault$/, () => NOT_FOUND)
  cannedResponse(/^\/api\/tokens\/offerings$/, () => offerings)
  cannedResponse(/^\/anon\/offerings\//, () => offeringForm)
}
