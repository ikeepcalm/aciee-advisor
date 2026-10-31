#!/bin/sh
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v ident="$ACIEE_IDENT_PASSWORD" -v anon="$ACIEE_ANON_PASSWORD" -v report="$ACIEE_REPORT_PASSWORD" <<'SQL'
CREATE ROLE aciee_ident LOGIN PASSWORD :'ident';
CREATE ROLE aciee_anon LOGIN PASSWORD :'anon';
CREATE ROLE aciee_report LOGIN PASSWORD :'report';
SQL
