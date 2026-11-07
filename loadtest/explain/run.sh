#!/bin/sh
set -eu
DATABASE_URL=${DATABASE_URL:-postgresql://aciee:aciee@localhost:5432/aciee_large}
export PGOPTIONS="-c client_min_messages=warning -c jit=off"
OUT=$(cd "$(dirname "$0")/../../docs/explain-plans" && pwd)
cd "$(dirname "$0")"
for script in ${@:-[0-9]*.sql}; do
    echo "$script"
    psql -X -q -v ON_ERROR_STOP=1 "$DATABASE_URL" -f "$script" > "$OUT/${script%.sql}.txt"
done
