BEGIN;
SELECT id FROM demo.offering WHERE id = 1 FOR UPDATE;
SELECT count(*) AS taken FROM demo.redemption_for_update WHERE key_hash = '\x01'::bytea \gset
\if :taken = 0
\sleep 5 ms
INSERT INTO demo.redemption_for_update (key_hash) VALUES ('\x01'::bytea);
\endif
COMMIT;
