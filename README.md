# PaperTrail

A personal learning project, not a production product. PaperTrail is a small REST API
for managing a catalog of research publications: add, view, update and delete papers,
search them, import metadata by DOI, and get AI-suggested keywords.

## Why this project exists

I'm an embedded/automotive software engineer and team lead (C, AUTOSAR, ASPICE).
I built PaperTrail to answer two questions:

1. Can an engineer with solid fundamentals deliver in an unfamiliar stack
   (Java, Spring Boot, PostgreSQL) by steering an AI coding agent?
2. Do the practices I use in automotive work (spec first, requirement traceability,
   reviews, CI quality gates) carry over to AI-assisted development?

## How it's built

- **The AI writes the code; I steer it.** Claude Code generates the implementation and tests.
  I write the requirements, make the design decisions and review every change.
  I don't accept code I don't understand.
- **Spec first.** Every requirement has an ID (FR-001, NFR-001, …). Tasks and tests
  reference those IDs, so every test traces back to a requirement, the same idea as
  ASPICE traceability.
- **Quality gates instead of trust.** A change is accepted only when `mvn verify` passes,
  which includes integration tests against a real PostgreSQL (Testcontainers) and at least
  80% line coverage (JaCoCo).
- **AI log.** [AI_LOG.md](AI_LOG.md) records what the AI got right, what it got wrong,
  and what I had to correct.

> I'm not a Java or web developer by background. This repo shows how I specify,
> direct and verify AI-generated work. It doesn't show years of Spring Boot experience.

## Status

- [x] Project setup: Docker Compose, PostgreSQL 16, Flyway, health check, Swagger UI, coverage gate
- [ ] CRUD API with validation and error handling
- [ ] Full-text search with pagination
- [ ] Import by DOI (Crossref API)
- [ ] AI keyword suggestions
