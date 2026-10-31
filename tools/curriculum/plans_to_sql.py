"""Turn UGAL ACIEE study-plan PDFs into a Liquibase changeset.

Usage: python plans_to_sql.py 2023=Pl_Inv_..._2023.pdf 2024=... 2025=... > seed.sql
The last intake gets an open-ended range until a newer plan is added.
Facultative (FA) courses are skipped: students opt in one by one, not as a group.
"""
import contextlib
import re
import sys
import unicodedata

import pymupdf

CODE = re.compile(r'^0502\.(\d)(OB|OP|FA)\d+[A-Z]$')


def normalize(name):
    name = name.replace('ş', 'ș').replace('Ş', 'Ș').replace('ţ', 'ț').replace('Ţ', 'Ț')
    return re.sub(r'\s+', ' ', name).strip()


def match_key(name):
    plain = unicodedata.normalize('NFKD', name.casefold())
    return ''.join(c for c in plain if not unicodedata.combining(c))


def taught_in(block):
    return block[5].strip() not in ('', '-') or block[4].strip() not in ('', '-')


def parse(path):
    rows = []
    for page in pymupdf.open(path):
        for table in page.find_tables().tables:
            for cells in table.extract():
                cells = [(c or '').replace('\n', ' ').strip() for c in cells]
                at = next((i for i, c in enumerate(cells) if CODE.match(c)), None)
                if at is None:
                    continue
                year, kind = CODE.match(cells[at]).groups()
                rows.append({
                    'code': cells[at], 'name': normalize(cells[at - 2]), 'kind': kind, 'year': int(year),
                    'new_package': cells[at - 1] != '',
                    'sem1': taught_in(cells[at + 1:at + 7]), 'sem2': taught_in(cells[at + 7:at + 13]),
                })
    items = []
    previous = None
    slots = {}
    for row in rows:
        if not row['new_package']:
            row['sem1'], row['sem2'], row['slot'] = previous['sem1'], previous['sem2'], previous['slot']
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
        items.append(row | {'semester': semester})
        previous = row
    names = [item['name'] for item in items]
    for item in items:
        if names.count(item['name']) > 1:
            item['name'] = f"{item['name']} (anul {item['year']})"
    return items


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def main(args):
    with contextlib.redirect_stdout(sys.stderr):
        plans = sorted((int(a.split('=', 1)[0]), parse(a.split('=', 1)[1])) for a in args)
    subjects = {}
    for intake, items in reversed(plans):
        for item in items:
            key = match_key(item['name'])
            if key not in subjects:
                taken = item['code'] in (code for code, _ in subjects.values())
                subjects[key] = (f"{item['code']}-{intake}" if taken else item['code'], item['name'])

    print('--liquibase formatted sql\n')
    print('--changeset aciee:seed-calculatoare')
    print("INSERT INTO core.faculty (code, name_ro)\n"
          "VALUES ('ACIEE', 'Facultatea de Automatică, Calculatoare, Inginerie Electrică și Electronică');")
    print("INSERT INTO core.specialization (faculty_id, code, name_ro, degree, duration_years)\n"
          "SELECT id, 'C', 'Calculatoare', 'BSc', 4 FROM core.faculty WHERE code = 'ACIEE';")
    ranges = []
    for i, (intake, _) in enumerate(plans):
        upper = str(plans[i + 1][0]) if i + 1 < len(plans) else ''
        ranges.append(f"({intake}, '[{intake},{upper})')")
    print("INSERT INTO core.study_plan (specialization_id, intake)\n"
          "SELECT s.id, v.intake::int4range FROM core.specialization s\n"
          f"CROSS JOIN (VALUES {', '.join(ranges)}) v(start, intake)\nWHERE s.code = 'C';")
    print('INSERT INTO core.subject (code, name_ro) VALUES\n'
          + ',\n'.join(f'    ({quote(code)}, {quote(name)})' for code, name in sorted(subjects.values()))
          + ';')
    values = [f"    ({intake}, {quote(subjects[match_key(item['name'])][0])}, {item['year']}, {item['semester']}, {item['slot'] or 'NULL'})"
              for intake, items in plans for item in items]
    print('INSERT INTO core.curriculum_item (plan_id, subject_id, study_year, semester_no, elective_slot)\n'
          'SELECT p.id, sub.id, v.study_year, v.semester_no, v.elective_slot\nFROM (VALUES\n'
          + ',\n'.join(values)
          + '\n) v(intake, subject_code, study_year, semester_no, elective_slot)\n'
          "JOIN core.specialization s ON s.code = 'C'\n"
          'JOIN core.study_plan p ON p.specialization_id = s.id AND lower(p.intake) = v.intake\n'
          'JOIN core.subject sub ON sub.code = v.subject_code;')
    print("--rollback DELETE FROM core.curriculum_item WHERE plan_id IN (SELECT p.id FROM core.study_plan p "
          "JOIN core.specialization s ON s.id = p.specialization_id WHERE s.code = 'C');")
    print("--rollback DELETE FROM core.subject WHERE code LIKE '0502.%';")
    print("--rollback DELETE FROM core.study_plan WHERE specialization_id IN (SELECT id FROM core.specialization WHERE code = 'C');")
    print("--rollback DELETE FROM core.specialization WHERE code = 'C'; DELETE FROM core.faculty WHERE code = 'ACIEE';")


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', newline='\n')
    main(sys.argv[1:])
