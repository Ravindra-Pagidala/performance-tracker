
-- uuid-ossp provides uuid_generate_v4() function.
-- We use UUIDs as primary keys instead of SERIAL integers because:
-- 1. Sequential integers expose employee count (security risk in HR systems)
-- 2. UUIDs are safe to expose in URLs, reports, audit logs
-- 3. When you eventually shard or federate, UUIDs don't collide across nodes
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";


-- =============================================================================
-- TABLE: employees
-- =============================================================================

CREATE TABLE employees (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),
    name            VARCHAR(255)    NOT NULL,
    department      VARCHAR(100)    NOT NULL,
    role            VARCHAR(100)    NOT NULL,
    joining_date    DATE            NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Primary key constraint
    CONSTRAINT pk_employees PRIMARY KEY (id),

    -- Business rule: name cannot be blank spaces
    CONSTRAINT chk_employee_name_not_blank
        CHECK (TRIM(name) <> ''),

    -- Business rule: department cannot be blank spaces
    CONSTRAINT chk_employee_department_not_blank
        CHECK (TRIM(department) <> ''),

    -- Business rule: role cannot be blank spaces
    CONSTRAINT chk_employee_role_not_blank
        CHECK (TRIM(role) <> ''),

    -- Business rule: joining date cannot be in the far future
    -- Employees can be pre-registered up to 1 year ahead max
    CONSTRAINT chk_employee_joining_date_reasonable
        CHECK (joining_date <= CURRENT_DATE + INTERVAL '1 year')
);

-- WHY this index?
-- GET /employees?department={dept}&minRating={x} filters by department.
-- Without this index, PostgreSQL scans ALL employee rows to find
-- employees in "Engineering". With 10,000 employees, that's 10,000
-- rows examined per request, for every manager viewing their dashboard.
-- With this index, PostgreSQL jumps directly to matching rows.
CREATE INDEX idx_employees_department
    ON employees (department);

-- WHY this composite index?
-- Your real query is always: WHERE department = ? AND is_active = TRUE
-- A composite index satisfies BOTH conditions from one index scan
-- without touching the main table at all (index-only scan).
-- More efficient than two separate single-column indexes.
CREATE INDEX idx_employees_department_active
    ON employees (department, is_active);

COMMENT ON TABLE employees IS
    'Stores all employee records. Never hard-delete — use is_active=false for terminations.';
COMMENT ON COLUMN employees.is_active IS
    'Soft delete flag. Terminated employees set to false. '
    'Their review history must be preserved for compliance.';


-- =============================================================================
-- TABLE: review_cycles
-- =============================================================================

CREATE TABLE review_cycles (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),
    name            VARCHAR(100)    NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN',

    -- CRITICAL PERFORMANCE OPTIMIZATION:
    -- Instead of SELECT AVG(rating) FROM performance_reviews WHERE cycle_id = ?
    -- (which scans ALL reviews for a cycle on every dashboard load),
    -- we maintain running totals updated atomically with each review INSERT.
    -- Average = total_rating / review_count — O(1) regardless of review count.
    -- This is the same pattern financial ledger systems use for account balances.
    -- Banks don't recompute your balance by summing every transaction since 1995.
    total_rating    INTEGER         NOT NULL DEFAULT 0,
    review_count    INTEGER         NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_review_cycles PRIMARY KEY (id),

    -- Business rule: cycle names must be unique ("Q1 2025" cannot exist twice)
    -- UNIQUE automatically creates a B-tree index — no separate index needed
    CONSTRAINT uq_review_cycle_name UNIQUE (name),

    -- Business rule: only valid status values allowed
    -- DB-level enum enforcement — if application has a bug and tries to
    -- insert "OPEN_MAYBE", the DB rejects it regardless
    CONSTRAINT chk_review_cycle_status
        CHECK (status IN ('OPEN', 'CLOSED', 'ARCHIVED')),

    -- Business rule: end date must be after start date
    CONSTRAINT chk_review_cycle_dates
        CHECK (end_date > start_date),

    -- Business rule: running totals cannot be negative
    CONSTRAINT chk_review_cycle_total_rating_non_negative
        CHECK (total_rating >= 0),

    CONSTRAINT chk_review_cycle_review_count_non_negative
        CHECK (review_count >= 0),

    -- Business rule: average rating integrity
    -- If review_count is 0, total_rating must also be 0
    CONSTRAINT chk_review_cycle_totals_consistent
        CHECK (
            (review_count = 0 AND total_rating = 0)
            OR
            (review_count > 0 AND total_rating > 0)
        )
);

