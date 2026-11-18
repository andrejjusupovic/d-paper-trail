# PaperTrail — Research Publication Catalog

## Purpose

This document is the complete specification, plan and implementation guide for PaperTrail, a Java/Spring Boot REST API for managing research publications. It is meant to be used as a single prompt input for Claude Code (Sonnet/Opus) to build the project from scratch using Spec-Driven Development.

The developer (Andrej) is an experienced embedded C/AUTOSAR engineer learning Java/Spring Boot. The AI must explain Java/Spring concepts clearly when introducing them, but not slow down the build.

---

## Project Overview

PaperTrail is a backend service where users can:
- Add research papers manually (title, authors, DOI, abstract, keywords, journal, year)
- Import a paper by DOI from the Crossref API
- Search papers by text, author, keyword, journal and year range (paginated, sorted)
- Request AI-suggested keywords from title and abstract

The data lives in PostgreSQL. There is no frontend and no login system.

---

## Constitution (project rules — never violate these)

1. **Spec first:** no code without an approved spec and plan. If the spec needs to change, update it before changing code.
2. **Test-first:** every feature has automated tests. Minimum 80% line coverage enforced by JaCoCo in `mvn verify`. Never weaken a test to make it pass — fix the code instead.
3. **Simplicity:** single deployable service, no premature abstractions, no unused features. No Kafka, no Redis, no microservices, no login system.
4. **API quality:** RESTful JSON API, consistent error format (RFC 7807 Problem Details), input validation on every endpoint, OpenAPI docs generated from code (springdoc-openapi).
5. **Security:** no secrets in the repository. Configuration via environment variables. `.gitignore` must cover `.env`, `target/`, `.idea/`.
6. **AI-assisted development:** AI generates code and tests, but every change must pass CI. The developer reviews all generated code. AI features (keyword suggestions) must be behind an interface so tests use a deterministic fake, never a real LLM.
7. **Observability:** structured logging (SLF4J/Logback) and health check endpoint (`/actuator/health`).
8. **Traceability:** every requirement has an ID (FR-001, FR-002…). Every task references its requirement IDs. Every test name or comment references the requirement it verifies. This gives ASPICE-style traceability from requirement → task → test.

---

## Requirements Specification

### Functional Requirements

**FR-001: Create publication manually**
User submits: title (required), abstract (max 10,000 chars), authors (at least one; each has name, optional ORCID), DOI (optional but unique if provided), publication year (required, cannot be in the future), journal name (optional), keywords (optional list of strings).
Returns: the saved publication with a generated ID.

**FR-002: Import publication by DOI**
User submits a DOI string. The system:
- Normalizes the DOI: lowercase, strip "https://doi.org/" prefix if present
- Checks if the DOI already exists in the database → if yes, return the existing record (200), do not create a duplicate
- Fetches metadata from Crossref API (`https://api.crossref.org/works/{doi}`)
- Maps the response: title, authors, DOI, year, journal. Abstract may be missing (Crossref often omits it) or contain JATS XML tags that must be stripped.
- Saves with source = CROSSREF and import timestamp
- If Crossref is unavailable or returns 404: return a clear error; the rest of the system keeps working

**FR-003: View publication**
GET by ID. Returns the full publication including authors (in order) and keywords.

**FR-004: Update publication**
PUT by ID. Same validation as FR-001. Returns the updated record.

**FR-005: Delete publication**
DELETE by ID. Returns 204 No Content.

**FR-006: Search publications**
Search by: free text in title and abstract (full-text), author name (case-insensitive partial), keyword (exact, case-insensitive), journal (case-insensitive partial), year range (yearFrom, yearTo).
All filters are optional and combinable.
Pagination: `page` (0-based), `size` (default 20, max 100). Sorting: default newest first (`publicationYear` desc, then `id` desc).
Returns: paginated response with content, totalElements, totalPages, page number.
Performance: must respond in under 500ms for 10,000 publications.

