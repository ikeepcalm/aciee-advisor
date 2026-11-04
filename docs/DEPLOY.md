# ACIEE Advisor — Deployment

One VPS running the deploy tool. Each environment is a deploy-tool **Docker Compose** resource built from this repository with `infra/docker-compose.prod.yml`:

| Service | Image | Role |
|---|---|---|
| `db` | `infra/postgres` (postgres:18 + `init-roles.sh`) | Database; creates the three login roles on first start |
| `backend` | `backend/Dockerfile` | Spring Boot; runs Liquibase as `aciee`, serves as `aciee_ident` |
| `web` | `frontend/Dockerfile` | nginx: the SPA, and `/api` + `/anon` proxied to `backend:8080` |
| `backup` | postgres:18 | `pg_dump -Fc` once a day into the `backups` volume, 14 days kept |

Only `web` gets a domain. the deploy tool's proxy terminates HTTPS and forwards to `web:80`. The browser sees one origin, so there is no CORS, and the session cookie stays `Secure` + `SameSite=Lax`.

## Environments

| the deploy tool resource | Domain | `LIQUIBASE_CONTEXTS` |
|---|---|---|
| app | `https://acieeadvisor.ro` (and `www.` redirecting to it) | `prod` (no fixtures) |
| demo | `https://demo.acieeadvisor.ro` | `fixtures` (synthetic data, demo accounts) |

The demo accounts are `demo.admin`, `demo.moderator`, `demo.leader` and `demo.student` `@student.ugal.ro`, all with the password `demo-parola`. They exist only where the `fixtures` context ran.

## Setting up a resource

1. the deploy tool → Project → New resource → Public/Private repository → `ikeepcalm/aciee-advisor`, branch `main`.
2. Build pack **Docker Compose**, base directory `/`, compose file `/infra/docker-compose.prod.yml`.
3. Environment variables (generate each password with `openssl rand -base64 32`):

   | Variable | Value |
   |---|---|
   | `POSTGRES_PASSWORD` | migrator / superuser `aciee` |
   | `ACIEE_IDENT_PASSWORD`, `ACIEE_ANON_PASSWORD`, `ACIEE_REPORT_PASSWORD` | app roles |
   | `LIQUIBASE_CONTEXTS` | `prod` or `fixtures` |
   | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` | SMTP provider (port 587, STARTTLS) |
   | `MAIL_FROM` | `ACIEE Advisor <no-reply@acieeadvisor.ro>` |

   The role passwords are read only when the `pgdata` volume is created. Changing them later needs `ALTER ROLE … PASSWORD` in the database as well.
4. Under the `web` service, set the domain (`https://acieeadvisor.ro`). Leave the other services without a domain.
5. Deploy. The first start runs every Liquibase changeset. With `fixtures` this takes about a minute.

## DNS

| Record | Value |
|---|---|
| `A acieeadvisor.ro`, `A www`, `A demo` | the VPS IPv4 (plus `AAAA` for IPv6) |
| `TXT acieeadvisor.ro` (SPF) | from the mail provider, e.g. `v=spf1 include:<provider> -all` |
| DKIM `TXT`/`CNAME` | from the mail provider |
| `TXT _dmarc` | `v=DMARC1; p=quarantine; rua=mailto:<admin mailbox>` |

Before launch, send a verification code to a real `@student.ugal.ro` inbox and check that it lands outside spam (ROADMAP "Checked so far").

## First admin

Sign up on the site, verify the code, then grant the role from the deploy tool's terminal of `db`:

```
psql -U aciee -d aciee -c "INSERT INTO core.user_role (user_id, role_code) SELECT id, 'admin' FROM core.user_account WHERE email = '<you>@student.ugal.ro'"
```

Sign out and in again so the session picks up the role.

## Backups

`backup` writes `/backups/aciee-YYYY-MM-DD.dump` into the `backups` volume. Copying off the server is still manual: either point object storage backup at the volume, or `rsync` it to a a storage box from a cron job on the host. To restore into an empty database:

```
pg_restore -U aciee -d aciee --clean --if-exists /backups/aciee-YYYY-MM-DD.dump
```

Test a restore into the demo resource before launch (ARCHITECTURE §8).

## Signing keys

The private key of each offering is a PKCS#8 file named by its fingerprint in the `token-keys` volume (`/keys`), and is never in the database or the dumps. Before a semester's ratings open, an admin calls `POST /api/admin/semesters/{id}/token-keys` once; it creates a key for every offering of that semester that has none, so rerunning it after a failure only fills the gaps. Then publish the new `/anon/keys.json` (ARCHITECTURE §3). Copy the volume off the server together with the dumps: a lost private key stops new issuance for its offering, while existing submissions stay valid because redemption only needs the public key.
