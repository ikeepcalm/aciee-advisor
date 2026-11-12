interface GuideSource {
  label: string
  url: string
}

interface GuideSection {
  id: string
  title: string
  short: string
  points: string[]
  action?: { to: string; label: string }
  sources: GuideSource[]
}

export interface Guide {
  title: string
  lede: string
  checked: string
  sources: string
  sections: GuideSection[]
}

const RAUS = 'https://www.ugal.ro/files/studenti/2024/RAUS_2024.pdf'
const FEES =
  'https://www.ugal.ro/files/taxe/2026-2027/Anexa_la_HS_nr._49_din_20.3.2026_Taxe_2026-2027_licenta_IF_si_dual_modificat_cu_FSM3000.pdf'
const CALENDAR =
  'https://www.ugal.ro/files/hotarari/hs/2026/HS_059_2026_Anexa_1-Structura_anului_universitar_2026-2027.pdf'
const SCHOLARSHIPS = 'https://www.ugal.ro/files/hotarari/hs/2026/HS_252_2026_Anexa_1.pdf'
const SCHOLARSHIPS_PAGE = 'https://www.ugal.ro/studenti/burse'
const CONTACT = 'https://aciee.ugal.ro/contact'

const ro: Guide = {
  title: 'Ghidul de anul I',
  lede: 'Regulile care contează în primul an, pe scurt. Fiecare secțiune are sursa oficială dedesubt.',
  checked:
    'Verificat pe 12 noiembrie 2026 din regulamentele UGAL. Regulile se pot schimba, așa că verifică sursa înainte de o decizie importantă.',
  sources: 'Sursa',
  sections: [
    {
      id: 'credits',
      title: 'Credite și trecerea în anul II',
      short: 'Ai nevoie de cel puțin 40 de credite din anul I ca să treci în anul II.',
      points: [
        'Toate creditele din anul I: promovat în anul II.',
        'Cel puțin 40 de credite: înscris în anul II cu credite restante, pe care le refaci mai târziu.',
        '39 de credite sau mai puțin: reînscris în anul I, cu taxă (60 de lei pe credit în 2026-2027).',
        'Pragul crește: 80 de credite adunate pentru anul III și 120 pentru anul IV.',
      ],
      sources: [
        { label: 'Regulamentul activității studenților (RAUS), Anexa 1', url: RAUS },
        { label: 'Taxe 2026-2027', url: FEES },
      ],
    },
    {
      id: 'attendance',
      title: 'Prezența la laborator, seminar și proiect',
      short:
        'Laboratoarele, seminarele și proiectele sunt obligatorii. Cu absențe nerecuperate nu intri în examen.',
      points: [
        'O absență la o activitate aplicativă îți ia dreptul de a da examenul până o recuperezi.',
        'Recuperarea e gratuită dacă mergi cu altă grupă, în programul normal.',
        'Poți recupera cel mult jumătate din activitățile unei discipline într-un an. Peste, refaci toată activitatea.',
        'Recuperarea în program suplimentar se plătește: 35 de lei pe lucrare la ACIEE.',
        'Cursul nu apare ca obligatoriu în regulament, dar profesorul poate cere prezență prin fișa disciplinei.',
      ],
      action: { to: '/timetable', label: 'Găsește o grupă cu care să recuperezi' },
      sources: [
        { label: 'RAUS, art. 11 și 12', url: RAUS },
        { label: 'Taxe 2026-2027', url: FEES },
      ],
    },
    {
      id: 'exams',
      title: 'Sesiune, restanțe și mărire de notă',
      short: 'Ai trei sesiuni pe an: iarna, vara și restanțele din iulie.',
      points: [
        'Într-o sesiune dai un examen o singură dată.',
        'Restanțele din 2026-2027 sunt între 17 sau 24 și 30 iulie 2027. Pentru anii care nu sunt terminali nu există sesiune în septembrie.',
        'Un examen restant din anii anteriori costă 40 de lei.',
        'Mărirea de notă e doar pentru integraliști, în ultima sesiune, la cel mult trei discipline. Dacă iei o notă mai mică, nu se trece în catalog.',
        'Poți contesta doar o probă scrisă, în două zile lucrătoare de la afișarea notelor. Recorectează o comisie de trei profesori, iar nota ei e finală.',
        'Programarea examenelor apare cu cel puțin două săptămâni înainte, iar notele în două zile lucrătoare. Ia carnetul de student la examen.',
      ],
      sources: [
        { label: 'RAUS, art. 14 - 18', url: RAUS },
        { label: 'Structura anului 2026-2027', url: CALENDAR },
      ],
    },
    {
      id: 'calendar',
      title: 'Anul universitar 2026-2027',
      short: 'Câte 14 săptămâni de cursuri pe semestru, cu sesiunea imediat după.',
      points: [
        '28 septembrie - 18 decembrie: 12 săptămâni de cursuri.',
        '19 decembrie - 10 ianuarie: vacanța de iarnă.',
        '11 - 22 ianuarie: ultimele 2 săptămâni de cursuri.',
        '23 ianuarie - 14 februarie: sesiunea de iarnă.',
        '22 februarie - 29 aprilie și 10 mai - 4 iunie: semestrul al doilea, cu vacanța de Paște între ele.',
        '5 - 27 iunie: sesiunea de vară, apoi practica și restanțele din iulie.',
      ],
      sources: [{ label: 'Structura anului universitar 2026-2027', url: CALENDAR }],
    },
    {
      id: 'scholarships',
      title: 'Burse',
      short:
        'Bursele pe medie cer să fii integralist. În primul semestru din anul I contează media de la admitere.',
      points: [
        'Tipuri de la buget: de performanță, de performanță pentru studiu, sociale, speciale și sportive.',
        'Anul I: în semestrul I se ia media de admitere, în semestrul II media din sesiunea de iarnă.',
        'Bursa de performanță cere cel puțin media 8,50, iar bursa specială cel puțin 8,00.',
        'Banii se împart pe an de studiu și pe specializare, după numărul de studenți de la buget.',
        'Bursa socială cere loc la buget, învățământ cu frecvență și vârsta sub 35 de ani. Dosarele pentru 2026-2027 s-au depus între 2 și 10 noiembrie.',
      ],
      sources: [
        { label: 'Metodologia burselor 2026', url: SCHOLARSHIPS },
        { label: 'Pagina de burse UGAL', url: SCHOLARSHIPS_PAGE },
      ],
    },
    {
      id: 'budget',
      title: 'Buget sau taxă',
      short: 'Locul la buget se reîmparte la începutul fiecărui an, după rezultate.',
      points: [
        'Statutul de buget sau taxă se stabilește din nou la începutul fiecărui an universitar.',
        'Trecerea între taxă și buget nu costă nimic în plus.',
        'Taxa de școlarizare la ACIEE în 2026-2027: 3600 de lei pe an în anii I și II.',
      ],
      sources: [
        { label: 'RAUS, art. 11 și 40', url: RAUS },
        { label: 'Taxe 2026-2027', url: FEES },
      ],
    },
    {
      id: 'contact',
      title: 'Secretariatul facultății',
      short: 'Pentru acte, adeverințe și situația școlară mergi la secretariat.',
      points: [
        'Str. Domnească 111 (intrarea din Str. Științei), Corpul Y, etajul 1.',
        'secretariat.faciee@ugal.ro',
        'Telefon: 0336 130 236 sau 0236 470 905.',
      ],
      sources: [{ label: 'Contact ACIEE', url: CONTACT }],
    },
  ],
}