**FR-007: AI keyword suggestions**
POST to `/api/publications/{id}/keyword-suggestions`.
Takes the publication's title and abstract, sends to an LLM, returns 3–8 suggested keywords as a JSON array.
The user decides whether to save them (separate PUT call to update keywords).
If the AI service is unavailable: return 503 with a clear message; rest of system unaffected.

### Non-Functional Requirements

**NFR-001:** DOI is stored normalized (lowercase, no URL prefix), with a unique constraint in the database.
**NFR-002:** Author order is preserved (publication_author join table with `author_order` column).
**NFR-003:** Each publication records its source (MANUAL, CROSSREF) and creation timestamp.
**NFR-004:** All errors returned as RFC 7807 Problem Details with consistent structure.
**NFR-005:** Full-text search uses PostgreSQL tsvector + GIN index on title and abstract.

---

## Technical Plan

### Tech Stack
- Java 21, latest stable Spring Boot 3.x (check start.spring.io), Maven
- Spring modules: Web, Validation, Data JPA, Actuator, Flyway
- PostgreSQL 16 (via Docker)
- springdoc-openapi for Swagger UI
- JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL), WireMock
- JaCoCo for coverage (80% minimum enforced in Maven)
- Multi-stage Dockerfile + docker-compose.yml (app + postgres)

### Architecture
Layered: Controller → Service → Repository
- **Controllers:** handle HTTP, validate input, return DTOs. Annotated with `@RestController`.
- **Services:** business logic, transactional boundaries. Annotated with `@Service`, `@Transactional`.
- **Repositories:** data access via Spring Data JPA. Custom queries for search.
- **DTOs:** separate from JPA entities. Used for request/response. Never expose entities directly.
- **Global exception handler:** `@RestControllerAdvice` returning Problem Details for all errors.

### Data Model

```
publication
-----------
id                BIGSERIAL PRIMARY KEY
title             VARCHAR(500) NOT NULL
abstract_text     VARCHAR(10000)
doi               VARCHAR(255) UNIQUE  -- normalized: lowercase, no URL prefix
publication_year  SMALLINT NOT NULL
journal_name      VARCHAR(500)
source            VARCHAR(20) NOT NULL DEFAULT 'MANUAL'  -- MANUAL, CROSSREF
created_at        TIMESTAMP NOT NULL DEFAULT NOW()
updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
search_vector     TSVECTOR  -- generated from title + abstract, updated by trigger

author
------
id                BIGSERIAL PRIMARY KEY
name              VARCHAR(300) NOT NULL
orcid             VARCHAR(50)

publication_author
------------------
publication_id    BIGINT REFERENCES publication(id) ON DELETE CASCADE
author_id         BIGINT REFERENCES author(id) ON DELETE CASCADE
author_order      SMALLINT NOT NULL
PRIMARY KEY (publication_id, author_id)

keyword
-------
id                BIGSERIAL PRIMARY KEY
value             VARCHAR(200) NOT NULL UNIQUE  -- stored lowercase

publication_keyword
-------------------
publication_id    BIGINT REFERENCES publication(id) ON DELETE CASCADE
keyword_id        BIGINT REFERENCES keyword(id) ON DELETE CASCADE
PRIMARY KEY (publication_id, keyword_id)
```

**Indexes:**
- GIN index on `search_vector` (full-text search)
- B-tree on `publication_year`
- Unique on `doi` (already in column definition)
- B-tree on `author.name` (for author search)
- Unique on `keyword.value`

**Trigger:** auto-update `search_vector` on INSERT/UPDATE of title or abstract_text:
```sql
search_vector = setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
                setweight(to_tsvector('english', coalesce(abstract_text, '')), 'B')
```

### External Clients

