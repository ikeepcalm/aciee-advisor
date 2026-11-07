INSERT INTO demo.redemption_primary_key (key_hash) VALUES ('\x01'::bytea) ON CONFLICT (key_hash) DO NOTHING;