const en: Guide = {
  title: 'First-year guide',
  lede: 'The rules that matter in your first year, in short. Each section links its official source.',
  checked:
    'Checked on 12 November 2026 against the UGAL regulations. Rules can change, so check the source before an important decision.',
  sources: 'Source',
  sections: [
    {
      id: 'credits',
      title: 'Credits and moving into year 2',
      short: 'You need at least 40 credits from year 1 to move into year 2.',
      points: [
        'All year 1 credits: you pass into year 2.',
        'At least 40 credits: you enrol in year 2 with outstanding credits to retake later.',
        '39 credits or fewer: you repeat year 1 and pay for it (60 lei per credit in 2026-2027).',
        'The bar rises: 80 credits in total for year 3 and 120 for year 4.',
      ],
      sources: [
        { label: 'Student activity regulation (RAUS), Annex 1', url: RAUS },
        { label: 'Fees 2026-2027', url: FEES },
      ],
    },
    {
      id: 'attendance',
      title: 'Attendance at labs, seminars and projects',
      short:
        'Labs, seminars and projects are compulsory. With absences you have not made up, you cannot sit the exam.',
      points: [
        'Missing a lab, seminar or project session takes away your right to sit the exam until you make it up.',
        'Making it up is free if you join another group during the normal timetable.',
        'You can make up at most half of a subject’s sessions in a year. Beyond that you redo the whole activity.',
        'Make-up sessions outside the timetable are paid: 35 lei per session at ACIEE.',
        'Lectures are not listed as compulsory in the regulation, but a professor can require attendance in the course sheet.',
      ],
      action: { to: '/timetable', label: 'Find a group to make up a class with' },
      sources: [
        { label: 'RAUS, art. 11 and 12', url: RAUS },
        { label: 'Fees 2026-2027', url: FEES },
      ],
    },
    {
      id: 'exams',
      title: 'Exam sessions, resits and grade improvement',
      short: 'You get three sessions a year: winter, summer and the resits in July.',
      points: [
        'You can sit an exam only once per session.',
        'Resits for 2026-2027 run from 17 or 24 July to 30 July 2027. There is no September session except for final-year students.',
        'An outstanding exam from an earlier year costs 40 lei.',
        'Grade improvement is only for students who passed everything, in the last session, for up to three subjects. A lower grade is not recorded.',
        'You can appeal only written exams, within two working days of the results. A committee of three professors re-marks it, and its grade is final.',
        'The exam schedule is out at least two weeks ahead, and results within two working days. Bring your student record book to the exam.',
      ],
      sources: [
        { label: 'RAUS, art. 14 - 18', url: RAUS },
        { label: 'Academic year 2026-2027', url: CALENDAR },
      ],
    },
    {
      id: 'calendar',
      title: 'Academic year 2026-2027',
      short: '14 teaching weeks per semester, with the exam session right after.',
      points: [
        '28 September - 18 December: 12 teaching weeks.',
        '19 December - 10 January: winter holiday.',
        '11 - 22 January: the last 2 teaching weeks.',
        '23 January - 14 February: winter exam session.',
        '22 February - 29 April and 10 May - 4 June: second semester, with the Easter break in between.',
        '5 - 27 June: summer exam session, then internship and the July resits.',
      ],
      sources: [{ label: 'Academic year structure 2026-2027', url: CALENDAR }],
    },
    {
      id: 'scholarships',
      title: 'Scholarships',
      short:
        'Grade-based scholarships need every exam passed. In your first semester, the admission average counts.',
      points: [
        'State-funded types: performance, study performance, social, special and sports.',
        'Year 1: semester 1 uses your admission average, semester 2 your winter session average.',
        'The performance scholarship needs an average of at least 8.50, the special one at least 8.00.',
        'The money is split by study year and programme, by the number of state-funded students.',
        'The social scholarship needs a state-funded place, full-time study and age under 35. Applications for 2026-2027 ran from 2 to 10 November.',
      ],
      sources: [
        { label: 'Scholarship rules 2026', url: SCHOLARSHIPS },
        { label: 'UGAL scholarship page', url: SCHOLARSHIPS_PAGE },
      ],
    },
    {
      id: 'budget',
      title: 'State-funded or fee-paying',
      short: 'State-funded places are reassigned at the start of every year, by results.',
      points: [
        'Your state-funded or fee-paying status is set again at the start of each academic year.',
        'Switching between the two costs nothing extra.',
        'ACIEE tuition in 2026-2027: 3600 lei a year in years 1 and 2.',
      ],
      sources: [
        { label: 'RAUS, art. 11 and 40', url: RAUS },
        { label: 'Fees 2026-2027', url: FEES },
      ],
    },
    {
      id: 'contact',
      title: 'Faculty secretariat',
      short: 'For papers, certificates and your academic record, go to the secretariat.',
      points: [
        'Str. Domnească 111 (enter from Str. Științei), building Y, 1st floor.',
        'secretariat.faciee@ugal.ro',
        'Phone: 0336 130 236 or 0236 470 905.',
      ],
      sources: [{ label: 'ACIEE contact', url: CONTACT }],
    },
  ],
}

