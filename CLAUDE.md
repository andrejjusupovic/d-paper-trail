# PaperTrail — Claude Code Instructions

## Role
You are building PaperTrail with me. I know C, Python and AUTOSAR but I'm new to Java/Spring Boot.
Explain Java/Spring concepts briefly when you introduce them, but keep moving.

## Spec
The full specification is in `PAPERTRAIL_SPEC.md`. That is the source of truth.
Never contradict it. If something needs to change, tell me first and I'll update the spec.

## Rules (never violate)
- Work ONE phase at a time. Do not start the next phase until I say "proceed".
- After each task, show what was created and how to verify it.
- Run `mvn verify` after every phase. All tests must pass and coverage must be ≥ 80%.
- `spring.jpa.hibernate.ddl-auto: validate` — Flyway owns the schema. Never change this.
- DTOs separate from JPA entities. Never return entities from controllers.
- Tests never call real external APIs. Use WireMock for Crossref, FakeKeywordSuggester for AI.
- Never weaken a test to make it pass. Fix the code. If the test is genuinely wrong, explain why before changing it.
- No scope creep: no Kafka, Redis, microservices, auth, frontend, or anything not in the spec.
- Every test name or comment must reference the requirement ID it verifies (e.g. FR-001, NFR-002).

## Code style
- Package: `com.papertrail`
- Layers: controller → service → repository
- Use `record` for DTOs where possible
- Use constructor injection (no `@Autowired` on fields)
- Use `RestClient` (not `RestTemplate` or `WebClient`) for HTTP calls
- Errors as RFC 7807 Problem Details via `@RestControllerAdvice`
- Logging via SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`)

## Git
- Commit after each phase with the message shown in the spec
- Branch: `main` for now (I'll add branch protection after CI is set up)

## When something breaks
1. Show the full error
2. Explain the root cause
3. Then fix it

## AI Log
After each phase, tell me what to add to `AI_LOG.md` — what you generated correctly,
what you got wrong, what I had to correct.
