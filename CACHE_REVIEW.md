# SimpleCache review

`ConcurrentHashMap` is a reasonable starting point for this workload. The main deployment blockers
are unbounded retention and wall-clock-based expiration. Thread-safe storage alone does not provide
the lifecycle or API guarantees expected from a cache.

## 1. Expired entries are never removed

`get()` returns `null` for an expired entry but leaves its key, wrapper and value in the map.
Entries that are never read again also remain there indefinitely. With a stream of new keys,
retained memory keeps growing even though the TTL is only one minute. For example, 100 new keys
per second leaves 8.64 million mappings after a day. This can cause frequent GC pauses and
eventually an out-of-memory failure.

Expiry needs physical reclamation: lazy removal on reads plus maintenance that also visits entries
that receive no further reads, or a cache library that already handles this. A full-map scan on every
`get()` would turn the read path into O(n) work and is unsuitable for the stated load.

If implementing lazy cleanup, it must not delete a concurrent replacement:

1. Reader A observes expired entry E1.
2. Writer B replaces the same key with fresh entry E2.
3. A calls `remove(key)` and accidentally removes E2.

Use conditional removal, `cache.remove(key, entry)`, for the entry actually inspected. Apply the
same rule to background cleanup. A custom implementation needing strict entry identity should
use an entry type with identity equality: this `data class` has structural equality. Equivalent
replacements with the same timestamp and value compare equal, although they also have the same
expiry under this TTL model.

## 2. TTL does not bound memory, even with cleanup

Cleaning up expired entries still leaves an unlimited number of live entries during the one-minute
window. A write burst, high key cardinality or a few large values can exhaust the available heap.

Set a capacity or weight limit and an eviction policy based on the application's memory budget.
An entry-count limit is useful for similarly sized values; a weight limit is more appropriate when
sizes vary. Do not enforce the limit with `if (size() < limit) put(...)`: concurrent writers can all
pass the check. Capacity enforcement needs to be part of the cache's coordinated eviction mechanism.

## 3. Expiration uses an adjustable wall clock

`System.currentTimeMillis()` measures wall-clock time, which can move after clock synchronization
or an administrative change. A backward adjustment can keep entries alive too long, including
entries whose calculated age becomes negative. A forward jump can expire many entries early,
causing a burst of misses and extra backend load.

For an in-process TTL, record a monotonic ticker such as `System.nanoTime()` and compare elapsed
time in matching units: `nowNanos - writtenAtNanos < ttlNanos`. Inject the ticker to test expiry
without sleeping. Keep the one-minute default if that is the requirement, but make the duration
configurable and validate it. This cache implements expiry after a write; reads do not refresh TTL.
That is a policy choice, not a bug.

## 4. `size()` does not mean the number of usable entries

It counts stored mappings, including expired entries that `get()` will not return. Even in a single
thread, it can report a positive count when every lookup misses. During concurrent writes it also
represents a transient observation, not an atomic snapshot for controlling application logic.

Define the contract explicitly. An estimated stored-entry count can be useful as a metric, but it
should be named and documented accordingly. An exact live-entry count requires expiry processing
and a consistency strategy; scanning the entire map on each call is not an attractive hot-path fix.

## 5. Null and mutable-value contracts are unclear

The unconstrained type parameters allow nullable types in Kotlin. A null key fails at runtime because
`ConcurrentHashMap` rejects null keys. A null value is different: the map stores a non-null
`CacheEntry`, so the wrapper can contain `null`, making a cached null indistinguishable from a miss.
Unless negative caching is required, constrain the API to `K : Any, V : Any`. Otherwise return an
explicit hit/miss result and document negative caching.

The immutable wrapper safely publishes a reference, but it does not make a mutable `V` thread-safe.
If callers share and modify a cached mutable object, they still need synchronization or an immutable
value contract. Keys must also keep stable equality and hash codes while stored.

## Concurrency that is already acceptable

A concurrent `get()` can read an older entry while a `put()` replaces it. That alone is not evidence
of a broken map or a need to synchronize every operation: overlapping calls can observe either
side of the update. Similarly, a value can reach its expiry immediately after the age check. The
usual cache contract checks freshness at lookup, not throughout the caller's subsequent use.

There is no loader in this API, so duplicate backend loads are not a demonstrated bug in this class.
If callers implement `get -> load -> put`, requests for the same missing key can stampede the backend;
that integration would need per-key load coalescing, rather than a global read/write lock.

## Suggested production direction and checks

I would use an established cache such as Caffeine with a configured `maximumSize` or `maximumWeight`,
`expireAfterWrite`, and an injectable ticker. It already provides eviction and expiry maintenance.
If prompt reclamation during idle periods matters, configure scheduled maintenance as well.

Before deployment, test the TTL boundary (just before, exactly at, and just after expiry), replacement
resetting TTL, cleanup racing with replacement, idle-entry reclamation, capacity under concurrent
inserts, and the chosen null contract. Use a fake ticker and barriers/latches rather than sleeps.
Then measure hit/miss rates, eviction counts, retained memory, GC and tail latency with realistic
key distribution and value sizes at the stated read/write concurrency. These are proposed checks
for the cache implementation; this repository's executable tests cover the hierarchy task.

References: [ConcurrentHashMap semantics](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html),
[System.nanoTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()),
[Caffeine eviction and expiration](https://github.com/ben-manes/caffeine/wiki/Eviction).