-- WHY this index?
-- "List all open cycles" is a common query any HR dashboard needs.
-- status has very low cardinality (only 3 values) but filtering
-- OPEN cycles from a table of thousands of historical cycles
-- still benefits from an index scan over a full table scan.
CREATE INDEX idx_review_cycles_status
    ON review_cycles (status);

COMMENT ON TABLE review_cycles IS
    'Performance review periods (e.g. Q1 2025). '
    'total_rating and review_count are maintained as running totals '
    'for O(1) average computation — never recomputed from reviews table.';


-- =============================================================================
-- TABLE: performance_reviews
-- =============================================================================

CREATE TABLE performance_reviews (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),

    -- FK to employees — the employee being reviewed
    employee_id     UUID            NOT NULL,

    -- FK to review_cycles — which cycle this review belongs to
    cycle_id        UUID            NOT NULL,

    -- FK to employees (self-referential) — the manager submitting the review
    -- WHY reference employees not a separate users table?
    -- Managers are also employees in most orgs.
    -- This self-referential relationship correctly models real org structure.
    reviewer_id     UUID            NOT NULL,

    -- SMALLINT saves 2 bytes per row vs INTEGER.
    -- Trivial per row but signals appropriate data type thinking.
    -- At 10M reviews: SMALLINT = 20MB, INTEGER = 40MB for this column alone.
    rating          SMALLINT        NOT NULL,

    notes           TEXT,
    submitted_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_performance_reviews PRIMARY KEY (id),

    CONSTRAINT fk_reviews_employee
        FOREIGN KEY (employee_id)
        REFERENCES employees (id)
        ON DELETE RESTRICT,
        -- RESTRICT not CASCADE: never auto-delete reviews if employee deleted.
        -- HR systems must preserve review history for compliance.

    CONSTRAINT fk_reviews_cycle
        FOREIGN KEY (cycle_id)
        REFERENCES review_cycles (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_reviews_reviewer
        FOREIGN KEY (reviewer_id)
        REFERENCES employees (id)
        ON DELETE RESTRICT,

    -- Rating must be between 1 and 5 — DB enforced, not just app enforced
    CONSTRAINT chk_review_rating
        CHECK (rating BETWEEN 1 AND 5),

    -- CRITICAL BUSINESS RULE:
    -- The SAME reviewer cannot review the SAME employee in the SAME cycle twice.
    -- This prevents duplicate submissions (accidental double-click, retry storms).
    -- BUT two DIFFERENT reviewers CAN both review the same employee (360 feedback).
    CONSTRAINT uq_review_per_reviewer_per_cycle
        UNIQUE (employee_id, cycle_id, reviewer_id),

    -- Business rule: an employee cannot review themselves
    CONSTRAINT chk_review_not_self_review
        CHECK (employee_id <> reviewer_id)
);

-- WHY this index?
-- GET /employees/{id}/reviews → WHERE employee_id = ?
-- This is the most frequently called read query in the system.
-- Every manager viewing an employee profile triggers this query.
-- Without index: full scan of ALL reviews (100k+ rows) per request.
-- With index: direct lookup — O(log n).
CREATE INDEX idx_reviews_employee_id
    ON performance_reviews (employee_id);

