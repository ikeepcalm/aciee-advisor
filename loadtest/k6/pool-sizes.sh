#!/bin/sh
set -eu
cd "$(dirname "$0")"
JAR=../../backend/build/libs/aciee-advisor-0.0.1-SNAPSHOT.jar
LOGS=$(mktemp -d)
echo "pool,course_p95_ms,professor_p95_ms,catalogue_p95_ms,failed_rate,requests"
for size in ${POOL_SIZES:-2 5 10 20 40}; do
    SERVER_PORT=8081 DB_URL=${DB_URL:-jdbc:postgresql://localhost:5432/aciee_large} TOKEN_KEYS_DIR="$LOGS/keys" \
        SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=$size java -jar "$JAR" > "$LOGS/pool-$size.log" 2>&1 &
    app=$!
    until curl -sf http://localhost:8081/api/catalog/lookups > /dev/null; do sleep 1; done
    ./run.sh browse > "$LOGS/k6-$size.txt" 2>&1 || true
    python3 -I - "$size" summary.json <<'PY'
import json, sys
m = json.load(open(sys.argv[2]))["metrics"]
p95 = lambda page: round(m[f"http_req_duration{{page:{page}}}"]["p(95)"], 1) if f"http_req_duration{{page:{page}}}" in m else ""
catalogue = [v for k, v in m.items() if k.startswith("http_req_duration") and "catalogue" in k]
print(f'{sys.argv[1]},{p95("course")},{p95("professor")},{round(catalogue[0]["p(95)"], 1) if catalogue else ""},'
      f'{round(m["http_req_failed"]["value"], 4)},{m["http_reqs"]["count"]}')
PY
    kill $app
    wait $app || true
done
rm -f summary.json
