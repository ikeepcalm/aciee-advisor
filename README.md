# ACIEE Advisor

Course survival guides and anonymous, structured teacher evaluations for students of the Faculty of Automatic Control, Computers, Electrical and Electronics Engineering (ACIEE), "Dunărea de Jos" University of Galați.

- Students sign in with a `@student.ugal.ro` account.
- Ratings use 14 axes in three pillars and are published only as aggregates, once at least 5 ratings exist.
- Ratings are submitted with RFC 9474 blind-signature tokens, so the database holds no link between a rating and an account.

## Structure

```
backend/     Java 25, Spring Boot 4, Liquibase changelogs and fixtures
frontend/    Vue 3 + TypeScript SPA (ro-RO)
infra/       Docker Compose (local and production), Postgres role script
loadtest/    k6 scenarios
docs/        specification, architecture, roadmap, decisions, AI log
```

## Local setup

```
docker compose -f infra/docker-compose.yml up -d
```

The Postgres image creates the `aciee_ident`, `aciee_anon` and `aciee_report` login roles (`infra/postgres/init-roles.sh`) only when its volume is first created. A volume created before those roles existed needs `docker compose -f infra/docker-compose.yml down -v` once, or the script run by hand. The app reads `DB_IDENT_USER` / `DB_IDENT_PASSWORD` for its pool and `DB_MIGRATOR_USER` / `DB_MIGRATOR_PASSWORD` for Liquibase.

Synthetic data: start the backend with `LIQUIBASE_CONTEXTS=fixtures` on a fresh volume (about 2% of the target volumes, demo accounts `demo.admin` / `demo.leader` / `demo.student` `@student.ugal.ro`, password `demo-parola`). `fixtures-large` loads the full volumes, and `demo` loads a smaller realistic data set with fictional professors for demo.acieeadvisor.com. Without the variable the context is `prod` and no fixtures run. The session cookie is `Secure` by default; set `COOKIE_SECURE=false` only when running locally over plain `http://localhost` (Safari drops Secure cookies there), never in production.

Deployment: [docs/DEPLOY.md](docs/DEPLOY.md) (Docker Compose behind an HTTPS reverse proxy).

## Documentation

- [Specification](docs/SPECIFICATION.md): problem, roles, evaluation model, views
- [Views](docs/VIEWS.md): route, roles, wireframe, data and actions for every view
- [Architecture](docs/ARCHITECTURE.md): stack, authentication, anonymity protocol, access control
- [Database](docs/DATABASE.md): tables, normal forms, constraints, indexes, connections
- [Roadmap](docs/ROADMAP.md): course stages, scope, timeline
- [Decisions](docs/DECISIONS.md): architectural decision records
- [AI log](docs/AI_LOG.md): AI usage and corrected AI errors

## License

[GNU AGPL-3.0](LICENSE). You may run, study, change and share this code; if you run a modified version for others over a network, you must offer them its source under the same license. Professor CV summaries and portraits are not part of this repository (see [DEPLOY.md](docs/DEPLOY.md#professor-profiles)).
