# IMDb High Performance Assignment

![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square&logo=springboot)
![Maven](https://img.shields.io/badge/Maven-3.x-C71A36?style=flat-square&logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![Status](https://img.shields.io/badge/Status-Complete-brightgreen?style=flat-square)

A high-performance Spring Boot application that ingests the full IMDb dataset,
builds optimized in-memory indexes at startup, and exposes fast REST endpoints
for querying titles, crew relationships, actor co-appearances, and genre rankings.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Why Heap-Based Storage](#why-heap-based-storage)
- [Startup & Loading Strategy](#startup--loading-strategy)
- [JVM Tuning](#jvm-tuning)
- [API Endpoints](#api-endpoints)
- [Curl Examples](#curl-examples)
- [Swagger / OpenAPI](#swagger--openapi)
- [Running the Application](#running-the-application)
- [Project Structure](#project-structure)
- [Future Improvements](#future-improvements)

---

## Architecture Overview

```
HTTP Request
     │
     ▼
RequestCounterFilter          — counts every request since startup (AtomicLong)
     │
     ▼
TitleQueryController          — REST layer, input validation, response wrapping
     │
     ▼
TitleQueryService             — business logic, caching, ready-state guard
     │
     ▼
ImdbDataStore                 — in-memory indexes (ConcurrentHashMap / HashSet)
     ▲
     │
ImdbDatasetLoader             — parallel TSV.GZ parsing at startup
```

**Layers:**

| Layer | Responsibility                                              |
|-------|-------------------------------------------------------------|
| `controller/` | REST endpoints, request validation, ApiResponse wrapping    |
| `service/` | Query logic, caching, ensureReady() guard, Parallel file parsing    |
| `model/dto/` | Response DTOs — never expose internal entities directly     |
| `model/entity/` | Lightweight POJOs mapped from TSV rows                      |
| `model/response/` | Uniform API envelope (ApiResponse, PagedResponse, ApiError) |
| `filter/` | HTTP request counter via OncePerRequestFilter               |
| `exception/` | Global exception handler — structured error responses       |
| `config/` | Caffeine cache configuration                                |
| `service/utils/` | TSV parsing utilities, field helpers                        |

---

## Why Heap-Based Storage

Two approaches were evaluated before settling on pure Java heap:

### H2 File Mode
- Data persisted to disk — no reload on restart
- Startup with existing file is instant
- Query performance limited by H2's SQL engine overhead
- Index creation after bulk load added 15–20 minutes to startup
- Total cold-start time: ~40 minutes

### H2 In-Memory Mode
- Faster queries than file mode
- H2 internally stores rows with significant metadata overhead
- **Real-world overhead: 10–20x raw data size** — the full dataset
  required well over 32GB heap, causing OOM even on 32GB RAM machines
- Not viable for this dataset size

### Final Decision: Pure Java Heap (ConcurrentHashMap / HashSet)

Java's native data structures store only what you put in them — no SQL engine,
no page cache, no transaction log, no index B-tree overhead.

| Metric | H2 In-Memory | Java Heap |
|--------|-------------|-----------|
| Memory overhead | 10–20x raw data | 2–3x raw data |
| Insert speed | Slow (SQL parsing, WAL) | Fast (direct map.put) |
| Lookup speed | SQL query planner | O(1) HashMap.get |
| Required RAM (full dataset) | 100GB+ | ~15GB |
| Query response time | Seconds | Milliseconds |

This is the correct architectural choice for a read-only, query-heavy,
standalone application over a large fixed dataset.

---

## Startup & Loading Strategy

Loading is phase-based to maximize parallelism while respecting data dependencies.

```
Phase 1 — Parallel (3 threads simultaneously)
├── title.basics.tsv.gz    → titleById index + titlesByGenre index
├── title.ratings.tsv.gz   → ratingById index
└── name.basics.tsv.gz     → personById index + personsByName index

Phase 2 — Sequential (depends on Phase 1 complete)
└── title.crew.tsv.gz      → computes Task 2 result immediately at load time
                              No intermediate maps stored — director/writer
                              intersection + alive check done per row,
                              final DTOs stored directly

Phase 3 — Sequential (depends on Phase 1 complete)
└── title.principals.tsv.gz → titlesByActor index (actors/actresses only)
```

**Key optimization in Phase 2:**
Rather than storing `directorsByTitle` and `writersByTitle` maps (~3.5GB combined),
the director-writer intersection and alive check are computed during the single
crew file pass. Only the final `DirectorWriterTitleDto` objects are stored (~500MB).
This saves ~3GB of heap with no query-time tradeoff.

**Approximate load times (32GB RAM, -Xmx16g):**

| Phase | Time |
|-------|------|
| Phase 1 (parallel) | ~60s |
| Phase 2 (crew) | ~45s |
| Phase 3 (principals) | ~90s |
| **Total** | **~3 minutes** |

The HTTP server starts immediately. All endpoints return `HTTP 503` with a clear
message until loading completes. The `/api/imdb/request-count` endpoint is available
from the first second — before data is ready.

---

## JVM Tuning

The application requires explicit heap allocation. Default JVM heap (~25% of RAM)
is insufficient for the full dataset.

```bash
java -Xms8g -Xmx16g -jar imdb-high-performance-assignment.jar
```

**Tradeoffs:**

| Setting | Effect |
|---------|--------|
| Higher `-Xmx` | More headroom during load peaks, fewer GC pauses during queries |
| Lower `-Xmx` | Smaller memory footprint, risk of OOM during parallel phase 1 |
| `-Xms` = ~40% of `-Xmx` | Pre-allocates heap — avoids JVM growing it incrementally during load |

**Recommended by available RAM:**

| System RAM | Recommended JVM flags |
|------------|----------------------|
| 16GB | `-Xms5g -Xmx13g` (tight, monitor GC) |
| 32GB | `-Xms8g -Xmx20g` (comfortable) |
| 64GB | `-Xms12g -Xmx28g` (headroom for large genres) |

---

## API Endpoints

All responses follow a uniform envelope:

```json
{
  "status": "success",
  "data": { ... },
  "requestCount": 42,
  "timestamp": "2026-05-01T10:00:00Z"
}
```

Errors return the same shape with `"status": "error"` and an `error` object
instead of `data`.

---

### Task 1 — Dataset Import

Handled automatically at startup. No endpoint required.
Monitor progress via application logs — progress is reported every 500,000 rows.

---

### Task 2 — Titles Where Director == Writer (Person Alive)

```
GET /api/imdb/task2/director-writer-titles?page=0&size=20
```

Returns paginated titles where at least one person is both the director
and writer of the title, and that person is still alive (`deathYear` is null).

Result is computed once at startup and cached — subsequent pages are served
from an in-memory sorted list with zero recomputation.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Zero-based page index |
| `size` | int | 20 | Results per page |

---

### Task 3 — Common Titles for Two Actors

```
GET /api/imdb/task3/common-titles?actor1={nconst}&actor2={nconst}
GET /api/imdb/task3/common-titles-by-name?actor1={name}&actor2={name}
```

Returns all titles where both specified actors appeared together,
sorted by rating descending.

The name-based endpoint resolves actor names to IMDB nconst identifiers.
When multiple people share the same name, the most-credited person is selected.

Results are cached in a bounded LRU map (100 actor pairs).

---

### Task 4 — Best Title Per Year by Genre

```
GET /api/imdb/task4/best-by-genre?genre={genre}
```

Returns the highest-rated title for each year within the requested genre.
Ranking: `averageRating DESC`, `numVotes DESC` as tiebreaker.
Results sorted by year descending. Maximum ~130 rows (one per year).

Cached per genre key using Caffeine — subsequent calls for the same genre
return instantly.

Valid genre values: `Action`, `Adventure`, `Animation`, `Biography`, `Comedy`,
`Crime`, `Documentary`, `Drama`, `Fantasy`, `Horror`, `Music`, `Mystery`,
`Romance`, `Sci-Fi`, `Sport`, `Thriller`, `War`, `Western`

---

### Task 5 — HTTP Request Counter

```
GET /api/imdb/request-count
```

Returns the total number of HTTP requests received since the last application
startup. Available immediately — does not require data load to complete.

---

## Curl Examples

```bash
# Task 2 — page 0, 20 results
curl "http://localhost:8080/api/imdb/task2/director-writer-titles?page=0&size=20"

# Task 3 — by nconst ID (Tom Hanks + Robin Wright)
curl "http://localhost:8080/api/imdb/task3/common-titles?actor1=nm0000158&actor2=nm0000741"

# Task 3 — by name
curl "http://localhost:8080/api/imdb/task3/common-titles-by-name?actor1=Tom%20Hanks&actor2=Robin%20Wright"

# Task 4 — best Drama title per year
curl "http://localhost:8080/api/imdb/task4/best-by-genre?genre=Drama"

# Task 5 — request counter
curl "http://localhost:8080/api/imdb/request-count"
```

---

## Swagger / OpenAPI

Interactive API documentation is available after startup at:

```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI spec (JSON):

```
http://localhost:8080/v3/api-docs
```

---

## Running the Application

### Prerequisites

- Java 21+
- Maven 3.8+
- IMDb dataset files (`.tsv.gz`) in a local directory

Download dataset files from: https://datasets.imdbws.com/

Required files:
```
title.basics.tsv.gz
title.ratings.tsv.gz
name.basics.tsv.gz
title.crew.tsv.gz
title.principals.tsv.gz
```

### Configuration

Edit `src/main/resources/application.properties`:

```properties
imdb.dataset-path=/path/to/your/dataset/
app.skip-data-load=false
```

Set `app.skip-data-load=true` to skip loading and use a pre-warmed store
(useful for development when you want instant startup).

### Run with Maven

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms8g -Xmx16g"
```

### Run with JAR

```bash
mvn clean package -DskipTests
java -Xms8g -Xmx16g -jar target/imdb-high-performance-assignment.jar
```

---

## Project Structure

```
src/main/java/com/mohkhan/imdb_assignment/
├── ImdbAssignmentApplication.java
├── controller/
│   └── TitleQueryController.java       — REST endpoints
├── service/
│   ├── TitleQueryService.java          — query logic + caching
│   ├── DataLoadStateService.java       — ready flag management
│   ├── ImdbLoadCoordinatorService.java — load orchestration
│   ├── ImdbDatasetLoader.java          — parallel phase-based TSV loader
│   └── utils/
│       ├── TsvFileUtil.java            — file reading helpers
│       └── FieldUtil.java              — TSV field parsing
├── model/
│   ├── store/
│   │    └── ImdbDataStore.java         — all in-memory indexes
│   ├── dto/
│   │   ├── DirectorWriterTitleDto.java
│   │   ├── CommonTitleDto.java
│   │   └── BestTitlePerYearDto.java
│   ├── entity/
│   │   ├── TitleBasicEntity.java
│   │   ├── TitleRatingEntity.java
│   │   └── PersonEntity.java
│   └── response/
│       ├── ApiResponse.java
│       ├── ApiError.java
│       └── PagedResponse.java
├── filter/
│   └── RequestCounterFilter.java       — AtomicLong request counter
├── config/
│   └── CacheConfig.java                — Caffeine cache setup
└── exception/
    ├── GlobalExceptionHandler.java
    ├── DataNotReadyException.java
    └── InvalidRequestException.java
```

---

## Future Improvements

Given more time, the following would be the next engineering steps:

**Performance**
- Off-heap memory (e.g. Chronicle Map) to reduce GC pressure on large indexes
- Binary dataset format (e.g. FlatBuffers, Apache Arrow) to cut parse time
- Incremental dataset refresh without full restart
- Parallel processing within a single large file (partitioned line reading)

**Scalability**
- GraalVM native image for faster startup and lower memory footprint
- Distributed cache (Redis) to share query results across instances
- Actor co-appearance graph index for multi-hop relationship queries

**Operational**
- `/actuator/health` readiness probe aligned with the `markReady()` flag
- Structured JSON logging with correlation IDs
- Micrometer metrics: load duration, cache hit rates, query latency histograms

**Containerization**
- Multi-stage Docker build + `docker-compose.yml` mounting dataset as volume with JVM flags via environment variable

**Cache Server Offload (Redis)**
- Offload precomputed results (`bestTitlesByGenre`, `directorWriterTitles`) to Redis via Spring Cache — enables multi-instance sharing and reduces required heap
- Tradeoff: ~1–5ms network latency per cache hit vs seconds of cold computation — worth it at scale

**Hybrid Heap / Off-Heap Storage**
- Keep `titleById`, `ratingById`, `personById` in heap for O(1) access; move `titlesByActor`, `titlesByGenre` to off-heap (Chronicle Map)
- Estimated heap reduction: ~15GB → ~6–8GB with negligible query performance impact

---

## Notes on Design Decisions

- **No JPA / Hibernate** — no relational database involved. Raw JDBC or ORM
  would add abstraction overhead with no benefit for in-memory read-only data.
- **`deathYear = null` treated as alive** — IMDB uses `\N` for both unknown
  and confirmed alive. Per assignment spec, null is treated as alive.
- **Genre parameter is case-sensitive** — matches IMDB's own genre strings
  (`Drama` not `drama`). Documented in Swagger.
- **Task 2 first call latency** — the sorted list is computed on first request
  and cached. Subsequent pages are O(1) list slices. First call may take
  several seconds depending on match count.

---

*Built as a technical assignment. All architectural decisions are documented
with explicit reasoning in the source code comments.*