const uk: Guide = {
  title: 'Гід для першого курсу',
  lede: 'Правила, які важливі на першому курсі, коротко. Під кожним розділом є офіційне джерело.',
  checked:
    'Перевірено 12 листопада 2026 за регламентами UGAL. Правила можуть змінитися, тому перед важливим рішенням перевір джерело.',
  sources: 'Джерело',
  sections: [
    {
      id: 'credits',
      title: 'Кредити та перехід на II курс',
      short: 'Щоб перейти на II курс, потрібно щонайменше 40 кредитів з I курсу.',
      points: [
        'Усі кредити I курсу: переходиш на II курс.',
        'Щонайменше 40 кредитів: записують на II курс із заборгованими кредитами (credite restante), які перескладаєш пізніше.',
        '39 кредитів або менше: залишаєшся на I курсі повторно, за плату (60 леїв за кредит у 2026-2027).',
        'Поріг зростає: 80 кредитів загалом для III курсу і 120 для IV курсу.',
      ],
      sources: [
        { label: 'Регламент діяльності студентів (RAUS), Додаток 1', url: RAUS },
        { label: 'Оплата 2026-2027', url: FEES },
      ],
    },
    {
      id: 'attendance',
      title: 'Відвідування лабораторних, семінарів і проєктів',
      short:
        'Лабораторні, семінари та проєкти обов’язкові. З невідпрацьованими пропусками до іспиту не допустять.',
      points: [
        'Пропуск лабораторної, семінару чи проєкту позбавляє права складати іспит, доки ти його не відпрацюєш.',
        'Відпрацювання безкоштовне, якщо ти ходиш з іншою групою в звичайному розкладі.',
        'За рік можна відпрацювати щонайбільше половину занять з дисципліни. Якщо більше, повторюєш усю активність.',
        'Відпрацювання поза розкладом платне: 35 леїв за роботу на ACIEE.',
        'Лекції в регламенті не вказані як обов’язкові, але викладач може вимагати відвідування в силабусі дисципліни.',
      ],
      action: { to: '/timetable', label: 'Знайди групу, з якою відпрацювати' },
      sources: [
        { label: 'RAUS, ст. 11 і 12', url: RAUS },
        { label: 'Оплата 2026-2027', url: FEES },
      ],
    },
    {
      id: 'exams',
      title: 'Сесія, перездачі (restanțe) і підвищення оцінки (mărire de notă)',
      short: 'На рік є три сесії: зимова, літня і перездачі в липні.',
      points: [
        'Іспит можна складати лише один раз за сесію.',
        'Перездачі 2026-2027 тривають з 17 або 24 до 30 липня 2027. Вересневої сесії немає, крім випускних курсів.',
        'Борговий іспит минулих років коштує 40 леїв.',
        'Підвищення оцінки (mărire de notă) лише для тих, хто склав усе (integralist), в останню сесію, щонайбільше з трьох дисциплін. Нижчу оцінку в залікову книжку не вносять.',
        'Оскаржити можна лише письмову роботу, протягом двох робочих днів після оголошення оцінок. Перевіряє комісія з трьох викладачів, її оцінка остаточна.',
        'Розклад іспитів оприлюднюють щонайменше за два тижні, а оцінки протягом двох робочих днів. Візьми на іспит студентську залікову книжку (carnet de student).',
      ],
      sources: [
        { label: 'RAUS, ст. 14 - 18', url: RAUS },
        { label: 'Структура року 2026-2027', url: CALENDAR },
      ],
    },
    {
      id: 'calendar',
      title: 'Навчальний рік 2026-2027',
      short: 'По 14 тижнів занять на семестр, а сесія одразу після них.',
      points: [
        '28 вересня - 18 грудня: 12 тижнів занять.',
        '19 грудня - 10 січня: зимові канікули.',
        '11 - 22 січня: останні 2 тижні занять.',
        '23 січня - 14 лютого: зимова сесія.',
        '22 лютого - 29 квітня і 10 травня - 4 червня: другий семестр, між ними великодні канікули.',
        '5 - 27 червня: літня сесія, потім практика і перездачі в липні.',
      ],
      sources: [{ label: 'Структура навчального року 2026-2027', url: CALENDAR }],
    },
    {
      id: 'scholarships',
      title: 'Стипендії',
      short:
        'Стипендії за успішність потребують, щоб ти склав усе (integralist). У першому семестрі I курсу враховують середній бал вступу.',
      points: [
        'Види для бюджету: за успішність (bursă de performanță), за успішність у навчанні, соціальні (bursă socială), спеціальні та спортивні.',
        'I курс: у I семестрі беруть середній бал вступу, у II семестрі середній бал зимової сесії.',
        'Стипендія за успішність вимагає середній бал щонайменше 8,50, а спеціальна щонайменше 8,00.',
        'Гроші ділять за роком навчання і спеціальністю, залежно від кількості студентів на бюджеті.',
        'Соціальна стипендія вимагає місце на бюджеті (buget), денну форму навчання і вік до 35 років. Документи на 2026-2027 подавали з 2 до 10 листопада.',
      ],
      sources: [
        { label: 'Методика призначення стипендій 2026', url: SCHOLARSHIPS },
        { label: 'Сторінка стипендій UGAL', url: SCHOLARSHIPS_PAGE },
      ],
    },
    {
      id: 'budget',
      title: 'Бюджет (buget) або контракт (taxă)',
      short: 'Бюджетні місця перерозподіляють на початку кожного року за результатами.',
      points: [
        'Статус бюджету або контракту визначають заново на початку кожного навчального року.',
        'Перехід між контрактом і бюджетом не коштує нічого додатково.',
        'Плата за навчання на ACIEE у 2026-2027: 3600 леїв на рік на I і II курсах.',
      ],
      sources: [
        { label: 'RAUS, ст. 11 і 40', url: RAUS },
        { label: 'Оплата 2026-2027', url: FEES },
      ],
    },
    {
      id: 'contact',
      title: 'Секретаріат факультету',
      short: 'По документи, довідки та успішність звертайся до секретаріату.',
      points: [
        'Str. Domnească 111 (вхід з Str. Științei), корпус Y, 1 поверх.',
        'secretariat.faciee@ugal.ro',
        'Телефон: 0336 130 236 або 0236 470 905.',
      ],
      sources: [{ label: 'Контакти ACIEE', url: CONTACT }],
    },
  ],
}

