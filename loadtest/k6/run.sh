#!/bin/sh
set -eu
cd "$(dirname "$0")"
K6_IMAGE=${K6_IMAGE:-grafana/k6:2.3.0}
BASE_URL=${BASE_URL:-http://host.docker.internal:8081}
k6() {
    docker run --rm -i -v "$PWD:/scripts" -e BASE_URL="$BASE_URL" "$@"
}
case ${1:-browse} in
    redeem)
        ./mint-token.sh > token.json
        k6 "$K6_IMAGE" run --quiet /scripts/redeem-same-token.js
        rm token.json ;;
    browse)
        k6 -e PEAK_RATE="${PEAK_RATE:-300}" "$K6_IMAGE" run --quiet --summary-export "/scripts/summary.json" /scripts/browse.js ;;
esac
