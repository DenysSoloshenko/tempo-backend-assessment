import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class HierarchyTest {
    @Test
    fun `matches the supplied example`() {
        val input = hierarchy(
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
            intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2),
        )

        assertHierarchy(
            input.filter { it % 3 != 0 },
            intArrayOf(1, 2, 5, 8, 10, 11),
            intArrayOf(0, 1, 1, 0, 1, 2),
        )
    }

    @Test
    fun `empty input does not evaluate the predicate`() {
        val result = hierarchy(intArrayOf(), intArrayOf()).filter {
            error("An empty hierarchy has no IDs to evaluate")
        }

        assertHierarchy(result, intArrayOf(), intArrayOf())
    }

    @Test
    fun `single root can be kept or removed`() {
        val input = hierarchy(intArrayOf(42), intArrayOf(0))

        assertHierarchy(input.filter { true }, intArrayOf(42), intArrayOf(0))
        assertHierarchy(input.filter { false }, intArrayOf(), intArrayOf())
    }

    @Test
    fun `keeping everything preserves forest order and original depths`() {
        val ids = intArrayOf(8, 3, 10, -2, 4, 1, 7)
        val depths = intArrayOf(0, 1, 2, 1, 0, 1, 0)

        assertHierarchy(hierarchy(ids, depths).filter { true }, ids, depths)
    }

    @Test
    fun `rejecting all roots produces an empty forest`() {
        val input = hierarchy(intArrayOf(1, 2, 3, 4, 5), intArrayOf(0, 1, 2, 0, 1))

        assertHierarchy(input.filter { false }, intArrayOf(), intArrayOf())
    }

    @Test
    fun `rejecting a root removes only its tree including first middle and last roots`() {
        val ids = intArrayOf(1, 2, 3, 4, 5, 6)
        val depths = intArrayOf(0, 1, 0, 1, 0, 1)
        val input = hierarchy(ids, depths)

        for (root in listOf(1, 3, 5)) {
            val remaining = ids.indices.filter { ids[it] != root && ids[it] != root + 1 }
            assertHierarchy(
                input.filter { it != root },
                remaining.map { ids[it] }.toIntArray(),
                remaining.map { depths[it] }.toIntArray(),
            )
        }
    }

    @Test
    fun `matching descendants of a rejected node are removed without promotion`() {
        val input = hierarchy(
            intArrayOf(1, 2, 3, 4, 5, 6),
            intArrayOf(0, 1, 2, 3, 1, 2),
        )

        assertHierarchy(input.filter { it != 2 }, intArrayOf(1, 5, 6), intArrayOf(0, 1, 2))
    }

    @Test
    fun `rejecting a leaf preserves siblings and later roots`() {
        val input = hierarchy(intArrayOf(1, 2, 3, 4, 5), intArrayOf(0, 1, 1, 0, 1))

        assertHierarchy(input.filter { it != 2 }, intArrayOf(1, 3, 4, 5), intArrayOf(0, 1, 0, 1))
    }

    @Test
    fun `consecutive rejected sibling subtrees do not hide a later sibling`() {
        val input = hierarchy(
            intArrayOf(1, 2, 3, 4, 5, 6, 7),
            intArrayOf(0, 1, 2, 1, 2, 1, 2),
        )

        assertHierarchy(input.filter { it != 2 && it != 4 }, intArrayOf(1, 6, 7), intArrayOf(0, 1, 2))
    }

    @Test
    fun `leaving a rejected branch by several levels resumes filtering`() {
        val input = hierarchy(
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            intArrayOf(0, 1, 2, 3, 4, 1, 2, 0),
        )

        assertHierarchy(
            input.filter { it != 4 },
            intArrayOf(1, 2, 3, 6, 7, 8),
            intArrayOf(0, 1, 2, 1, 2, 0),
        )
    }

    @Test
    fun `predicate receives IDs rather than indexes and supports any integer ID`() {
        val input = hierarchy(
            intArrayOf(Int.MIN_VALUE, 0, -7, Int.MAX_VALUE),
            intArrayOf(0, 1, 1, 0),
        )

        assertHierarchy(
            input.filter { it != 0 },
            intArrayOf(Int.MIN_VALUE, -7, Int.MAX_VALUE),
            intArrayOf(0, 1, 0),
        )
    }

    @Test
    fun `predicate is evaluated once for eligible nodes and skipped for rejected descendants`() {
        val input = hierarchy(intArrayOf(1, 2, 3, 4, 5, 6), intArrayOf(0, 1, 2, 1, 0, 1))
        val visited = mutableListOf<Int>()

        val result = input.filter {
            visited += it
            it != 2 && it != 5
        }

        assertEquals(listOf(1, 2, 4, 5), visited)
        assertHierarchy(result, intArrayOf(1, 4), intArrayOf(0, 1))
    }

    @Test
    fun `filter does not modify input and returns independent arrays`() {
        val ids = intArrayOf(1, 2, 3)
        val depths = intArrayOf(0, 1, 0)
        val input = hierarchy(ids, depths)
        val result = input.filter { it != 2 }

        assertHierarchy(input, intArrayOf(1, 2, 3), intArrayOf(0, 1, 0))
        ids[0] = 100
        depths[0] = 5
        assertHierarchy(result, intArrayOf(1, 3), intArrayOf(0, 0))
    }

    @Test
    fun `filtering an already filtered forest preserves the same rules`() {
        val input = hierarchy(intArrayOf(1, 2, 3, 4, 5), intArrayOf(0, 1, 2, 1, 0))

        val result = input.filter { it != 3 }.filter { it != 1 }

        assertHierarchy(result, intArrayOf(5), intArrayOf(0))
    }

    @Test
    fun `predicate exceptions are propagated without modifying the source`() {
        val input = hierarchy(intArrayOf(1, 2), intArrayOf(0, 1))
        val failure = IllegalStateException("predicate failed")

        val thrown = assertFailsWith<IllegalStateException> {
            input.filter {
                if (it == 2) throw failure
                true
            }
        }

        assertSame(failure, thrown)
        assertHierarchy(input, intArrayOf(1, 2), intArrayOf(0, 1))
    }

    @Test
    fun `deep chains work without a recursive call stack`() {
        val count = 100_000
        val ids = IntArray(count) { it }
        val depths = IntArray(count) { it }
        val input = hierarchy(ids, depths)

        assertHierarchy(input.filter { true }, ids, depths)
        assertHierarchy(
            input.filter { it != count / 2 },
            ids.copyOf(count / 2),
            depths.copyOf(count / 2),
        )
    }

    @Test
    fun `wide forests keep evaluating independent roots`() {
        val count = 50_000
        val input = hierarchy(IntArray(count) { it }, IntArray(count))

        assertHierarchy(
            input.filter { it % 2 == 0 },
            IntArray(count / 2) { it * 2 },
            IntArray(count / 2),
        )
    }

    @Test
    fun `seeded random forests agree with an explicit ancestor based model`() {
        val random = Random(20261112)

        repeat(500) { case ->
            val nodes = mutableListOf<Node>()
            val roots = mutableListOf<Node>()
            val count = random.nextInt(1, 201)
            for (id in (-count until 0).shuffled(random)) {
                val parent = if (nodes.isEmpty() || random.nextInt(4) == 0) null else nodes.random(random)
                val node = Node(id, parent)
                if (parent == null) roots += node else parent.children += node
                nodes += node
            }

            val flattened = mutableListOf<Pair<Node, Int>>()
            fun visit(node: Node, depth: Int) {
                flattened += node to depth
                node.children.forEach { visit(it, depth + 1) }
            }
            roots.forEach { visit(it, 0) }

            val allowed = nodes.filter { random.nextBoolean() }.map { it.id }.toSet()
            val expected = flattened.filter { (node, _) ->
                generateSequence(node) { it.parent }.all { it.id in allowed }
            }
            val input = hierarchy(
                flattened.map { it.first.id }.toIntArray(),
                flattened.map { it.second }.toIntArray(),
            )

            assertHierarchy(
                input.filter { it in allowed },
                expected.map { it.first.id }.toIntArray(),
                expected.map { it.second }.toIntArray(),
                "Random case $case",
            )
        }
    }

    private class Node(val id: Int, val parent: Node?) {
        val children = mutableListOf<Node>()
    }

    private fun hierarchy(ids: IntArray, depths: IntArray): Hierarchy = ArrayBasedHierarchy(ids, depths)

    private fun assertHierarchy(
        actual: Hierarchy,
        expectedIds: IntArray,
        expectedDepths: IntArray,
        message: String = "",
    ) {
        assertEquals(expectedIds.size, actual.size, message)
        assertContentEquals(expectedIds, IntArray(actual.size) { actual.nodeId(it) }, message)
        assertContentEquals(expectedDepths, IntArray(actual.size) { actual.depth(it) }, message)
    }
}