**CrossrefClient** (interface + implementation):
- URL: `https://api.crossref.org/works/{doi}`
- Add header: `User-Agent: PaperTrail/1.0 (mailto:jusupovic@protonmail.ch)`
- Timeout: 5 seconds connect, 10 seconds read
- Handle: 200 (parse), 404 (publication not found), 5xx (service unavailable)
- Abstract handling: if `abstract` field exists, strip JATS XML tags with regex (`<[^>]+>`)
- Use Spring's RestClient

**KeywordSuggester** (interface):
```java
public interface KeywordSuggester {
    List<String> suggest(String title, String abstractText);
}
```
Two implementations:
- `FakeKeywordSuggester`: returns deterministic keywords extracted from the title (split words > 3 chars). Used in tests and local dev. Active when profile != "ai".
- `LlmKeywordSuggester`: calls OpenAI or Bedrock. Active with profile "ai". NOT needed for the minimum version — implement later.

### API Endpoints

```
POST   /api/publications                         FR-001  Create
GET    /api/publications/{id}                     FR-003  View
PUT    /api/publications/{id}                     FR-004  Update
DELETE /api/publications/{id}                     FR-005  Delete
GET    /api/publications?q=&author=&keyword=&journal=&yearFrom=&yearTo=&page=&size=&sort=  FR-006  Search
POST   /api/publications/import                   FR-002  Import by DOI
POST   /api/publications/{id}/keyword-suggestions FR-007  AI keywords
GET    /actuator/health                           Health check
```

**Request/Response examples:**

Create request (FR-001):
```json
{
  "title": "Attention Is All You Need",
  "abstractText": "The dominant sequence transduction models...",
  "authors": [
    {"name": "Ashish Vaswani"},
    {"name": "Noam Shazeer", "orcid": "0000-0001-1234-5678"}
  ],
  "doi": "10.5555/3295222.3295349",
  "publicationYear": 2017,
  "journalName": "Advances in Neural Information Processing Systems",
  "keywords": ["attention", "transformer", "neural machine translation"]
}
```

Publication response:
```json
{
  "id": 1,
  "title": "Attention Is All You Need",
  "abstractText": "The dominant sequence transduction models...",
  "authors": [
    {"name": "Ashish Vaswani", "orcid": null},
    {"name": "Noam Shazeer", "orcid": "0000-0001-1234-5678"}
  ],
  "doi": "10.5555/3295222.3295349",
  "publicationYear": 2017,
  "journalName": "Advances in Neural Information Processing Systems",
  "keywords": ["attention", "transformer", "neural machine translation"],
  "source": "MANUAL",
  "createdAt": "2026-11-20T10:30:00Z",
  "updatedAt": "2026-11-20T10:30:00Z"
}
```

Import request (FR-002):
```json
{ "doi": "10.1038/nature14539" }
```

Search response (FR-006): Standard Spring Page wrapper with `content`, `totalElements`, `totalPages`, `number`, `size`.

Keyword suggestions response (FR-007):
```json
{
  "publicationId": 1,
  "suggestedKeywords": ["deep learning", "neural networks", "representation learning"]
}
```

Error response (all errors):
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/publications",
  "violations": [
    {"field": "title", "message": "must not be blank"},
    {"field": "publicationYear", "message": "must not be in the future"}
  ]
}
```

---

## Implementation Tasks

Work in this exact order. After each phase: run `mvn verify`, review the code, commit.

### Phase 1: Project Setup (T001–T005)

**T001 [FR: all]** Generate Spring Boot project with Maven. Dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, spring-boot-starter-actuator, flyway-core, flyway-database-postgresql, postgresql driver, springdoc-openapi-starter-webmvc-ui, spring-boot-starter-test, spring-boot-testcontainers, testcontainers-postgresql, testcontainers-junit-jupiter, wiremock-spring-boot (or wiremock-standalone), jacoco-maven-plugin.

**T002 [NFR]** Configure `application.yml`:
- datasource: `jdbc:postgresql://localhost:5432/papertrail`
- `spring.jpa.hibernate.ddl-auto: validate` (Flyway manages schema, NOT Hibernate)
- `spring.jpa.show-sql: true` (for development, disable later)
- `spring.flyway.locations: classpath:db/migration`
- Server port 8080
- Crossref base URL and timeout as configurable properties

