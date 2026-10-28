# ACIEE Advisor

Course survival guides and anonymous, structured teacher evaluations for students of the Faculty of Automatic Control, Computers, Electrical and Electronics Engineering (ACIEE), "Dunărea de Jos" University of Galați.

- Students sign in with a `@student.ugal.ro` account.
- Ratings use 14 axes in three pillars and are published only as aggregates, once at least 5 ratings exist.
- Ratings are submitted with RFC 9474 blind-signature tokens, so the database holds no link between a rating and an account.

## Structure

```
backend/     Java 25, Spring Boot 4, Liquibase changelogs and fixtures
frontend/    Vue 3 + TypeScript SPA (ro-RO)
infra/       Docker Compose, Caddy, backups
loadtest/    k6 scenarios
docs/        specification, architecture, roadmap, decisions, AI log
```

## Documentation

- [Specification](docs/SPECIFICATION.md): problem, roles, evaluation model, views
- [Views](docs/VIEWS.md): route, roles, wireframe, data and actions for every view
- [Architecture](docs/ARCHITECTURE.md): stack, authentication, anonymity protocol, access control
- [Database](docs/DATABASE.md): tables, normal forms, constraints, indexes, connections
- [Roadmap](docs/ROADMAP.md): course stages, scope, timeline
- [Decisions](docs/DECISIONS.md): architectural decision records
- [AI log](docs/AI_LOG.md): AI usage and corrected AI errors