-- WHY this index?
-- GET /cycles/{id}/summary needs to find all reviews for a cycle.
-- Also used when Hibernate loads reviews for a cycle.
CREATE INDEX idx_reviews_cycle_id
    ON performance_reviews (cycle_id);

-- WHY this COMPOSITE index with DESC?
-- The summary endpoint finds the TOP PERFORMER (highest rating in a cycle).
-- With (cycle_id, rating DESC), PostgreSQL reads the first entry of this
-- index for a given cycle_id — the highest-rated employee is already
-- at the top. Zero sorting needed. This is called a "loose index scan".
-- Without this: PostgreSQL scans all reviews for a cycle, sorts by rating.
-- With this: PostgreSQL reads exactly 1 index entry. O(1) top-performer lookup.
CREATE INDEX idx_reviews_cycle_rating
    ON performance_reviews (cycle_id, rating DESC);

-- WHY this composite index?
-- GET /employees?department=X&minRating=Y needs per-employee average rating.
-- This index makes per-employee aggregation within a cycle fast.
CREATE INDEX idx_reviews_employee_cycle
    ON performance_reviews (employee_id, cycle_id);

COMMENT ON TABLE performance_reviews IS
    'Performance reviews submitted by managers (reviewer_id) for employees. '
    'One reviewer cannot review the same employee twice in the same cycle. '
    'Multiple different reviewers CAN review the same employee (360-degree feedback).';


-- =============================================================================
-- TABLE: goals
-- =============================================================================

CREATE TABLE goals (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),
    employee_id     UUID            NOT NULL,
    cycle_id        UUID            NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_goals PRIMARY KEY (id),

    CONSTRAINT fk_goals_employee
        FOREIGN KEY (employee_id)
        REFERENCES employees (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_goals_cycle
        FOREIGN KEY (cycle_id)
        REFERENCES review_cycles (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_goal_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'MISSED')),

    CONSTRAINT chk_goal_title_not_blank
        CHECK (TRIM(title) <> '')

    -- WHY no FK to performance_reviews?
    -- Goals and reviews are PARALLEL, INDEPENDENT entities.
    -- Both belong to a (employee, cycle) pair — NOT to each other.
    -- An employee has goals set at cycle START, before any reviews exist.
    -- Linking goals to reviews would mean you cannot create goals
    -- until a review exists — that is backwards business logic.
);

-- WHY this composite index?
-- GET /cycles/{id}/summary needs: completed vs missed goals COUNT per cycle.
-- Query: SELECT status, COUNT(*) FROM goals WHERE cycle_id = ? GROUP BY status
-- This index lets PostgreSQL answer that query using ONLY the index
-- without touching the goals table at all (index-only scan).
CREATE INDEX idx_goals_cycle_status
    ON goals (cycle_id, status);

-- WHY this composite index?
-- Loading goals for a specific employee in a specific cycle.
-- Used by GET /employees/{id}/reviews (returns goals context too).
CREATE INDEX idx_goals_employee_cycle
    ON goals (employee_id, cycle_id);

COMMENT ON TABLE goals IS
    'Employee goals for a review cycle. Independent of performance_reviews — '
    'goals exist from cycle start, reviews come later. '
    'Both hang off (employee_id, cycle_id) independently.';


-- =============================================================================
-- TABLE: employee_cycle_stats
-- =============================================================================
-- WHY does this table exist?
--
-- The endpoint GET /employees?department={dept}&minRating={x} without
-- this table requires:
--   SELECT e.*, AVG(pr.rating) as avg_rating
--   FROM employees e
--   JOIN performance_reviews pr ON e.id = pr.employee_id
--   WHERE e.department = ?
--   GROUP BY e.id
--   HAVING AVG(pr.rating) >= ?
--
-- This is a JOIN + GROUP BY + HAVING aggregate on every single API call.
-- With 500 managers refreshing dashboards during performance season,
-- that's 500 simultaneous aggregate queries — connection pool exhaustion.
--
-- With this table, that query becomes:
--   SELECT e.*, ecs.avg_rating
--   FROM employees e
--   JOIN employee_cycle_stats ecs ON e.id = ecs.employee_id
--   WHERE e.department = ? AND ecs.avg_rating >= ? AND ecs.cycle_id = ?
--
-- Simple indexed join. No aggregation at query time.
-- This table is updated in the SAME TRANSACTION as the review INSERT.
-- Denormalization done deliberately and transactionally is not a design
-- =============================================================================

