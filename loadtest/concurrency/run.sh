#!/bin/sh
set -eu
DATABASE_URL=${DATABASE_URL:-postgresql://aciee:aciee@localhost:5432/aciee}
export PGOPTIONS="-c client_min_messages=warning"
CLIENTS=${CLIENTS:-64}
cd "$(dirname "$0")"
psql -X -q -v ON_ERROR_STOP=1 "$DATABASE_URL" -f setup.sql
for step in naive for_update serializable primary_key; do
    script=$(ls [0-9]-$(echo $step | tr _ -).sql)
    echo "=== $script: $CLIENTS clients redeem the same token at once"
    pgbench -n -c "$CLIENTS" -j 8 -t 1 --max-tries=1000 -f "$script" "$DATABASE_URL" 2>&1 \
        | grep -E 'transactions actually processed|failed|retried|retries|latency average'
    psql -X -At "$DATABASE_URL" -c "SELECT 'rows stored for the token: ' || count(*) FROM demo.redemption_$step"
done
psql -X -q "$DATABASE_URL" -c 'DROP SCHEMA demo CASCADE'
