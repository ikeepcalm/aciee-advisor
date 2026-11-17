# ACIEE Advisor — Deployment

Each environment is one Docker Compose project built from this repository with `infra/docker-compose.prod.yml`, on any host that runs Docker Compose behind an HTTPS reverse proxy:

| Service | Image | Role |
|---|---|---|
| `db` | `infra/postgres` (postgres:18 + `init-roles.sh`) | Database; creates the three login roles on first start |
| `backend` | `backend/Dockerfile` | Spring Boot; runs Liquibase as `aciee`, serves as `aciee_ident` |
| `web` | `frontend/Dockerfile` | nginx: the SPA, and `/api` + `/anon` proxied to `backend:8080` |
| `backup` | postgres:18 | `pg_dump -Fc` once a day into the `backups` volume, 14 days kept |

Only `web` is reachable from outside: it publishes `127.0.0.1:${WEB_PORT}` (default 8080), and the host's reverse proxy terminates HTTPS and forwards to it. The proxy must set `X-Forwarded-Proto` (the session cookie is `Secure`, and Microsoft sign-in builds its redirect URL from it) and allow a read timeout of about two minutes for the signing-key batch. The browser sees one origin, so there is no CORS, and the session cookie stays `Secure` + `SameSite=Lax`.

## Environments

| Environment | Domain | `LIQUIBASE_CONTEXTS` |
|---|---|---|
| app | `https://acieeadvisor.com` (and `www.`) | `prod` (no fixtures) |
| demo | `https://demo.acieeadvisor.com` | `demo` (realistic fictional data, demo accounts), plus `SPRING_PROFILES_ACTIVE=show` |

The demo accounts are `demo.admin`, `demo.moderator`, `demo.leader` and `demo.student` `@student.ugal.ro`, all with the password `demo-parola`. They exist only where the `demo` or `fixtures` context ran.

Pitch show (demo only): open `/show?room=<secret>` on the laptop (add `&lang=ro` for Romanian) and `/show/remote?room=<same secret>` on the phone. The laptop warms up every beat, then the phone's Next plays them one by one; →, Space and PageDown also advance, Escape twice exits. The show serves its own bundled data, so it needs the backend only for the phone relay (`/api/show/**`, the `show` profile), which the host nginx must proxy without buffering. How it works and how to edit beats: [SHOW.md](SHOW.md).

## Setting up an environment

1. Point your deploy tool at `infra/docker-compose.prod.yml` with `infra/` as the working directory, so the `../backend` and `../frontend` build contexts resolve.
2. Environment variables (generate each password with `openssl rand -base64 32`):

   | Variable | Value |
   |---|---|
   | `POSTGRES_PASSWORD` | migrator / superuser `aciee` |
   | `ACIEE_IDENT_PASSWORD`, `ACIEE_ANON_PASSWORD`, `ACIEE_REPORT_PASSWORD` | app roles |
   | `LIQUIBASE_CONTEXTS` | `prod` or `demo` |
   | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` | SMTP provider, port 587 with STARTTLS (many VPS providers block outbound 25 and 465) |
   | `MAIL_FROM` | `ACIEE Advisor <no-reply@acieeadvisor.com>` |
   | `FEEDBACK_TO` | inbox for the "Report a problem" form; empty disables sending |
   | `PUBLIC_URL` | `https://acieeadvisor.com` (`https://demo.acieeadvisor.com` on demo); absolute links in the WhatsApp/Telegram link-preview cards |
   | `WEB_PORT` | host port `web` publishes on `127.0.0.1` for the reverse proxy |
   | `SPRING_PROFILES_ACTIVE`, `ENTRA_CLIENT_ID`, `ENTRA_CLIENT_SECRET` | `microsoft` and the app registration below, to turn on Microsoft sign-in; leave empty to hide it |

   The role passwords are read only when the `pgdata` volume is created. Changing them later needs `ALTER ROLE … PASSWORD` in the database as well.
3. Deploy. The first start runs every Liquibase changeset. With `demo` this takes about a minute.

## Professor profiles