**T003 [NFR]** Create `docker-compose.yml`: postgres:16 with volume, port 5432, database `papertrail`. App service building from Dockerfile, port 8080, depends_on postgres.

**T004 [NFR]** Create multi-stage `Dockerfile`: build with Maven, run with Eclipse Temurin 21 JRE.

**T005 [NFR]** Configure JaCoCo in pom.xml: 80% minimum line coverage, fail build if below.

**Verify:** `docker compose up -d db`, `mvn spring-boot:run`, hit `http://localhost:8080/actuator/health` → `{"status":"UP"}`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

**Commit:** `feat: project setup with Docker, Flyway, actuator and Swagger`

### Phase 2: Data Model + CRUD (T006–T015)

**T006 [FR-001, NFR-001, NFR-002, NFR-003]** Flyway migration `V1__create_schema.sql`: create all tables, indexes, the tsvector trigger. Use the exact schema from the Data Model section.

**T007 [FR-001]** JPA entities: Publication, Author, PublicationAuthor, Keyword. Use `@Entity`, `@Id`, `@GeneratedValue`, `@ManyToMany` or explicit join entity for ordered authors.

**T008 [FR-001]** DTOs: CreatePublicationRequest, UpdatePublicationRequest, PublicationResponse, AuthorRequest, AuthorResponse. Use Jakarta Validation annotations: `@NotBlank`, `@Size`, `@Min`, custom `@ValidDoi`, `@PastOrPresentYear`.

**T009 [FR-001]** PublicationService: create method. Normalize DOI, check uniqueness, save publication with authors (preserving order) and keywords.

**T010 [FR-003]** PublicationService: findById. Return PublicationResponse with authors in order.

**T011 [FR-004]** PublicationService: update. Full replacement of authors and keywords.

**T012 [FR-005]** PublicationService: delete. Return 204.

**T013 [FR-001, FR-003, FR-004, FR-005, NFR-004]** PublicationController with all CRUD endpoints. Global exception handler returning Problem Details for: validation errors (400), not found (404), duplicate DOI (409), unexpected errors (500).

**T014 [FR-001, NFR-001]** Tests — controller layer (@WebMvcTest):
- Create valid publication → 201
- Create with blank title → 400 with violations
- Create with empty authors → 400
- Create with year in the future → 400
- Create with duplicate DOI → 409
- Get existing → 200 with authors in order
- Get non-existent → 404 with Problem Details
- Update → 200
- Delete → 204, then GET → 404

**T015 [FR-001, NFR-001, NFR-002]** Tests — integration (@SpringBootTest + Testcontainers):
- Create and retrieve: authors are in correct order
- DOI uniqueness enforced at database level (try inserting same DOI directly)
- DOI normalization: "HTTPS://DOI.ORG/10.1234/TEST" and "10.1234/test" treated as same

**Verify:** `mvn verify` passes, coverage ≥ 80%. Manually test via Swagger UI: create a publication, get it, update it, delete it, try invalid inputs.

**Commit:** `feat: publication CRUD with validation and error handling`

### Phase 3: Search (T016–T020)

