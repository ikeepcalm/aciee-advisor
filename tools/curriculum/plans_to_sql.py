"""Turn UGAL ACIEE study-plan PDFs into a Liquibase changeset.

Usage: python plans_to_sql.py C Calculatoare 2023=Pl_Inv_..._2023.pdf 2024=... 2025=... > seed/001-calculatoare.sql
The first two arguments are the specialization code and name. The last intake gets an open-ended range
until a newer plan is added.
Facultative (FA) courses are skipped: students opt in one by one, not as a group.
"""
import contextlib
import re
import sys
import unicodedata

import pymupdf

CODE = re.compile(r'^(\d{4})\.(\d)(OB|OP|FA)\d+[A-Z]$')
COMPONENTS = ('lecture', 'seminar', 'lab', 'project')


def normalize(name):
    name = name.replace('ş', 'ș').replace('Ş', 'Ș').replace('ţ', 'ț').replace('Ţ', 'Ț')
    return re.sub(r'\s+', ' ', name).strip()


def match_key(name):
    plain = unicodedata.normalize('NFKD', name.casefold())
    return ''.join(c for c in plain if not unicodedata.combining(c))


def taught_in(block):
    return block[5].strip() not in ('', '-') or block[4].strip() not in ('', '-')


def hours(block):
    return [int(cell) if cell.strip().isdigit() else 0 for cell in block[:4]]


def parse(path):
    rows = []
    for page in pymupdf.open(path):
        for table in page.find_tables(strategy='lines_strict').tables:
            for cells in table.extract():
                cells = [(c or '').replace('\n', ' ').strip() for c in cells]
                at = next((i for i, c in enumerate(cells) if CODE.match(c)), None)
                if at is None:
                    continue
                domain, year, kind = CODE.match(cells[at]).groups()
                rows.append({
                    'code': cells[at], 'domain': domain, 'name': normalize(cells[at - 2]), 'kind': kind,
                    'year': int(year), 'new_package': cells[at - 1] != '',
                    'sem1': taught_in(cells[at + 1:at + 7]), 'sem2': taught_in(cells[at + 7:at + 13]),
                    'hours1': hours(cells[at + 1:at + 7]), 'hours2': hours(cells[at + 7:at + 13]),
                })
    items = []
    previous = None
    slots = {}
    for row in rows:
        if not row['new_package']:
            for key in ('sem1', 'sem2', 'hours1', 'hours2', 'slot'):
                row[key] = previous[key]
        if row['kind'] == 'FA':
            previous = row | {'slot': None}
            continue
        semester = 1 if row['sem1'] else 2
        if row['new_package']:
            row['slot'] = None
            if row['kind'] == 'OP':
                key = (row['year'], semester)
                slots[key] = slots.get(key, 0) + 1
                row['slot'] = slots[key]
        assert row['sem1'] or row['sem2'], row
        item = row | {'semester': semester, 'hours': row['hours1'] if semester == 1 else row['hours2']}
        twin = next((i for i in items if (match_key(i['name']), i['year'], i['semester'])
                     == (match_key(item['name']), item['year'], item['semester'])), None)
        if twin:
            twin['hours'] = [a + b for a, b in zip(twin['hours'], item['hours'])]
        else:
            items.append(item)
        previous = row
    names = [item['name'] for item in items]
    for item in items:
        if names.count(item['name']) > 1:
            item['name'] = f"{item['name']} (anul {item['year']})"
    return items


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def main(code, name, args):
    with contextlib.redirect_stdout(sys.stderr):
        plans = sorted((int(a.split('=', 1)[0]), parse(a.split('=', 1)[1])) for a in args)
    domains = {item['domain'] for _, items in plans for item in items}
    assert len(domains) == 1, domains
    domain = domains.pop()
    subjects = {}
    for intake, items in reversed(plans):
        for item in items:
            key = match_key(item['name'])
            if key not in subjects:
                taken = item['code'] in (code for code, _ in subjects.values())
                subjects[key] = (f"{item['code']}-{intake}" if taken else item['code'], item['name'])

    print('--liquibase formatted sql\n')
    print(f"--changeset aciee:seed-{match_key(name).replace(' ', '-')}")
    print("INSERT INTO core.faculty (code, name_ro)\n"
          "VALUES ('ACIEE', 'Facultatea de Automatică, Calculatoare, Inginerie Electrică și Electronică')\n"
          "ON CONFLICT (code) DO NOTHING;")
    print("INSERT INTO core.specialization (faculty_id, code, name_ro, degree, duration_years)\n"
          f"SELECT id, {quote(code)}, {quote(name)}, 'BSc', 4 FROM core.faculty WHERE code = 'ACIEE';")
    ranges = []
    for i, (intake, _) in enumerate(plans):
        upper = str(plans[i + 1][0]) if i + 1 < len(plans) else ''
        ranges.append(f"({intake}, '[{intake},{upper})')")
    print("INSERT INTO core.study_plan (specialization_id, intake)\n"
          "SELECT s.id, v.intake::int4range FROM core.specialization s\n"
          f"CROSS JOIN (VALUES {', '.join(ranges)}) v(start, intake)\nWHERE s.code = {quote(code)};")
    print('INSERT INTO core.subject (code, name_ro) VALUES\n'
          + ',\n'.join(f'    ({quote(code)}, {quote(name)})' for code, name in sorted(subjects.values()))
          + ';')
    values = [f"    ({intake}, {quote(subjects[match_key(item['name'])][0])}, {item['year']}, {item['semester']}, "
              f"{item['slot'] or 'NULL'}, {', '.join(map(str, item['hours']))})"
              for intake, items in plans for item in items]
    hour_columns = ', '.join(f'{component}_hours' for component in COMPONENTS)
    print(f'INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no, elective_slot, {hour_columns})\n'
          'SELECT p.id, sub.id, v.study_year, v.semester_no, v.elective_slot, '
          + ', '.join(f'v.{component}_hours' for component in COMPONENTS) + '\nFROM (VALUES\n'
          + ',\n'.join(values)
          + f'\n) v(intake, subject_code, study_year, semester_no, elective_slot, {hour_columns})\n'
          f"JOIN core.specialization s ON s.code = {quote(code)}\n"
          'JOIN core.study_plan p ON p.specialization_id = s.id AND lower(p.intake) = v.intake\n'
          'JOIN core.subject sub ON sub.code = v.subject_code;')
    print("--rollback DELETE FROM core.curriculum_item WHERE plan_id IN (SELECT p.id FROM core.study_plan p "
          f"JOIN core.specialization s ON s.id = p.specialization_id WHERE s.code = {quote(code)});")
    print(f"--rollback DELETE FROM core.subject WHERE code LIKE '{domain}.%';")
    print("--rollback DELETE FROM core.study_plan WHERE specialization_id IN "
          f"(SELECT id FROM core.specialization WHERE code = {quote(code)});")
    print(f"--rollback DELETE FROM core.specialization WHERE code = {quote(code)};")
    print("--rollback DELETE FROM core.faculty f WHERE code = 'ACIEE' "
          "AND NOT EXISTS (SELECT 1 FROM core.specialization s WHERE s.faculty_id = f.id);")


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', newline='\n')
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
