#!/bin/sh
set -eu
DATABASE_URL=${DATABASE_URL:-postgresql://aciee:aciee@localhost:5432/aciee_large}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK/key.pem" 2>/dev/null
openssl pkey -in "$WORK/key.pem" -pubout -outform DER -out "$WORK/public.der"
FINGERPRINT=$(openssl dgst -sha256 -hex "$WORK/public.der" | awk '{print $NF}')
OFFERING=$(psql -X -At "$DATABASE_URL" -c "SELECT o.id FROM core.course_offering o WHERE NOT EXISTS (SELECT 1 FROM anon.offering_key k WHERE k.offering_id = o.id AND k.active) ORDER BY o.id LIMIT 1")
psql -X -q "$DATABASE_URL" -c "INSERT INTO anon.offering_key (fingerprint, offering_id, public_key) VALUES ('\\x$FINGERPRINT', $OFFERING, '\\x$(xxd -p "$WORK/public.der" | tr -d '\n')')"
head -c 32 /dev/urandom > "$WORK/msg.bin"
openssl dgst -sha384 -sign "$WORK/key.pem" -sigopt rsa_padding_mode:pss -sigopt rsa_pss_saltlen:0 -sigopt rsa_mgf1_md:sha384 \
    -out "$WORK/sig.bin" "$WORK/msg.bin"
printf '{"offeringId":%s,"fingerprint":"%s","msg":"%s","sig":"%s","examDifficulty":7,"ratings":[]}\n' "$OFFERING" \
    "$(echo "$FINGERPRINT" | xxd -r -p | base64)" "$(base64 < "$WORK/msg.bin")" "$(base64 < "$WORK/sig.bin" | tr -d '\n')"
