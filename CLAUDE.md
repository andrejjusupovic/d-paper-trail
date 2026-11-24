# PaperTrail — Claude Code Instructions

## Role
You are building PaperTrail with me. I know C, Python and AUTOSAR but I'm new to Java/Spring Boot.
Explain Java/Spring concepts briefly when you introduce them, but keep moving.

## Spec
The source of truth is Spec Kit: `specs/` (spec, plan, tasks per feature) and
`.specify/memory/constitution.md`. Never contradict it. If something needs to change, tell me
first and I'll update it. I write all Spec Kit files myself.

`PAPERTRAIL_SPEC.md` is the stakeholder/customer input that I extract requirements from.
Read it for context, never edit it, and never let it override `specs/`. If `specs/` doesn't
cover something yet, ask me instead of taking it from `PAPERTRAIL_SPEC.md`.

How the work is organised (PR order, local vs pipeline): `docs/engineering-plan.md`.

## Rules (never violate)
- Work ONE phase at a time. Do not start the next phase until I say "proceed".
- After each task, show what was created and how to verify it.
- Run `.\mvnw.cmd verify` (Maven Wrapper; CI runs `./mvnw -B verify`) after every phase. Never a
  global `mvn`. All tests must pass and coverage must be ≥ 80%.
- `spring.jpa.hibernate.ddl-auto: validate` — Flyway owns the schema. Never change this.
- DTOs separate from JPA entities. Never return entities from controllers.
- Tests never call real external APIs. Use WireMock for Crossref, FakeKeywordSuggester for AI.
- Never weaken a test to make it pass. Fix the code. If the test is genuinely wrong, explain why before changing it.
- No scope creep: no Kafka, Redis, microservices, auth, frontend, or anything not in `specs/`.
- Every test name or comment must reference the requirement ID it verifies (e.g. FR-001, NFR-002).

## Verifying your work
- Build + tests + coverage: `.\mvnw.cmd verify` → must end with `BUILD SUCCESS`, and JaCoCo
  prints `All coverage checks have been met.` Docker Desktop must be running (Testcontainers).
- Run locally: `docker compose up -d db`, then `.\mvnw.cmd spring-boot:run`.
- Health: GET http://localhost:8080/actuator/health → `{"status":"UP"}`.

Run `.\mvnw.cmd verify` before reporting any task complete, and paste the result lines.
If a test fails, fix the code, not the test.

## Code style
- Package: `com.papertrail`
- Layers: controller → service → repository
- Use `record` for DTOs where possible
- Use constructor injection (no `@Autowired` on fields)
- Use `RestClient` (not `RestTemplate` or `WebClient`) for HTTP calls
- Errors as RFC 7807 Problem Details via `@RestControllerAdvice`
- Logging via SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`)

## Git
- `main` is protected: changes reach it only through a pull request with a green `build` check.
  Never commit to `main` directly.
- One branch per PR, named as in `docs/engineering-plan.md` (e.g. `phase-2-crud`).
- PRs are squash-merged; the PR title becomes the commit message. Phase PRs use the message
  shown in the phase's tasks (originally from `PAPERTRAIL_SPEC.md`).
- Remote is SSH (`git@github.com:andrejjusupovic/d-paper-trail.git`); use `gh` for PRs.
- Every PR fills in `.github/pull_request_template.md`.

## Things Claude gets wrong
<!-- When Claude makes the same mistake twice, add one line here. -->

## When something breaks
1. Show the full error
2. Explain the root cause
3. Then fix it

## AI Log
After each phase, tell me what to add to `AI_LOG.md` — what you generated correctly,
what you got wrong, what I had to correct.
