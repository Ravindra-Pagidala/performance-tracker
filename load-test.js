/**
 * load-test.js
 * Employee Performance Tracker — Full Concurrency + DB Validation Test
 *
 * HOW TO RUN:
 *   Terminal 1: docker compose up --build
 *   Terminal 2: npm install pg && node load-test.js
 */

const http = require("http");
const { Pool } = require("pg");

const CONFIG = {
  HOST: "localhost",
  PORT: 8080,
  BATCH_SIZE: 50,
  DB: {
    host: "localhost",
    port: 5433,
    database: "performance_tracker",
    user: "tracker_user",
    password: "tracker_pass",
  },
};

const DEPARTMENTS = ["Engineering", "Product", "Design", "Marketing", "Sales"];
const ROLES = ["Senior Engineer", "Engineer", "Manager", "Lead", "Analyst"];

// ─── HTTP ─────────────────────────────────────────────────────────────────────

function httpRequest(method, path, body = null) {
  return new Promise((resolve) => {
    const start = Date.now();
    const payload = body ? JSON.stringify(body) : null;
    const options = {
      hostname: CONFIG.HOST,
      port: CONFIG.PORT,
      path,
      method,
      headers: {
        "Content-Type": "application/json",
        ...(payload && { "Content-Length": Buffer.byteLength(payload) }),
      },
    };

    const req = http.request(options, (res) => {
      let raw = "";
      res.on("data", (c) => (raw += c));
      res.on("end", () => {
        const latency = Date.now() - start;
        try {
          resolve({
            ok: res.statusCode >= 200 && res.statusCode < 300,
            status: res.statusCode,
            body: JSON.parse(raw),
            latency,
          });
        } catch {
          resolve({ ok: false, status: res.statusCode, body: null, latency });
        }
      });
    });

    req.on("error", (e) =>
      resolve({ ok: false, status: 0, body: null, latency: Date.now() - start, error: e.message })
    );
    req.setTimeout(15000, () => {
      req.destroy();
      resolve({ ok: false, status: 0, body: null, latency: 15000, error: "timeout" });
    });

    if (payload) req.write(payload);
    req.end();
  });
}

// ─── HELPERS ──────────────────────────────────────────────────────────────────

function percentile(arr, p) {
  if (!arr.length) return 0;
  const sorted = [...arr].sort((a, b) => a - b);
  return sorted[Math.ceil((p / 100) * sorted.length) - 1];
}

function sep(char = "═", len = 65) {
  return char.repeat(len);
}

function printSection(title) {
  console.log("\n" + sep());
  console.log(`  ${title}`);
  console.log(sep());
}

function printSubSection(title) {
  console.log("\n  " + sep("─", 60));
  console.log(`  🔍 ${title}`);
  console.log("  " + sep("─", 60));
}

function printStats(label, results) {
  const ok = results.filter((r) => r.ok);
  const fail = results.filter((r) => !r.ok);
  const latencies = results.map((r) => r.latency);

  console.log(`\n  ${label}`);
  console.log(`  Total:   ${results.length}`);
  console.log(`  ✅ OK:   ${ok.length} (${((ok.length / results.length) * 100).toFixed(1)}%)`);
  console.log(`  ❌ Fail: ${fail.length}`);
  console.log(
    `  ⏱  p50: ${percentile(latencies, 50)}ms | p95: ${percentile(latencies, 95)}ms | p99: ${percentile(latencies, 99)}ms | max: ${Math.max(...latencies)}ms`
  );

  if (fail.length > 0) {
    console.log(`  Sample errors:`);
    fail.slice(0, 3).forEach((r) => {
      const msg = r.body?.message || r.body?.error || r.error || `HTTP ${r.status}`;
      console.log(`    - [${r.status}] ${msg}`);
    });
  }

  return { ok, fail };
}

async function runInBatches(tasks, batchSize, label) {
  const results = [];
  for (let i = 0; i < tasks.length; i += batchSize) {
    const batch = tasks.slice(i, i + batchSize);
    const batchResults = await Promise.all(batch.map((fn) => fn()));
    results.push(...batchResults);
    process.stdout.write(
      `\r  ${label}: ${Math.min(i + batchSize, tasks.length)}/${tasks.length}`
    );
  }
  console.log();
  return results;
}

// ─── DB VALIDATOR ─────────────────────────────────────────────────────────────
// Queries DB directly and compares against what the API returned.
// PASS = API response matches DB truth.

function compare(label, apiVal, dbVal) {
  const apiStr = String(apiVal ?? "null");
  const dbStr = String(dbVal ?? "null");
  const match = apiStr === dbStr;
  console.log(
    `    ${match ? "✅" : "❌"} ${label.padEnd(35)} API: ${apiStr.padEnd(15)} DB: ${dbStr}`
  );
  return match;
}

// ─── MAIN ─────────────────────────────────────────────────────────────────────