The CV summaries and portraits of professors are personal data copied from the university's site, so they are not in this repository. `core.professor_profile` starts empty; load the rows into the database once (`psql -f`), and copy the portraits into the `professor-photos` volume, which `web` serves at `/professors/`. A profile matches a professor by `core.name_key(full_name)`.

## Microsoft sign-in

The button "Continuă cu Microsoft" appears only when the app runs with the `microsoft` profile (ADR-030).

1. Register the app in Microsoft Entra (portal.azure.com → App registrations → New registration), signed in with a `@student.ugal.ro` account if the tenant allows students to register apps, otherwise ask the university's IT administrator.
   - If students cannot register apps in the UGAL tenant, register it in another directory as **multitenant** (`signInAudience: AzureADMultipleOrgs`); the portal refuses that change until the manifest's `api.requestedAccessTokenVersion` is `2`. Sign-in still goes through UGAL's tenant endpoint and checks `tid`, so other organisations are refused.
   - Supported account types: **this organizational directory only** (UGAL, tenant `445e5699-60fa-4f59-8e7f-778197a4d740`).
   - Redirect URI, platform **Web**: `https://acieeadvisor.com/api/auth/oauth2/code/microsoft` (add `https://demo.acieeadvisor.com/…` and `http://localhost:8080/…` for the other environments).
2. Certificates & secrets → New client secret. Copy the value (shown once) into `ENTRA_CLIENT_SECRET`, the Application (client) ID into `ENTRA_CLIENT_ID`.
3. API permissions: the app asks only for `openid profile email`; the default `User.Read` can be removed. These are permissions users can normally consent to themselves, but if the UGAL tenant blocks user consent, an administrator has to grant it once (the first sign-in then shows "Need admin approval").
4. Set `SPRING_PROFILES_ACTIVE=microsoft` and redeploy. `GET /api/auth/providers` should answer `{"microsoft":true}`.

At startup the app fetches the tenant's OpenID configuration from `login.microsoftonline.com`; without network access it fails to start with the profile on. `ENTRA_TENANT_ID` overrides the tenant for testing with another directory.

## DNS

| Record | Value |
|---|---|
| `A acieeadvisor.com`, `A www`, `A demo` | the VPS IPv4 (plus `AAAA` for IPv6) |
| `TXT acieeadvisor.com` (SPF) | from the mail provider, e.g. `v=spf1 include:<provider> -all` |
| DKIM `TXT`/`CNAME` | from the mail provider |
| `TXT _dmarc` | `v=DMARC1; p=quarantine; rua=mailto:<admin mailbox>` |

Before launch, send a verification code to a real `@student.ugal.ro` inbox and check that it lands outside spam (ROADMAP "Checked so far").

## First admin

Sign up on the site, verify the code, then grant the role from a shell in the `db` container:

```
psql -U aciee -d aciee -c "INSERT INTO core.user_role (user_id, role_code) SELECT id, 'admin' FROM core.user_account WHERE email = '<you>@student.ugal.ro'"
```

Sign out and in again so the session picks up the role.

## Backups

`backup` writes `/backups/aciee-YYYY-MM-DD.dump` into the `backups` volume. Copying off the server is still manual: sync the volume to object storage or another machine from a cron job on the host. To restore into an empty database:

```
pg_restore -U aciee -d aciee --clean --if-exists /backups/aciee-YYYY-MM-DD.dump
```

Test a restore into the demo resource before launch (ARCHITECTURE §8).

## Signing keys

The private key of each offering is a PKCS#8 file named by its fingerprint in the `token-keys` volume (`/keys`), and is never in the database or the dumps. Before a semester's ratings open, an admin calls `POST /api/admin/semesters/{id}/token-keys` once; it creates a key for every offering of that semester that has none, so rerunning it after a failure only fills the gaps. Then add the base64 SHA-256 of the new `/anon/keys.json` to `frontend/src/crypto/pinned-keys.json`, replacing that environment's old entry, and redeploy the frontend: until then students on that environment cannot get tokens. A key takes about a second to create, so a large semester can outlast the proxy timeout; the batch keeps running on the server, and calling it again afterwards returns 0 once it is complete. Copy the volume off the server together with the dumps: a lost private key stops new issuance for its offering, while existing submissions stay valid because redemption only needs the public key.
