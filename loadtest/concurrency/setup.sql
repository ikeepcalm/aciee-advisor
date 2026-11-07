DROP SCHEMA IF EXISTS demo CASCADE;
CREATE SCHEMA demo;
CREATE TABLE demo.offering (id integer PRIMARY KEY);
INSERT INTO demo.offering VALUES (1);
CREATE TABLE demo.redemption_naive (key_hash bytea NOT NULL);
CREATE TABLE demo.redemption_for_update (key_hash bytea NOT NULL);
CREATE TABLE demo.redemption_serializable (key_hash bytea NOT NULL);
CREATE TABLE demo.redemption_primary_key (key_hash bytea PRIMARY KEY);
