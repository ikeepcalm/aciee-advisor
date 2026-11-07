BEGIN;
SELECT count(*) AS taken FROM demo.redemption_naive WHERE key_hash = '\x01'::bytea \gset
\if :taken = 0
\sleep 5 ms
INSERT INTO demo.redemption_naive (key_hash) VALUES ('\x01'::bytea);
\endif
COMMIT;
