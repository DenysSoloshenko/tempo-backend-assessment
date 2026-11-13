# Tempo backend assessment

Two parts: [hierarchy filtering](src/main/kotlin/Hierarchy.kt) and a [SimpleCache code review](CACHE_REVIEW.md).

## Run the tests

Requires JDK 21. The Gradle wrapper downloads Gradle and the test dependencies on the first run.

```sh
./gradlew test
```

On Windows, use `gradlew.bat test`. The HTML report is in `build/reports/tests/test/index.html`.
Tests also run on GitHub Actions. The build uses Kotlin 2.2.21 and JUnit 5.

## Approach

The input is already in depth-first preorder, so every subtree occupies a continuous range.
When a node fails the predicate, the filter remembers its depth and skips deeper nodes until
it reaches a sibling or a later ancestor-level node. Kept IDs and depths are copied into new arrays.
Their order and depths stay unchanged because a retained node always retains all its ancestors.

This takes O(n) time and O(n) allocated space, assuming constant-time hierarchy access and predicate
evaluation. The traversal uses no recursion and only constant-size bookkeeping beyond the arrays.

## Assumptions and coverage

- The source follows the supplied hierarchy invariants: equal array lengths, unique IDs, non-negative
  depths, roots at depth zero, and increases in depth of at most one. Empty forests are supported.
- The source is not mutated during filtering. The supplied interface and array-based representation
  are retained; validation of malformed inputs is outside the filter's contract.
- Predicates should be side-effect free. They are evaluated once for each eligible node, in order,
  and not evaluated for descendants of a rejected node. Predicate exceptions propagate.
- Tests cover the supplied example, empty input, pruning at different levels, multiple roots,
  input preservation, independent result arrays, a 100,000-node chain, and a wide forest.
  A fixed-seed test also checks 500 generated forests against a separate parent-link model.

The cache exercise asks for a review, so its findings and suggested fixes are in `CACHE_REVIEW.md`;
no replacement cache or application server is needed to run this submission.