async function main() {
  printSection("EMPLOYEE PERFORMANCE TRACKER — LOAD TEST + DB VALIDATION");

  const pool = new Pool(CONFIG.DB);
  try {
    await pool.query("SELECT 1");
    console.log("\n  ✅ DB connected");
  } catch (e) {
    console.error(`\n  ❌ DB connection failed: ${e.message}`);
    process.exit(1);
  }

  const health = await httpRequest("GET", "/employees");
  if (health.status === 0) {
    console.error("\n  ❌ App not reachable at localhost:8080");
    process.exit(1);
  }
  console.log("  ✅ App is reachable");

  // ── Fetch seeded cycles from DB ──────────────────────────────────────────
  const cycleRows = await pool.query(
    "SELECT id, name, status FROM review_cycles ORDER BY start_date ASC"
  );
  if (!cycleRows.rows.length) {
    console.error("\n  ❌ No cycles found. Did Flyway migration run?");
    process.exit(1);
  }

  const cycles = cycleRows.rows;
  const openCycle = cycles.find((c) => c.status === "OPEN") || cycles[0];
  console.log(`\n  📋 Found ${cycles.length} cycle(s) in DB`);
  cycles.forEach((c) => console.log(`     - ${c.name} [${c.status}] id=${c.id}`));
  console.log(`  📌 Using: ${openCycle.name} (${openCycle.id})`);

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 1 — POST /employees (100 concurrent)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 1 — POST /employees (100 concurrent)");

  const employeeTasks = Array.from({ length: 100 }, (_, i) => () =>
    httpRequest("POST", "/employees", {
      name: `Load Test User ${i + 1}`,
      department: DEPARTMENTS[i % DEPARTMENTS.length],
      role: ROLES[i % ROLES.length],
      joiningDate: "2023-06-15",
    })
  );

  const employeeResults = await runInBatches(employeeTasks, CONFIG.BATCH_SIZE, "Creating employees");
  const { ok: createdEmployees } = printStats("POST /employees", employeeResults);
  const employeeIds = createdEmployees.map((r) => r.body?.data?.id).filter(Boolean);
  console.log(`\n  📋 Collected ${employeeIds.length} employee IDs`);

  if (employeeIds.length < 2) {
    console.error("\n  ❌ Not enough employees. Cannot continue.");
    await pool.end();
    process.exit(1);
  }

  // ── DB Validation: Phase 1 ───────────────────────────────────────────────
  printSubSection("DB Validation — Employee Creation");

  const sampleEmp = createdEmployees[0].body.data;
  const dbEmp = await pool.query(
    "SELECT id, name, department, role, is_active FROM employees WHERE id = $1",
    [sampleEmp.id]
  );

  console.log(`\n  Query: SELECT id, name, department, role, is_active FROM employees WHERE id = '${sampleEmp.id}'`);
  if (dbEmp.rows.length > 0) {
    const row = dbEmp.rows[0];
    compare("name", sampleEmp.name, row.name);
    compare("department", sampleEmp.department, row.department);
    compare("role", sampleEmp.role, row.role);
    compare("isActive", sampleEmp.isActive, row.is_active);
  } else {
    console.log("  ❌ Employee not found in DB!");
  }

  const dbCountRow = await pool.query(
    "SELECT COUNT(*) as cnt FROM employees WHERE is_active = true AND name LIKE 'Load Test User%'"
  );
  console.log(`\n  Query: SELECT COUNT(*) FROM employees WHERE is_active=true AND name LIKE 'Load Test User%'`);
  console.log(`  DB count: ${dbCountRow.rows[0].cnt} | API created: ${employeeIds.length} ${parseInt(dbCountRow.rows[0].cnt) >= employeeIds.length ? "✅" : "❌"}`);

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 2 — POST /goals (concurrent, 1 goal per employee)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 2 — POST /goals (100 concurrent)");
  console.log(`  Creating 1 goal per employee in the open cycle`);
  console.log(`  Goals start as PENDING — will update some to COMPLETED/MISSED later`);

  const goalTitles = [
    "Complete Q1 OKRs",
    "Improve code review turnaround",
    "Deliver project on time",
    "Complete training program",
    "Reduce bug count by 20%",
  ];

  const goalTasks = employeeIds.map((empId, i) => () =>
    httpRequest("POST", "/goals", {
      employeeId: empId,
      cycleId: openCycle.id,
      title: goalTitles[i % goalTitles.length],
      status: "PENDING",
    })
  );

  const goalResults = await runInBatches(goalTasks, CONFIG.BATCH_SIZE, "Creating goals");
  const { ok: createdGoals } = printStats("POST /goals", goalResults);
  const goalIds = createdGoals.map((r) => r.body?.data?.goalId).filter(Boolean);
  console.log(`\n  📋 Collected ${goalIds.length} goal IDs`);

  // ── DB Validation: Goals Created ─────────────────────────────────────────
  printSubSection("DB Validation — Goal Creation");

  const sampleGoal = createdGoals[0]?.body?.data;
  if (sampleGoal) {
    const dbGoal = await pool.query(
      "SELECT id, title, status, employee_id, cycle_id FROM goals WHERE id = $1",
      [sampleGoal.goalId]
    );
    console.log(`\n  Query: SELECT id, title, status, employee_id, cycle_id FROM goals WHERE id = '${sampleGoal.goalId}'`);
    if (dbGoal.rows.length > 0) {
      const row = dbGoal.rows[0];
      compare("title", sampleGoal.title, row.title);
      compare("status", sampleGoal.status, row.status);
      compare("employeeId", sampleGoal.employeeId, row.employee_id);
      compare("cycleId", sampleGoal.cycleId, row.cycle_id);
    }
  }

  const dbGoalCount = await pool.query(
    "SELECT COUNT(*) as cnt FROM goals WHERE cycle_id = $1 AND status = 'PENDING'",
    [openCycle.id]
  );
  console.log(`\n  Query: SELECT COUNT(*) FROM goals WHERE cycle_id='${openCycle.id}' AND status='PENDING'`);
  console.log(`  DB pending goals: ${dbGoalCount.rows[0].cnt} | API created: ${goalIds.length} ${parseInt(dbGoalCount.rows[0].cnt) >= goalIds.length ? "✅" : "❌"}`);

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 3 — PATCH /goals/{id}/status (concurrent status updates)
  // Update first 40 goals: 20 → COMPLETED, 20 → MISSED
  // This is what makes cycle summary show non-zero completed/missed counts
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 3 — PATCH /goals/{id}/status (40 concurrent updates)");
  console.log(`  20 goals → COMPLETED, 20 goals → MISSED`);
  console.log(`  After this, summary endpoint should show completedGoals=20, missedGoals=20`);

  const completedGoalIds = goalIds.slice(0, 20);
  const missedGoalIds = goalIds.slice(20, 40);

  const goalUpdateTasks = [
    ...completedGoalIds.map((id) => () =>
      httpRequest("PATCH", `/goals/${id}/status`, { status: "COMPLETED" })
    ),
    ...missedGoalIds.map((id) => () =>
      httpRequest("PATCH", `/goals/${id}/status`, { status: "MISSED" })
    ),
  ];

  const goalUpdateResults = await runInBatches(goalUpdateTasks, CONFIG.BATCH_SIZE, "Updating goal statuses");
  printStats("PATCH /goals/{id}/status", goalUpdateResults);

  // ── DB Validation: Goal Status Updates ───────────────────────────────────
  printSubSection("DB Validation — Goal Status Updates");

  const dbGoalStatuses = await pool.query(
    `SELECT status, COUNT(*) as cnt FROM goals WHERE cycle_id = $1 GROUP BY status ORDER BY status`,
    [openCycle.id]
  );
  console.log(`\n  Query: SELECT status, COUNT(*) FROM goals WHERE cycle_id='${openCycle.id}' GROUP BY status`);
  console.log(`\n  ${"Status".padEnd(15)} ${"Count".padEnd(10)}`);
  console.log(`  ${"─".repeat(25)}`);
  dbGoalStatuses.rows.forEach((r) => {
    console.log(`  ${r.status.padEnd(15)} ${r.cnt}`);
  });

  const completedInDb = dbGoalStatuses.rows.find((r) => r.status === "COMPLETED");
  const missedInDb = dbGoalStatuses.rows.find((r) => r.status === "MISSED");

  compare("COMPLETED count", 20, parseInt(completedInDb?.cnt || 0));
  compare("MISSED count", 20, parseInt(missedInDb?.cnt || 0));

  // ── Verify a specific goal's status ──────────────────────────────────────
  if (completedGoalIds.length > 0) {
    const dbSingleGoal = await pool.query(
      "SELECT id, status FROM goals WHERE id = $1",
      [completedGoalIds[0]]
    );
    console.log(`\n  Query: SELECT status FROM goals WHERE id = '${completedGoalIds[0]}'`);
    if (dbSingleGoal.rows.length > 0) {
      compare("specific goal status", "COMPLETED", dbSingleGoal.rows[0].status);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 4 — POST /reviews (100 concurrent)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 4 — POST /reviews (100 concurrent, same cycle)");
  console.log(`  Tests: HikariCP pool, atomic running total update, employee_cycle_stats upsert`);

  const reviewTasks = employeeIds.map((empId, i) => {
    const reviewerId = employeeIds[(i + 1) % employeeIds.length];
    const rating = (i % 5) + 1;
    return () =>
      httpRequest("POST", "/reviews", {
        employeeId: empId,
        cycleId: openCycle.id,
        reviewerId: reviewerId,
        rating: rating,
        notes: `Load test review ${i + 1}. Rating: ${rating}/5.`,
      });
  });

  const reviewStart = Date.now();
  const reviewResults = await runInBatches(reviewTasks, CONFIG.BATCH_SIZE, "Submitting reviews");
  const reviewDuration = Date.now() - reviewStart;
  const { ok: okReviews } = printStats("POST /reviews", reviewResults);
  console.log(`\n  ⚡ Throughput: ${Math.round((reviewResults.length / reviewDuration) * 1000)} reviews/sec`);

  // ── DB Validation: Reviews + Running Totals ───────────────────────────────
  printSubSection("DB Validation — Reviews & Running Totals");

  const cycleCheck = await pool.query(
    `SELECT
       rc.name,
       rc.review_count        as stored_count,
       rc.total_rating        as stored_total,
       CASE WHEN rc.review_count > 0
            THEN ROUND(rc.total_rating::numeric / rc.review_count, 2)
            ELSE 0 END        as stored_avg,
       COUNT(pr.id)           as actual_count,
       COALESCE(SUM(pr.rating), 0) as actual_total
     FROM review_cycles rc
     LEFT JOIN performance_reviews pr ON pr.cycle_id = rc.id
     WHERE rc.id = $1
     GROUP BY rc.id, rc.name, rc.review_count, rc.total_rating`,
    [openCycle.id]
  );

  console.log(`\n  Query: running totals vs actual reviews aggregate for cycle '${openCycle.id}'`);
  if (cycleCheck.rows.length > 0) {
    const row = cycleCheck.rows[0];
    const countMatch = parseInt(row.stored_count) === parseInt(row.actual_count);
    const totalMatch = parseInt(row.stored_total) === parseInt(row.actual_total);
    console.log(`\n  ${"Field".padEnd(20)} ${"Stored (running)".padEnd(20)} ${"Actual (aggregate)".padEnd(20)} Match?`);
    console.log(`  ${"─".repeat(70)}`);
    console.log(`  ${"review_count".padEnd(20)} ${row.stored_count.toString().padEnd(20)} ${row.actual_count.toString().padEnd(20)} ${countMatch ? "✅" : "❌ RACE CONDITION!"}`);
    console.log(`  ${"total_rating".padEnd(20)} ${row.stored_total.toString().padEnd(20)} ${row.actual_total.toString().padEnd(20)} ${totalMatch ? "✅" : "❌ RACE CONDITION!"}`);
    console.log(`  ${"avg_rating".padEnd(20)} ${row.stored_avg.toString().padEnd(20)} ${"(computed above)".padEnd(20)}`);

    if (countMatch && totalMatch) {
      console.log(`\n  ✅ RUNNING TOTALS ACCURATE — atomic updates working, no race condition`);
    } else {
      console.log(`\n  ❌ MISMATCH — race condition detected in running total updates`);
    }
  }

  // ── DB Validation: employee_cycle_stats ──────────────────────────────────
  const ecsCheck = await pool.query(
    `SELECT
       COUNT(*)          as row_count,
       AVG(avg_rating)   as avg_of_avgs,
       MIN(avg_rating)   as min_rating,
       MAX(avg_rating)   as max_rating,
       SUM(review_count) as total_reviews
     FROM employee_cycle_stats
     WHERE cycle_id = $1`,
    [openCycle.id]
  );

  console.log(`\n  Query: SELECT COUNT(*), AVG/MIN/MAX(avg_rating), SUM(review_count) FROM employee_cycle_stats WHERE cycle_id='${openCycle.id}'`);
  const ecsRow = ecsCheck.rows[0];
  console.log(`\n  Rows in employee_cycle_stats: ${ecsRow.row_count}`);
  console.log(`  Avg of avg_ratings:           ${parseFloat(ecsRow.avg_of_avgs || 0).toFixed(2)}`);
  console.log(`  Min avg_rating:               ${ecsRow.min_rating}`);
  console.log(`  Max avg_rating:               ${ecsRow.max_rating}`);
  console.log(`  Total reviews tracked:        ${ecsRow.total_reviews}`);
  console.log(`  ${parseInt(ecsRow.row_count) > 0 ? "✅ employee_cycle_stats populated" : "❌ employee_cycle_stats EMPTY"}`);

  // ── Validate a specific review response matches DB ────────────────────────
  const sampleReview = okReviews[0]?.body?.data;
  if (sampleReview) {
    const dbReview = await pool.query(
      `SELECT pr.id, pr.rating, pr.employee_id, pr.cycle_id, pr.reviewer_id
       FROM performance_reviews pr WHERE pr.id = $1`,
      [sampleReview.reviewId]
    );
    console.log(`\n  Query: SELECT rating, employee_id, cycle_id FROM performance_reviews WHERE id='${sampleReview.reviewId}'`);
    if (dbReview.rows.length > 0) {
      const row = dbReview.rows[0];
      compare("rating", sampleReview.rating, parseInt(row.rating));
      compare("employeeId", sampleReview.employeeId, row.employee_id);
      compare("cycleId", sampleReview.cycleId, row.cycle_id);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 5 — GET /employees/{id}/reviews (30 concurrent)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 5 — GET /employees/{id}/reviews (30 concurrent)");

  const reviewReadTasks = employeeIds.slice(0, 30).map((id) => () =>
    httpRequest("GET", `/employees/${id}/reviews`)
  );

  const reviewReadResults = await runInBatches(reviewReadTasks, CONFIG.BATCH_SIZE, "Fetching reviews");
  printStats("GET /employees/{id}/reviews", reviewReadResults);

  // ── DB Validation: Employee Reviews ──────────────────────────────────────
  printSubSection("DB Validation — Employee Reviews");

  const sampleEmpReviews = reviewReadResults.find((r) => r.ok && r.body?.data);
  if (sampleEmpReviews) {
    const apiReviews = sampleEmpReviews.body.data;
    const empId = apiReviews[0]?.employeeId || employeeIds[0];

    const dbReviews = await pool.query(
      `SELECT COUNT(*) as cnt, AVG(rating) as avg
       FROM performance_reviews WHERE employee_id = $1 AND cycle_id = $2`,
      [empId, openCycle.id]
    );

    console.log(`\n  Query: SELECT COUNT(*), AVG(rating) FROM performance_reviews WHERE employee_id='${empId}'`);
    const dbRow = dbReviews.rows[0];
    compare("review count for employee", Array.isArray(apiReviews) ? apiReviews.length : 0, parseInt(dbRow.cnt));

    if (Array.isArray(apiReviews) && apiReviews.length > 0) {
      const firstReview = apiReviews[0];
      console.log(`\n  Sample review fields from API response:`);
      console.log(`    reviewId:    ${firstReview.reviewId}`);
      console.log(`    cycleId:     ${firstReview.cycleId}`);
      console.log(`    cycleName:   ${firstReview.cycleName}`);
      console.log(`    reviewerId:  ${firstReview.reviewerId}`);
      console.log(`    reviewerName:${firstReview.reviewerName}`);
      console.log(`    rating:      ${firstReview.rating}`);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 6 — GET /cycles/{id}/summary (100 concurrent)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 6 — GET /cycles/{id}/summary (100 concurrent)");
  console.log(`  O(1) running totals — p99 should be < 200ms`);
  console.log(`  completedGoals and missedGoals should now be non-zero`);

  const summaryTasks = Array.from({ length: 100 }, () => () =>
    httpRequest("GET", `/cycles/${openCycle.id}/summary`)
  );

  const summaryResults = await runInBatches(summaryTasks, CONFIG.BATCH_SIZE, "Fetching summaries");
  printStats("GET /cycles/{id}/summary", summaryResults);

  const goodSummary = summaryResults.find((r) => r.ok && r.body?.data);
  if (goodSummary) {
    const s = goodSummary.body.data;
    console.log(`\n  📊 API Response:`);
    console.log(`     cycleName:                ${s.cycleName}`);
    console.log(`     averageRating:            ${s.averageRating}`);
    console.log(`     topPerformerName:         ${s.topPerformerName || "N/A"}`);
    console.log(`     topPerformerAvgRating:    ${s.topPerformerAverageRating || "N/A"}`);
    console.log(`     completedGoals:           ${s.completedGoals}`);
    console.log(`     missedGoals:              ${s.missedGoals}`);
  }

  // ── DB Validation: Summary ────────────────────────────────────────────────
  printSubSection("DB Validation — Cycle Summary Cross-Check");

  const dbSummary = await pool.query(
    `SELECT
       rc.name                                     as cycle_name,
       ROUND(rc.total_rating::numeric / NULLIF(rc.review_count,0), 2) as avg_rating,
       rc.review_count,
       (SELECT COUNT(*) FROM goals WHERE cycle_id = rc.id AND status = 'COMPLETED') as completed_goals,
       (SELECT COUNT(*) FROM goals WHERE cycle_id = rc.id AND status = 'MISSED')    as missed_goals,
        (SELECT e.name
             FROM employee_cycle_stats ecs
             JOIN employees e ON e.id = ecs.employee_id
             WHERE ecs.cycle_id = rc.id
             ORDER BY ecs.avg_rating DESC, e.name ASC
             LIMIT 1)                                   as top_performer_name,
        (SELECT ecs.avg_rating
             FROM employee_cycle_stats ecs
             JOIN employees e ON e.id = ecs.employee_id
             WHERE ecs.cycle_id = rc.id
             ORDER BY ecs.avg_rating DESC, e.name ASC
             LIMIT 1)                                   as top_performer_rating
     FROM review_cycles rc
     WHERE rc.id = $1`,
    [openCycle.id]
  );

  console.log(`\n  Query: full summary computation from DB for cycle '${openCycle.id}'`);

  if (dbSummary.rows.length > 0 && goodSummary) {
    const db = dbSummary.rows[0];
    const api = goodSummary.body.data;

    console.log(`\n  ${"Field".padEnd(30)} ${"API Value".padEnd(25)} ${"DB Value".padEnd(25)} Match?`);
    console.log(`  ${"─".repeat(85)}`);

    compare("cycleName", api.cycleName, db.cycle_name);
    compare("averageRating", api.averageRating, parseFloat(db.avg_rating || 0));
    compare("completedGoals", api.completedGoals, parseInt(db.completed_goals));
    compare("missedGoals", api.missedGoals, parseInt(db.missed_goals));
    compare("topPerformerName", api.topPerformerName, db.top_performer_name);
    compare(
      "topPerformerAvgRating",
      api.topPerformerAverageRating,
      db.top_performer_rating ? parseFloat(db.top_performer_rating) : null
    );

    if (parseInt(db.completed_goals) === 0 && parseInt(db.missed_goals) === 0) {
      console.log(`\n  ⚠️  Goals are 0 — goal updates may not have propagated to summary`);
      console.log(`      Check GoalService — does updateGoalStatus recalculate summary?`);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 7 — GET /employees?department=X&minRating=Y (concurrent)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 7 — GET /employees?department=X&minRating=Y");
  console.log(`  Tests employee_cycle_stats pre-computed avg — no live aggregation`);

  const filterTasks = DEPARTMENTS.flatMap((dept) =>
    [1, 2, 3].map((rating) => () =>
      httpRequest("GET", `/employees?department=${encodeURIComponent(dept)}&minRating=${rating}`)
    )
  );

  const filterResults = await runInBatches(filterTasks, CONFIG.BATCH_SIZE, "Filtering employees");
  printStats("GET /employees?department&minRating", filterResults);

  // ── DB Validation: Filter ─────────────────────────────────────────────────
  printSubSection("DB Validation — Department Filter Cross-Check");

  const testDept = "Engineering";
  const testMinRating = 1.0;

  const apiFilter = await httpRequest(
    "GET",
    `/employees?department=${encodeURIComponent(testDept)}&minRating=${testMinRating}`
  );

  const dbFilter = await pool.query(
    `SELECT e.id, e.name, e.department, ecs.avg_rating
     FROM employees e
     JOIN employee_cycle_stats ecs ON e.id = ecs.employee_id
     WHERE e.is_active = true
       AND LOWER(e.department) = LOWER($1)
       AND ecs.avg_rating >= $2
     ORDER BY ecs.avg_rating DESC, e.name ASC`,
    [testDept, testMinRating]
  );

  console.log(`\n  Query: employees in '${testDept}' with avg_rating >= ${testMinRating}`);
  console.log(`  SQL: JOIN employee_cycle_stats WHERE department='${testDept}' AND avg_rating>=${testMinRating}`);

  const apiCount = Array.isArray(apiFilter.body?.data) ? apiFilter.body.data.length : 0;
  const dbCount = dbFilter.rows.length;

  console.log(`\n  API returned: ${apiCount} employees`);
  console.log(`  DB query:     ${dbCount} employees`);
  console.log(`  Match: ${apiCount === dbCount ? "✅" : "❌ MISMATCH"}`);

  if (dbFilter.rows.length > 0) {
    console.log(`\n  Top 3 from DB:`);
    dbFilter.rows.slice(0, 3).forEach((r) => {
      console.log(`    - ${r.name.padEnd(25)} avg_rating: ${r.avg_rating}`);
    });
  }

  if (apiFilter.body?.data?.length > 0) {
    console.log(`\n  Top 3 from API:`);
    apiFilter.body.data.slice(0, 3).forEach((r) => {
      console.log(`    - ${(r.employeeName || r.name || "?").padEnd(25)} avg_rating: ${r.averageRating}`);
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 8 — 360 FEEDBACK (different reviewers, same employee+cycle)
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 8 — 360-DEGREE FEEDBACK EDGE CASE");
  console.log(`  Requirement says: handle employee reviewed multiple times in a cycle`);
  console.log(`  2 different reviewers review the SAME employee — both should succeed`);

  const targetEmployee = employeeIds[0];
  const reviewer1 = employeeIds[2];
  const reviewer2 = employeeIds[3];

  const r1 = await httpRequest("POST", "/reviews", {
    employeeId: targetEmployee,
    cycleId: openCycle.id,
    reviewerId: reviewer1,
    rating: 5,
    notes: "360 review from reviewer 1",
  });

  const r2 = await httpRequest("POST", "/reviews", {
    employeeId: targetEmployee,
    cycleId: openCycle.id,
    reviewerId: reviewer2,
    rating: 3,
    notes: "360 review from reviewer 2",
  });

  console.log(`\n  Reviewer 1 [${r1.status}]: ${r1.body?.message || r1.body?.error}`);
  console.log(`  Reviewer 2 [${r2.status}]: ${r2.body?.message || r2.body?.error}`);

  const bothSucceeded = r1.ok && r2.ok;
  console.log(`\n  ${bothSucceeded ? "✅ 360-DEGREE FEEDBACK WORKS — both reviewers accepted" : "❌ 360-DEGREE FEEDBACK FAILED"}`);

  const db360 = await pool.query(
    `SELECT pr.reviewer_id, e.name as reviewer_name, pr.rating
     FROM performance_reviews pr
     JOIN employees e ON e.id = pr.reviewer_id
     WHERE pr.employee_id = $1 AND pr.cycle_id = $2
     ORDER BY pr.submitted_at`,
    [targetEmployee, openCycle.id]
  );

  console.log(`\n  Query: SELECT reviewer_id, rating FROM performance_reviews WHERE employee_id='${targetEmployee}'`);
  console.log(`  Reviews found in DB for this employee:`);
  db360.rows.forEach((row) => {
    console.log(`    - reviewer: ${row.reviewer_name.padEnd(30)} rating: ${row.rating}`);
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // PHASE 9 — DUPLICATE GUARD
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("PHASE 9 — DUPLICATE REVIEW GUARD (5 identical concurrent)");
  console.log(`  Same reviewer + employee + cycle — only 1 should succeed`);

  const freshEmp = await httpRequest("POST", "/employees", {
    name: "Dup Test Employee",
    department: "Engineering",
    role: "Engineer",
    joiningDate: "2023-06-15",
  });
  const freshReviewer = await httpRequest("POST", "/employees", {
    name: "Dup Test Reviewer",
    department: "Engineering",
    role: "Manager",
    joiningDate: "2023-06-15",
  });

  if (freshEmp.ok && freshReviewer.ok) {
    const dupEmpId = freshEmp.body.data.id;
    const dupReviewerId = freshReviewer.body.data.id;

    console.log(`\n  Fresh employee:  ${dupEmpId}`);
    console.log(`  Fresh reviewer:  ${dupReviewerId}`);

    const dupResults = await Promise.all(
      Array.from({ length: 5 }, (_, i) => () =>
        httpRequest("POST", "/reviews", {
          employeeId: dupEmpId,
          cycleId: openCycle.id,
          reviewerId: dupReviewerId,
          rating: 4,
          notes: `Duplicate attempt ${i + 1}`,
        })
      ).map((fn) => fn())
    );

    const dupOk = dupResults.filter((r) => r.ok).length;
    const dupFail = dupResults.filter((r) => !r.ok).length;

    console.log(`\n  Results:`);
    dupResults.forEach((r, i) => {
      const msg = r.body?.message || r.body?.error || `HTTP ${r.status}`;
      console.log(`  Request ${i + 1}: [${r.status}] ${msg}`);
    });
    console.log(`\n  ✅ Succeeded: ${dupOk} (expected: 1)`);
    console.log(`  ❌ Rejected:  ${dupFail} (expected: 4)`);

    if (dupOk === 1 && dupFail === 4) {
      console.log(`\n  ✅ DUPLICATE GUARD WORKING PERFECTLY`);
    } else if (dupOk === 0) {
      console.log(`\n  ⚠️  All rejected — already reviewed or timing issue`);
    } else {
      console.log(`\n  ❌ DUPLICATE GUARD FAILED — ${dupOk} duplicates got through`);
    }

    const dbDupCheck = await pool.query(
      `SELECT COUNT(*) as cnt FROM performance_reviews
       WHERE employee_id = $1 AND reviewer_id = $2 AND cycle_id = $3`,
      [dupEmpId, dupReviewerId, openCycle.id]
    );
    console.log(`\n  Query: COUNT reviews WHERE employee+reviewer+cycle = this combination`);
    console.log(`  DB count: ${dbDupCheck.rows[0].cnt} ${dbDupCheck.rows[0].cnt === "1" ? "✅ exactly 1" : "❌ should be 1"}`);
  }

    // ═══════════════════════════════════════════════════════════════════════════
    // PESSIMISTIC LOCKING VERIFICATION
    // Fires 50 reviews for the SAME employee simultaneously.
    // Without pessimistic locking on review_cycles and employee_cycle_stats,
    // the running totals (total_rating, review_count) would be wrong —
    // concurrent transactions would read stale values and overwrite each other.
    // With locking: each transaction waits its turn → totals always accurate.
    // ═══════════════════════════════════════════════════════════════════════════
    printSection("PESSIMISTIC LOCKING VERIFICATION");
    console.log(`  Creating 50 reviewers to review the SAME employee simultaneously`);
    console.log(`  If locking works: stored running totals = actual DB aggregate`);
    console.log(`  If locking fails: stored != actual (lost updates / dirty reads)`);

    // Create a dedicated target employee
    const lockTarget = await httpRequest("POST", "/employees", {
      name: "Lock Test Target",
      department: "Engineering",
      role: "Engineer",
      joiningDate: "2023-01-01",
    });

    // Create 50 unique reviewers
    console.log(`\n  Creating 50 unique reviewers...`);
    const lockReviewerTasks = Array.from({ length: 50 }, (_, i) => () =>
      httpRequest("POST", "/employees", {
        name: `Lock Reviewer ${i + 1}`,
        department: "Engineering",
        role: "Manager",
        joiningDate: "2023-01-01",
      })
    );
    const lockReviewerResults = await runInBatches(lockReviewerTasks, CONFIG.BATCH_SIZE, "Creating reviewers");
    const lockReviewerIds = lockReviewerResults
      .filter((r) => r.ok)
      .map((r) => r.body?.data?.id)
      .filter(Boolean);

    if (!lockTarget.ok || lockReviewerIds.length < 10) {
      console.log(`  ❌ Could not create test data for locking test`);
    } else {
      const lockTargetId = lockTarget.body.data.id;
      console.log(`  Target employee: ${lockTargetId}`);
      console.log(`  Reviewers created: ${lockReviewerIds.length}`);

      // Snapshot BEFORE — what are the current running totals?
      const beforeLock = await pool.query(
        `SELECT review_count, total_rating FROM review_cycles WHERE id = $1`,
        [openCycle.id]
      );
      const beforeCount = parseInt(beforeLock.rows[0].review_count);
      const beforeTotal = parseInt(beforeLock.rows[0].total_rating);
      console.log(`\n  Before burst — cycle running totals:`);
      console.log(`    review_count: ${beforeCount}`);
      console.log(`    total_rating: ${beforeTotal}`);

      // Fire ALL 50 reviews simultaneously — true concurrency burst
      // Each uses a different reviewer so no duplicate rejection
      console.log(`\n  Firing ${lockReviewerIds.length} reviews simultaneously (true concurrent burst)...`);
      const lockStart = Date.now();
      const lockRating = 4; // all same rating so we can predict totals exactly

      const lockResults = await Promise.all(
        lockReviewerIds.map((reviewerId) => () =>
          httpRequest("POST", "/reviews", {
            employeeId: lockTargetId,
            cycleId: openCycle.id,
            reviewerId: reviewerId,
            rating: lockRating,
            notes: "Pessimistic lock test",
          })
        ).map((fn) => fn())
      );

      const lockDuration = Date.now() - lockStart;
      const lockOk = lockResults.filter((r) => r.ok).length;
      const lockFail = lockResults.filter((r) => !r.ok).length;
      console.log(`  Duration: ${lockDuration}ms | OK: ${lockOk} | Failed: ${lockFail}`);

      // Wait briefly for any in-flight transactions to commit
      await new Promise((r) => setTimeout(r, 500));

      // Snapshot AFTER
      const afterLock = await pool.query(
        `SELECT review_count, total_rating FROM review_cycles WHERE id = $1`,
        [openCycle.id]
      );
      const afterCount = parseInt(afterLock.rows[0].review_count);
      const afterTotal = parseInt(afterLock.rows[0].total_rating);

      // What the ACTUAL DB says (ground truth via aggregate)
      const actualLock = await pool.query(
        `SELECT COUNT(*) as cnt, COALESCE(SUM(rating),0) as total
         FROM performance_reviews
         WHERE cycle_id = $1`,
        [openCycle.id]
      );
      const actualCount = parseInt(actualLock.rows[0].cnt);
      const actualTotal = parseInt(actualLock.rows[0].total);

      // Expected: before + however many succeeded
      const expectedCountIncrease = lockOk;
      const expectedTotalIncrease = lockOk * lockRating;

      console.log(`\n  ${"Metric".padEnd(35)} ${"Expected".padEnd(15)} ${"Stored".padEnd(15)} ${"Actual DB".padEnd(15)} Match?`);
      console.log(`  ${"─".repeat(85)}`);

      const countStored = afterCount - beforeCount;
      const totalStored = afterTotal - beforeTotal;
      const countStoredMatch = countStored === expectedCountIncrease;
      const totalStoredMatch = totalStored === expectedTotalIncrease;
      const countActualMatch = afterCount === actualCount;
      const totalActualMatch = afterTotal === actualTotal;

      console.log(`  ${"review_count increase".padEnd(35)} ${expectedCountIncrease.toString().padEnd(15)} ${countStored.toString().padEnd(15)} ${(actualCount - beforeCount).toString().padEnd(15)} ${countStoredMatch && countActualMatch ? "✅" : "❌ LOCK FAILURE"}`);
      console.log(`  ${"total_rating increase".padEnd(35)} ${expectedTotalIncrease.toString().padEnd(15)} ${totalStored.toString().padEnd(15)} ${(actualTotal - beforeTotal).toString().padEnd(15)} ${totalStoredMatch && totalActualMatch ? "✅" : "❌ LOCK FAILURE"}`);
      console.log(`  ${"stored == actual (no lost updates)".padEnd(35)} ${"".padEnd(15)} ${afterCount.toString().padEnd(15)} ${actualCount.toString().padEnd(15)} ${countActualMatch && totalActualMatch ? "✅" : "❌ LOST UPDATE DETECTED"}`);

      // Check employee_cycle_stats for this specific employee
      const ecsLock = await pool.query(
        `SELECT ecs.review_count, ecs.total_rating, ecs.avg_rating
         FROM employee_cycle_stats ecs
         WHERE ecs.employee_id = $1 AND ecs.cycle_id = $2`,
        [lockTargetId, openCycle.id]
      );

      if (ecsLock.rows.length > 0) {
        const ecs = ecsLock.rows[0];
        const ecsCountMatch = parseInt(ecs.review_count) === lockOk;
        const ecsTotalMatch = parseInt(ecs.total_rating) === lockOk * lockRating;
        const ecsExpectedAvg = lockOk > 0 ? lockRating : 0;

        console.log(`\n  employee_cycle_stats for lock target employee:`);
        console.log(`  ${"Field".padEnd(20)} ${"Expected".padEnd(15)} ${"Stored".padEnd(15)} Match?`);
        console.log(`  ${"─".repeat(55)}`);
        console.log(`  ${"review_count".padEnd(20)} ${lockOk.toString().padEnd(15)} ${ecs.review_count.toString().padEnd(15)} ${ecsCountMatch ? "✅" : "❌"}`);
        console.log(`  ${"total_rating".padEnd(20)} ${(lockOk * lockRating).toString().padEnd(15)} ${ecs.total_rating.toString().padEnd(15)} ${ecsTotalMatch ? "✅" : "❌"}`);
        console.log(`  ${"avg_rating".padEnd(20)} ${ecsExpectedAvg.toString().padEnd(15)} ${ecs.avg_rating.toString().padEnd(15)} ${parseFloat(ecs.avg_rating) === ecsExpectedAvg ? "✅" : "❌"}`);
      }

      const allLockChecks = countStoredMatch && totalStoredMatch && countActualMatch && totalActualMatch;
      if (allLockChecks) {
        console.log(`\n  ✅ PESSIMISTIC LOCKING CONFIRMED`);
        console.log(`     ${lockOk} concurrent writes to same cycle — zero lost updates`);
        console.log(`     Running totals match actual aggregate exactly`);
      } else {
        console.log(`\n  ❌ LOCKING ISSUE DETECTED`);
        console.log(`     Stored running totals differ from actual DB aggregate`);
        console.log(`     This means concurrent transactions overwrote each other`);
      }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FINAL DB HEALTH CHECK
    // ═══════════════════════════════════════════════════════════════════════════
    printSection("FINAL DB HEALTH CHECK");

    const checks = await Promise.all([
      pool.query("SELECT COUNT(*) as cnt FROM review_cycles WHERE review_count < 0 OR total_rating < 0"),
      pool.query("SELECT COUNT(*) as cnt FROM employee_cycle_stats WHERE avg_rating < 0 OR avg_rating > 5"),
      pool.query("SELECT COUNT(*) as cnt FROM performance_reviews WHERE rating < 1 OR rating > 5"),
      pool.query("SELECT COUNT(*) as cnt FROM goals WHERE status NOT IN ('PENDING','COMPLETED','MISSED')"),
      pool.query("SELECT COUNT(*) as cnt FROM performance_reviews WHERE employee_id = reviewer_id"),
    ]);

    const healthItems = [
      ["Negative running totals in review_cycles",        checks[0].rows[0].cnt],
      ["Invalid avg_rating in employee_cycle_stats",      checks[1].rows[0].cnt],
      ["Invalid ratings (not 1-5) in performance_reviews",checks[2].rows[0].cnt],
      ["Invalid goal statuses",                           checks[3].rows[0].cnt],
      ["Self-reviews (employee reviewed themselves)",     checks[4].rows[0].cnt],
    ];

    console.log(`\n  ${"Check".padEnd(50)} ${"Count".padEnd(8)} Status`);
    console.log(`  ${"─".repeat(70)}`);
    healthItems.forEach(([label, cnt]) => {
      console.log(`  ${label.padEnd(50)} ${cnt.padEnd(8)} ${cnt === "0" ? "✅" : "❌ ISSUE"}`);
    });

  // ═══════════════════════════════════════════════════════════════════════════
  // FINAL VERDICT
  // ═══════════════════════════════════════════════════════════════════════════
  printSection("FINAL VERDICT");

  const allResults = [
    ...employeeResults, ...goalResults, ...goalUpdateResults,
    ...reviewResults, ...reviewReadResults, ...summaryResults, ...filterResults,
  ];
  const totalOk = allResults.filter((r) => r.ok).length;
  const totalFail = allResults.filter((r) => !r.ok).length;
  const allLatencies = allResults.map((r) => r.latency);

  console.log(`
  📊 TOTALS
     Requests sent:       ${allResults.length}
     Successful:          ${totalOk} (${((totalOk / allResults.length) * 100).toFixed(1)}%)
     Failed:              ${totalFail}

  ⏱️  OVERALL LATENCY
     p50:  ${percentile(allLatencies, 50)}ms
     p95:  ${percentile(allLatencies, 95)}ms
     p99:  ${percentile(allLatencies, 99)}ms
     max:  ${Math.max(...allLatencies)}ms

  🎯 ENDPOINTS TESTED
     POST /employees              ✓ 100 concurrent
     POST /goals                  ✓ 100 concurrent
     PATCH /goals/{id}/status     ✓ 40 concurrent
     POST /reviews                ✓ 100 concurrent
     GET  /employees/{id}/reviews ✓ 30 concurrent
     GET  /cycles/{id}/summary    ✓ 100 concurrent
     GET  /employees?dept&rating  ✓ 15 concurrent
     360-degree feedback          ✓ edge case tested
     Duplicate review guard       ✓ 5 simultaneous

  📌 DB VALIDATIONS PERFORMED
     ✓ Employee fields match DB after creation
     ✓ Goal fields match DB after creation
     ✓ Goal status updates verified in DB
     ✓ Running totals (stored) vs aggregate (actual) compared
     ✓ employee_cycle_stats population verified
     ✓ Single review fields cross-checked against DB
     ✓ Filter endpoint results compared with direct DB query
     ✓ Summary response fields compared with DB computation
     ✓ 360-feedback reviews verified in DB
     ✓ Duplicate guard count verified in DB
     ✓ Data integrity checks (no negatives, no self-reviews)
  `);

  await pool.end();
}

main().catch((e) => {
  console.error("Load test crashed:", e);
  process.exit(1);
});