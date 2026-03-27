# Employee Performance Tracker API

A production-grade Spring Boot backend for an internal HR performance management system. Managers track employee performance, submit reviews, set goals, and run analytical reports across review cycles.

---

## What This Project Does

This system solves a real problem every mid-to-large company faces: structured, auditable employee performance tracking across quarterly review cycles. Think of it like the backend that powers Workday, BambooHR, or any in-house HR tool — but built from scratch with production architecture decisions baked in.

**Core capabilities:**

- Create and manage employees with department and role metadata
- Define review cycles (Q1 2025, Q2 2025, etc.)
- Submit performance reviews with ratings (1–5) and reviewer notes
- Track employee goals per cycle with PENDING / COMPLETED / MISSED status
- Query cycle-level summaries: average rating, top performer, goal completion counts
- Filter employees by department and minimum average rating

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Spring Boot App                       │
│                                                          │
│  Controller → Service → Repository → PostgreSQL          │
│                                                          │
│  EmployeeController    EmployeeService                   │
│  ReviewController      ReviewService    HikariCP Pool    │
│  GoalController        GoalService      (20 connections) │
│  CycleController       CycleService                      │
└─────────────────────────────────────────────────────────┘
                          │
                    Docker Network
                          │
┌─────────────────────────────────────────────────────────┐
│              PostgreSQL 15                               │
│  max_connections=100  shared_buffers=256MB               │
│                                                          │
│  Tables: employees, review_cycles, performance_reviews,  │
│          goals, employee_cycle_stats                     │
└─────────────────────────────────────────────────────────┘
```

**Key design decisions:**

- **UUID primary keys** — sequential integers expose employee count in URLs and are unsafe for HR data
- **Soft deletes** — `is_active = false` instead of DELETE, preserving review history for compliance
- **Running totals** — `total_rating` and `review_count` maintained atomically on `review_cycles`, making `AVG(rating)` computation O(1) instead of O(n)
- **`employee_cycle_stats` denormalization table** — pre-computed per-employee averages eliminate live JOIN + GROUP BY aggregation on the filter endpoint
- **Pessimistic locking** — `SELECT FOR UPDATE` on `review_cycles` and `employee_cycle_stats` during review submission prevents lost updates under concurrent load
- **Flyway migrations** — versioned, reproducible schema management

---

## Project Structure

```
performance-tracker/
├── src/main/java/com/hivel/tracker/
│   ├── controller/          # HTTP layer only — no business logic
│   │   ├── EmployeeController.java
│   │   ├── ReviewController.java
│   │   ├── GoalController.java
│   │   └── CycleController.java
│   ├── service/             # All business logic lives here
│   │   └── impl/
│   ├── repository/          # JPA repositories + custom HQL queries
│   ├── entity/              # JPA entities mapping to DB tables
│   ├── dto/
│   │   ├── request/         # What comes IN to the API
│   │   └── response/        # What goes OUT from the API
│   ├── exception/           # Custom exceptions + GlobalExceptionHandler
│   ├── enums/               # CycleStatus, GoalStatus
│   └── utils/               # ValidationUtils
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/
│       └── V1__initial_schema.sql   # Flyway migration
├── docker-compose.yml
├── Dockerfile
├── load-test.js             # Concurrency + DB validation test
└── pom.xml
```

---

## Prerequisites

- Docker Desktop installed and running
- Node.js 18+ (for load test)
- Java 17 + Maven (for local IntelliJ development only)

---

## Running The Application

### Option A — Full Docker (Recommended for testing)

Both PostgreSQL and Spring Boot run in Docker. This is how you demo and test.

**Step 1 — First time setup or after code changes:**

```bash
# From project root
docker compose down -v
docker compose up --build
```

The `--build` flag rebuilds the Spring Boot JAR inside Docker. Use this after any Java code change.

**Step 2 — Watch startup logs:**

```bash
docker compose logs -f app
```

Wait until you see:
```
Started PerformanceTrackerApplication in X.XXX seconds
```

Flyway will automatically run `V1__initial_schema.sql` and seed two review cycles (Q1 2025, Q2 2025).

**Step 3 — Verify the app is running:**

```bash
curl http://localhost:8080/employees
```

Expected response: `{"status":200,"message":"...","data":[]}`

---

### Option B — Local Development (IntelliJ + Docker DB)

Run only PostgreSQL in Docker, run Spring Boot from IntelliJ.

```bash
# Start only the database
docker compose up postgres -d

# Verify DB is healthy
docker compose ps
# Should show: performance_tracker_db   Up (healthy)

# Run from IntelliJ — or from terminal:
./mvnw spring-boot:run
```

`application.properties` points to `localhost:5433` for local dev. Docker compose maps PostgreSQL's internal 5432 to host port 5433 (to avoid conflicts with any locally installed PostgreSQL).

---

## Stopping Everything

```bash
# Stop containers but keep data
docker compose down

# Stop containers AND wipe all data (clean slate)
docker compose down -v
```

---

## API Endpoints

### POST /employees — Create an employee

```bash
curl -s -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sarah Chen",
    "department": "Engineering",
    "role": "Senior Engineer",
    "joiningDate": "2022-03-01"
  }' | python3 -m json.tool
