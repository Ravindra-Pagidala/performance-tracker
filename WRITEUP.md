# System Design Write-up

## How I'd Scale This for 500 Concurrent Managers

Most of the scaling work is already done at the schema level, before we touch infrastructure.

When 500 managers hit their dashboards simultaneously, the expensive operation is computing average ratings per cycle. The naive `SELECT AVG(rating) FROM performance_reviews WHERE cycle_id = ?` scans every review row on every request. At 100k reviews with 500 concurrent managers, that's 50 million row reads per second on the same table.

So instead, I maintain `total_rating` and `review_count` directly on `review_cycles`, updated atomically in the same transaction as every review submission. Average becomes `total_rating / review_count` — two integers, one division, O(1) regardless of data volume. This is how financial ledger systems work: banks don't recompute your balance by summing every transaction since account opening. They keep a running total.

I also built an `employee_cycle_stats` table storing pre-computed average ratings per employee per cycle. Without it, the filter endpoint runs a live `JOIN + GROUP BY + HAVING` aggregate on every dashboard load. With it, that's a simple indexed join — no aggregation at query time — returning in under 30ms under concurrent load.

For infrastructure: the app is fully stateless, so three Spring Boot instances behind a load balancer is trivial. HikariCP pool at 20 connections per instance gives 60 total, within PostgreSQL's limit. All GET requests route to a read replica; primary handles writes only. During review season reads outnumber writes roughly 20:1, so this split nearly doubles throughput without changing application code.

---

## What I'd Do if the Summary Endpoint Gets Slow at 100k+ Reviews

First — `EXPLAIN ANALYZE` on the actual query. Optimizing without measuring is guesswork.

With running totals in place, there's no aggregation happening at read time. Top-performer lookup uses a composite index on `(cycle_id, avg_rating DESC)` — PostgreSQL reads the first index entry for the cycle and stops. If it still degrades, Redis caching is the next step. Closed cycles are immutable — once closed, the summary never changes. Cache those with no TTL, invalidate only on status change. Open cycles get a 60-second TTL. Stale averages by a minute are completely acceptable on an HR dashboard.

---

## Where I'd Add Caching

I evaluate each endpoint against three questions: read frequency, write frequency, and how much stale data hurts.

**Summary endpoint** — cache it. High reads, low writes, 60-second staleness is fine. Closed cycles cached forever in Redis, open cycles with 60-second TTL.

**Department filter** — cache it. Department composition changes rarely. 10-minute TTL, invalidate on new employee creation in that department.

**Employee reviews** — don't cache. A manager submits a review and immediately checks it. Stale cache makes them think it failed, they retry, hit a 409. The indexed query is already fast enough that caching adds complexity without benefit.

Redis over Spring's in-memory `@Cacheable` specifically because multiple app instances each maintain isolated caches — one serves fresh data while another serves stale. Redis is a shared external cache all instances read from. That consistency matters when managers are actively discussing reviews in real time.