const ru: Guide = {
  title: 'Гид для первого курса',
  lede: 'Правила, которые важны на первом курсе, коротко. Под каждым разделом есть официальный источник.',
  checked:
    'Проверено 12 ноября 2026 по регламентам UGAL. Правила могут измениться, поэтому перед важным решением проверь источник.',
  sources: 'Источник',
  sections: [
    {
      id: 'credits',
      title: 'Кредиты и переход на II курс',
      short: 'Чтобы перейти на II курс, нужно не менее 40 кредитов с I курса.',
      points: [
        'Все кредиты I курса: переходишь на II курс.',
        'Не менее 40 кредитов: тебя записывают на II курс с задолженными кредитами (credite restante), которые пересдаёшь позже.',
        '39 кредитов или меньше: остаёшься на I курсе повторно, за плату (60 леев за кредит в 2026-2027).',
        'Порог растёт: 80 кредитов в сумме для III курса и 120 для IV курса.',
      ],
      sources: [
        { label: 'Регламент деятельности студентов (RAUS), Приложение 1', url: RAUS },
        { label: 'Оплата 2026-2027', url: FEES },
      ],
    },
    {
      id: 'attendance',
      title: 'Посещаемость лабораторных, семинаров и проектов',
      short:
        'Лабораторные, семинары и проекты обязательны. С неотработанными пропусками к экзамену не допустят.',
      points: [
        'Пропуск лабораторной, семинара или проекта лишает права сдавать экзамен, пока ты его не отработаешь.',
        'Отработка бесплатная, если ты ходишь с другой группой в обычном расписании.',
        'За год можно отработать не более половины занятий по дисциплине. Если больше, повторяешь всю активность.',
        'Отработка вне расписания платная: 35 леев за работу на ACIEE.',
        'Лекции в регламенте не указаны как обязательные, но преподаватель может потребовать посещаемость в карточке дисциплины.',
      ],
      action: { to: '/timetable', label: 'Найди группу, с которой отработать' },
      sources: [
        { label: 'RAUS, ст. 11 и 12', url: RAUS },
        { label: 'Оплата 2026-2027', url: FEES },
      ],
    },
    {
      id: 'exams',
      title: 'Сессия, пересдачи (restanțe) и повышение оценки (mărire de notă)',
      short: 'В году три сессии: зимняя, летняя и пересдачи в июле.',
      points: [
        'Экзамен можно сдавать только один раз за сессию.',
        'Пересдачи 2026-2027 идут с 17 или 24 по 30 июля 2027. Сентябрьской сессии нет, кроме выпускных курсов.',
        'Долговой экзамен прошлых лет стоит 40 леев.',
        'Повышение оценки (mărire de notă) только для тех, кто сдал всё (integralist), в последнюю сессию, не более чем по трём дисциплинам. Более низкую оценку в зачётку не вносят.',
        'Оспорить можно только письменную работу, в течение двух рабочих дней после объявления оценок. Перепроверяет комиссия из трёх преподавателей, её оценка окончательная.',
        'Расписание экзаменов публикуют не менее чем за две недели, а оценки в течение двух рабочих дней. Возьми на экзамен студенческую зачётную книжку (carnet de student).',
      ],
      sources: [
        { label: 'RAUS, ст. 14 - 18', url: RAUS },
        { label: 'Структура года 2026-2027', url: CALENDAR },
      ],
    },
    {
      id: 'calendar',
      title: 'Учебный год 2026-2027',
      short: 'По 14 недель занятий в семестре, а сессия сразу после них.',
      points: [
        '28 сентября - 18 декабря: 12 недель занятий.',
        '19 декабря - 10 января: зимние каникулы.',
        '11 - 22 января: последние 2 недели занятий.',
        '23 января - 14 февраля: зимняя сессия.',
        '22 февраля - 29 апреля и 10 мая - 4 июня: второй семестр, между ними пасхальные каникулы.',
        '5 - 27 июня: летняя сессия, потом практика и пересдачи в июле.',
      ],
      sources: [{ label: 'Структура учебного года 2026-2027', url: CALENDAR }],
    },
    {
      id: 'scholarships',
      title: 'Стипендии',
      short:
        'Стипендии за успеваемость требуют, чтобы ты сдал всё (integralist). В первом семестре I курса учитывают средний балл при поступлении.',
      points: [
        'Виды для бюджета: за успеваемость (bursă de performanță), за успеваемость в учёбе, социальные (bursă socială), специальные и спортивные.',
        'I курс: в I семестре берут средний балл при поступлении, во II семестре средний балл зимней сессии.',
        'Стипендия за успеваемость требует средний балл не менее 8,50, а специальная не менее 8,00.',
        'Деньги делят по году обучения и специальности, по числу студентов на бюджете.',
        'Социальная стипендия требует место на бюджете (buget), очную форму обучения и возраст до 35 лет. Документы на 2026-2027 подавали со 2 по 10 ноября.',
      ],
      sources: [
        { label: 'Методика назначения стипендий 2026', url: SCHOLARSHIPS },
        { label: 'Страница стипендий UGAL', url: SCHOLARSHIPS_PAGE },
      ],
    },
    {
      id: 'budget',
      title: 'Бюджет (buget) или контракт (taxă)',
      short: 'Бюджетные места перераспределяют в начале каждого года по результатам.',
      points: [
        'Статус бюджета или контракта определяют заново в начале каждого учебного года.',
        'Переход между контрактом и бюджетом ничего не стоит дополнительно.',
        'Плата за обучение на ACIEE в 2026-2027: 3600 леев в год на I и II курсах.',
      ],
      sources: [
        { label: 'RAUS, ст. 11 и 40', url: RAUS },
        { label: 'Оплата 2026-2027', url: FEES },
      ],
    },
    {
      id: 'contact',
      title: 'Секретариат факультета',
      short: 'За документами, справками и данными об успеваемости иди в секретариат.',
      points: [
        'Str. Domnească 111 (вход со Str. Științei), корпус Y, 1 этаж.',
        'secretariat.faciee@ugal.ro',
        'Телефон: 0336 130 236 или 0236 470 905.',
      ],
      sources: [{ label: 'Контакты ACIEE', url: CONTACT }],
    },
  ],
}

const GUIDES: Record<string, Guide> = { ro, en, uk, ru }

export function guideFor(locale: string): Guide {
  return GUIDES[locale] ?? en
}