CREATE TABLE employee_cycle_stats (
    employee_id         UUID            NOT NULL,
    cycle_id            UUID            NOT NULL,
    avg_rating          NUMERIC(3,2)    NOT NULL DEFAULT 0.00,

    -- Running totals (same pattern as review_cycles table)
    -- avg_rating = total_rating / review_count (computed, not stored separately)
    total_rating        INTEGER         NOT NULL DEFAULT 0,
    review_count        INTEGER         NOT NULL DEFAULT 0,

    goals_completed     INTEGER         NOT NULL DEFAULT 0,
    goals_missed        INTEGER         NOT NULL DEFAULT 0,
    goals_pending       INTEGER         NOT NULL DEFAULT 0,

    last_updated        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Composite primary key — one stats row per (employee, cycle) pair
    CONSTRAINT pk_employee_cycle_stats
        PRIMARY KEY (employee_id, cycle_id),

    CONSTRAINT fk_ecs_employee
        FOREIGN KEY (employee_id)
        REFERENCES employees (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ecs_cycle
        FOREIGN KEY (cycle_id)
        REFERENCES review_cycles (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_ecs_avg_rating_range
        CHECK (avg_rating BETWEEN 0.00 AND 5.00),

    CONSTRAINT chk_ecs_review_count_non_negative
        CHECK (review_count >= 0),

    CONSTRAINT chk_ecs_goals_non_negative
        CHECK (
            goals_completed >= 0
            AND goals_missed >= 0
            AND goals_pending >= 0
        )
);

-- WHY this index?
-- The department filter endpoint queries:
-- WHERE ecs.cycle_id = ? AND ecs.avg_rating >= ?
-- This composite index with DESC on avg_rating means:
-- 1. PostgreSQL filters by cycle_id first (high selectivity)
-- 2. Then applies minRating filter on already-sorted avg_rating
-- Results come back pre-sorted by rating descending — no sort step needed.
CREATE INDEX idx_ecs_cycle_avg_rating
    ON employee_cycle_stats (cycle_id, avg_rating DESC);

-- WHY this index?
-- Looking up stats for a specific employee across all cycles.
-- Used when building employee profile/history views.
CREATE INDEX idx_ecs_employee_id
    ON employee_cycle_stats (employee_id);

COMMENT ON TABLE employee_cycle_stats IS
    'Pre-computed per-employee stats per cycle. '
    'Updated atomically in the same transaction as review INSERT/UPDATE. '
    'Eliminates aggregate JOIN queries on the hot GET /employees filter endpoint. '
    'avg_rating = total_rating / review_count — recomputed on each update.';


-- =============================================================================
-- TRIGGER: auto-update updated_at columns
-- =============================================================================
-- WHY a trigger instead of application-level update?
-- If anyone updates a row via direct SQL, migration script, or another
-- service, the updated_at still gets set correctly.
-- Application code can forget. The DB never forgets.

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_employees_updated_at
    BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_performance_reviews_updated_at
    BEFORE UPDATE ON performance_reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_goals_updated_at
    BEFORE UPDATE ON goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


INSERT INTO review_cycles (id, name, start_date, end_date, status)
VALUES (
    uuid_generate_v4(),
    'Q1 2025',
    '2025-01-01',
    '2025-03-31',
    'OPEN'
);

INSERT INTO review_cycles (id, name, start_date, end_date, status)
VALUES (
    uuid_generate_v4(),
    'Q2 2025',
    '2025-04-01',
    '2025-06-30',
    'OPEN'
);