**T016 [FR-006, NFR-005]** Flyway migration `V2__search_index.sql` (only if the GIN index wasn't in V1): ensure the GIN index and trigger exist.

**T017 [FR-006]** SearchQuery DTO with optional fields: q (free text), author, keyword, journal, yearFrom, yearTo. Pageable with defaults (page=0, size=20, max 100).

**T018 [FR-006, NFR-005]** PublicationRepository: custom query using `@Query` with native SQL. Full-text search: `search_vector @@ plainto_tsquery('english', :q)`. Combine with other filters using dynamic criteria or Specification pattern. Order by relevance when searching text, by year desc otherwise.

**T019 [FR-006]** PublicationController: GET `/api/publications` with query params.

**T020 [FR-006]** Tests:
- Search by text in title → finds it
- Search by text in abstract → finds it
- Search by author name (partial, case-insensitive) → finds it
- Search by keyword (exact, case-insensitive) → finds it
- Search by year range → correct filtering
- Combined filters → intersection (AND)
- Pagination: size=2, check totalPages
- Size > 100 → capped or rejected
- Empty results → 200 with empty content, not 404
- Integration test: insert 100 publications, verify search uses index (optional: log the query plan)

**Verify:** `mvn verify`. Manually: add 3–4 papers via Swagger, search with different filters.

**Commit:** `feat: full-text search with pagination`

### Phase 4: Import by DOI (T021–T027)

**T021 [FR-002]** CrossrefClient interface:
```java
public interface CrossrefClient {
    Optional<CrossrefWork> fetchByDoi(String doi);
}
```

**T022 [FR-002]** CrossrefClientImpl using Spring RestClient. Configurable base URL and timeouts. User-Agent header. Maps Crossref JSON to CrossrefWork record. Strips JATS XML tags from abstract. Handles 404 (returns empty Optional) and 5xx (throws ServiceUnavailableException).

**T023 [FR-002]** CrossrefMapper: maps CrossrefWork → CreatePublicationRequest. Handles missing abstract (set null), missing authors, year extraction from `published-print` or `published-online` or `created` date parts.

**T024 [FR-002]** PublicationService.importByDoi: normalize DOI → check if exists (return existing) → fetch from Crossref → map → save with source=CROSSREF.

**T025 [FR-002]** PublicationController: POST `/api/publications/import`.

**T026 [FR-002]** Tests with WireMock (controller + integration):
- Import valid DOI → 201, publication saved with correct data
- Import same DOI again → 200, same publication, no duplicate
- Import DOI with URL prefix "https://doi.org/10.1038/nature14539" → normalized, works
- Import DOI with uppercase → normalized to lowercase
- Crossref has no abstract → publication saved without abstract
- Crossref abstract has JATS tags → tags stripped
- Crossref returns 404 → 404 with clear message
- Crossref returns 500 → 503 "External service unavailable"
- Crossref times out → 503 with timeout message

**T027 [FR-002]** JATS tag stripping: unit test with real examples:
- `<jats:p>Some text</jats:p>` → `Some text`
- `<jats:italic>word</jats:italic>` → `word`
- Nested tags
- null input → null output

**Verify:** `mvn verify`. Manual test: import `10.1038/nature14539` (Deep Learning, LeCun et al.) via Swagger. Import it again — should return the same record. Try a fake DOI.

**Commit:** `feat: import publication by DOI from Crossref`

---

## CI: GitHub Actions

Create `.github/workflows/ci.yml` right after Phase 1:

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Build, test, coverage gate
        run: mvn -B verify
      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco
```

Protect the `main` branch: Settings → Branches → require status check to pass. Work on feature branches and merge via pull requests.

---

## Manual Test File

Create `requests.http` (works in IntelliJ and VS Code REST Client extension):

```http
### Health check
GET http://localhost:8080/actuator/health

### Create publication (FR-001)
POST http://localhost:8080/api/publications
Content-Type: application/json

{
  "title": "Attention Is All You Need",
  "abstractText": "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks.",
  "authors": [
    {"name": "Ashish Vaswani"},
    {"name": "Noam Shazeer"}
  ],
  "doi": "10.5555/3295222.3295349",
  "publicationYear": 2017,
  "journalName": "NeurIPS",
  "keywords": ["attention", "transformer"]
}

### Create with invalid data — should return 400 (FR-001, NFR-004)
POST http://localhost:8080/api/publications
Content-Type: application/json

{
  "title": "",
  "authors": [],
  "publicationYear": 2999
}

### Get publication (FR-003)
GET http://localhost:8080/api/publications/1

### Get non-existent — should return 404 (NFR-004)
GET http://localhost:8080/api/publications/99999

### Update publication (FR-004)
PUT http://localhost:8080/api/publications/1
Content-Type: application/json

{
  "title": "Attention Is All You Need (Updated)",
  "abstractText": "Updated abstract.",
  "authors": [
    {"name": "Ashish Vaswani"},
    {"name": "Noam Shazeer"},
    {"name": "Niki Parmar"}
  ],
  "publicationYear": 2017,
  "journalName": "NeurIPS",
  "keywords": ["attention", "transformer", "self-attention"]
}

### Delete publication (FR-005)
DELETE http://localhost:8080/api/publications/1

### Search by text (FR-006)
GET http://localhost:8080/api/publications?q=transformer&page=0&size=20

### Search by author (FR-006)
GET http://localhost:8080/api/publications?author=vaswani

### Search by year range (FR-006)
GET http://localhost:8080/api/publications?yearFrom=2015&yearTo=2020

### Search combined (FR-006)
GET http://localhost:8080/api/publications?q=neural&author=lecun&yearFrom=2010

### Import by DOI (FR-002)
POST http://localhost:8080/api/publications/import
Content-Type: application/json

{"doi": "10.1038/nature14539"}

### Import same DOI again — should return existing, not duplicate (FR-002)
POST http://localhost:8080/api/publications/import
Content-Type: application/json

{"doi": "https://doi.org/10.1038/NATURE14539"}

### Import non-existent DOI — should return 404 (FR-002)
POST http://localhost:8080/api/publications/import
Content-Type: application/json

{"doi": "10.9999/doesnotexist"}

### AI keyword suggestions (FR-007) — uses fake in local dev
POST http://localhost:8080/api/publications/1/keyword-suggestions
```

---

## AI Log

Create `AI_LOG.md` in the project root. After every significant AI interaction, add a line:

```markdown
# AI Development Log

| Date | Phase | What happened | What I corrected |
|------|-------|--------------|-----------------|
| | | | |
```

This is critical for the README and interview. Record:
- When the AI generated code that didn't work and why
- When the AI tried to weaken a test to make it pass
- When the AI added unnecessary complexity (Redis, Kafka, extra abstractions)
- When the AI got something right on the first try
- Architecture or design decisions you made that the AI didn't suggest

---

## Important Warnings

1. **`spring.jpa.hibernate.ddl-auto` must be `validate`, never `update` or `create`.** Flyway manages the schema. If the AI sets it to anything else, change it back.
2. **Tests must never call real external APIs.** Use WireMock for Crossref, FakeKeywordSuggester for AI. Only manual testing (requests.http) hits real APIs.
3. **Never let the AI change a failing test to make it pass.** Ask: "Explain the root cause before fixing. Do not change the test unless the test itself is wrong."
4. **Keep DTOs separate from entities.** If the AI returns JPA entities from controllers, fix it.
5. **Check for N+1 queries:** enable SQL logging, do a search that returns 5 results, count the SQL statements. If there are more than 2–3 queries, fix with `JOIN FETCH` or `@EntityGraph`.
6. **DOI normalization must happen in one place** (a utility method), used everywhere: create, import, search, duplicate check.

---

## How to Start

Paste this entire document as context in Claude Code and say:

```
I want to build this project step by step. Start with Phase 1 (T001–T005).
After each task, show me what was created and what to verify.
Do not skip ahead to the next phase until I confirm the current one works.
Explain Java/Spring concepts briefly when you introduce them — I know C and Python but I'm new to Java.
```

After Phase 1 works, say:
```
Phase 1 is verified. Proceed to Phase 2 (T006–T015).
```

And so on for Phase 3 and Phase 4.