```

### POST /reviews — Submit a performance review

```bash
curl -s -X POST http://localhost:8080/reviews \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "EMPLOYEE_UUID",
    "cycleId": "CYCLE_UUID",
    "reviewerId": "REVIEWER_UUID",
    "rating": 4,
    "notes": "Strong delivery, excellent cross-team collaboration."
  }' | python3 -m json.tool
```

### GET /employees/{id}/reviews — Get all reviews for an employee

```bash
curl -s http://localhost:8080/employees/EMPLOYEE_UUID/reviews | python3 -m json.tool
```

### GET /cycles/{id}/summary — Get cycle summary

```bash
curl -s http://localhost:8080/cycles/CYCLE_UUID/summary | python3 -m json.tool
```

Returns: average rating, top performer name + rating, completed goals count, missed goals count.

### GET /employees?department={dept}&minRating={x} — Filter employees

```bash
curl -s "http://localhost:8080/employees?department=Engineering&minRating=3" | python3 -m json.tool
```

### POST /goals — Create a goal

```bash
curl -s -X POST http://localhost:8080/goals \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "EMPLOYEE_UUID",
    "cycleId": "CYCLE_UUID",
    "title": "Complete Q1 OKRs",
    "status": "PENDING"
  }' | python3 -m json.tool
```

### PATCH /goals/{id}/status — Update goal status

```bash
curl -s -X PATCH http://localhost:8080/goals/GOAL_UUID/status \
  -H "Content-Type: application/json" \
  -d '{"status": "COMPLETED"}' | python3 -m json.tool
```

---

## Getting Cycle IDs

The seeded cycles (Q1 2025, Q2 2025) are inserted by Flyway but there is no GET /cycles endpoint. Query the DB directly:

```bash
docker exec -it performance_tracker_db \
  psql -U tracker_user -d performance_tracker \
  -c "SELECT id, name, status FROM review_cycles;"
```

---

## Running The Concurrency + DB Validation Test

This test fires all endpoints under concurrent load and cross-verifies every API response against direct DB queries.

```bash
# Install the PostgreSQL client for Node (one time)
npm install pg

# Run the test (app must be running in Docker first)
node load-test.js
```

**What the test covers:**

| Phase | What it tests | Concurrency |
|-------|--------------|-------------|
| 1 | POST /employees | 100 simultaneous |
| 2 | POST /goals | 100 simultaneous |
| 3 | PATCH /goals/{id}/status | 40 simultaneous |
| 4 | POST /reviews | 100 simultaneous |
| 5 | GET /employees/{id}/reviews | 30 simultaneous |
| 6 | GET /cycles/{id}/summary | 100 simultaneous |
| 7 | GET /employees?department&minRating | 15 simultaneous |
| 8 | 360-degree feedback edge case | 2 reviewers, same employee |
| 9 | Duplicate review guard | 5 identical concurrent requests |
| 10 | Pessimistic locking verification | 50 concurrent writes, same employee |

After each phase, the test queries PostgreSQL directly and compares the DB state against what the API returned. A mismatch means the API is returning stale or incorrect data.

---

## Checking DB State Manually

Connect to PostgreSQL inside Docker:

```bash
docker exec -it performance_tracker_db psql -U tracker_user -d performance_tracker
```

Useful queries:

```sql
-- All tables
\dt

-- All indexes
\di

-- Review cycle running totals vs actual aggregate
SELECT
  rc.name,
  rc.review_count as stored_count,
  COUNT(pr.id) as actual_count,
  rc.total_rating as stored_total,
  COALESCE(SUM(pr.rating), 0) as actual_total
FROM review_cycles rc
LEFT JOIN performance_reviews pr ON pr.cycle_id = rc.id
GROUP BY rc.id, rc.name, rc.review_count, rc.total_rating;

-- Top performers per cycle
SELECT
  e.name,
  e.department,
  ecs.avg_rating,
  ecs.review_count
FROM employee_cycle_stats ecs
JOIN employees e ON e.id = ecs.employee_id
ORDER BY ecs.avg_rating DESC, e.name ASC
LIMIT 10;

-- Goal status breakdown per cycle
SELECT
  rc.name as cycle,
  g.status,
  COUNT(*) as count
FROM goals g
JOIN review_cycles rc ON rc.id = g.cycle_id
GROUP BY rc.name, g.status
ORDER BY rc.name, g.status;
```

---

## Rebuilding After Code Changes

```bash
# Rebuild the app image and restart
docker compose up --build -d app

# Watch logs
docker compose logs -f app
```

If you change the SQL migration file, you must wipe the volume first (Flyway checksums the file and rejects modifications):

```bash
docker compose down -v
docker compose up --build
```

---

## Assumptions Made

- A reviewer cannot review themselves (enforced at DB level via CHECK constraint)
- The same reviewer cannot review the same employee twice in the same cycle (DB UNIQUE constraint), but multiple different reviewers can review the same employee — supporting 360-degree feedback
- Employees are never hard-deleted — `is_active = false` is used for terminations
- Goal status flows only forward: PENDING → COMPLETED or MISSED (no validation prevents backward transitions but the business logic assumes forward-only)
- Review cycles have no "close" endpoint in this implementation — status is managed directly in the DB for this scope
- The `GET /employees?department&minRating` filter operates across all cycles, not a specific cycle, as the assignment did not specify cycle scoping
