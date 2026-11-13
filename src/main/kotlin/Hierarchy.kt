/** An ordered forest stored in depth-first preorder, with parallel node ID and depth arrays. */
interface Hierarchy {
    val size: Int

    fun nodeId(index: Int): Int

    fun depth(index: Int): Int

    fun formatString(): String = (0 until size).joinToString(
        separator = ", ",
        prefix = "[",
        postfix = "]",
    ) { index -> "${nodeId(index)}:${depth(index)}" }
}

/** Keeps a node only when its ID and every ancestor's ID pass [nodeIdPredicate]. */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
    val keptIds = IntArray(size)
    val keptDepths = IntArray(size)
    var keptCount = 0
    var rejectedDepth = -1

    for (index in 0 until size) {
        val currentDepth = depth(index)

        // Descendants are contiguous in preorder. Resume at a sibling or an ancestor's sibling.
        if (rejectedDepth >= 0) {
            if (currentDepth > rejectedDepth) continue
            rejectedDepth = -1
        }

        val id = nodeId(index)
        if (!nodeIdPredicate(id)) {
            rejectedDepth = currentDepth
            continue
        }

        keptIds[keptCount] = id
        keptDepths[keptCount] = currentDepth
        keptCount++
    }

    return ArrayBasedHierarchy(keptIds.copyOf(keptCount), keptDepths.copyOf(keptCount))
}

class ArrayBasedHierarchy(
    private val myNodeIds: IntArray,
    private val myDepths: IntArray,
) : Hierarchy {
    override val size: Int = myDepths.size

    override fun nodeId(index: Int): Int = myNodeIds[index]

    override fun depth(index: Int): Int = myDepths[index]
}
