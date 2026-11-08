package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.simulateTor.FlagStatus
import io.github.aidan_es.torpathsim.simulateTor.SubsetRestrictionFlags
import java.io.File
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

data class CachedNode(
    val id: Int,
    val bandwidth: Int,
    val isGuard: Boolean,
    val isExit: Boolean,
    val isStable: Boolean,
    val isValid: Boolean,
    val isRunning: Boolean,
    val isAuthority: Boolean,
    val isMiddleOnly: Boolean,
    val isBadExit: Boolean
)

class NodeDataManager {
    private var nodeCache: List<CachedNode> = emptyList()

    // Pre-indexed views for better filtering performance.
    private var guardNodes: List<CachedNode> = emptyList()
    private var exitNodes: List<CachedNode> = emptyList()
    private var stableNodes: List<CachedNode> = emptyList()

    @Throws(IOException::class)
    fun readFileIntoCache(file: File) {
        val newNodes = mutableListOf<CachedNode>()
        var idCounter = 1

        var currentBandwidth: Int? = null
        var currentFlags: Set<String>? = null
        var hasActiveNode = false

        file.useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("r ") -> {
                        val flags = currentFlags
                        val bw = currentBandwidth
                        if (hasActiveNode && flags != null && bw != null) {
                            newNodes.add(createNode(idCounter++, bw, flags))
                        }
                        hasActiveNode = true
                        currentBandwidth = null
                        currentFlags = null
                    }

                    line.startsWith("s ") -> {
                        currentFlags = line.substring(2).split(" ").toSet()
                    }

                    line.startsWith("w ") -> {
                        val bwPart = line.substringAfter("Bandwidth=", "")
                        if (bwPart.isNotEmpty()) {
                            currentBandwidth = bwPart.split(" ")[0].toIntOrNull()
                        }
                    }
                }
            }
            val flags = currentFlags
            val bw = currentBandwidth
            if (hasActiveNode && flags != null && bw != null) {
                newNodes.add(createNode(idCounter, bw, flags))
            }
        }

        nodeCache = newNodes
        updateIndices()
    }

    private fun updateIndices() {
        guardNodes = nodeCache.filter { it.isGuard }
        exitNodes = nodeCache.filter { it.isExit }
        stableNodes = nodeCache.filter { it.isStable }
    }

    private fun createNode(id: Int, bandwidth: Int, flags: Set<String>) = CachedNode(
        id = id,
        bandwidth = bandwidth,
        isGuard = flags.contains("Guard"),
        isExit = flags.contains("Exit"),
        isStable = flags.contains("Stable"),
        isValid = flags.contains("Valid"),
        isRunning = flags.contains("Running"),
        isAuthority = flags.contains("Authority"),
        isMiddleOnly = flags.contains("MiddleOnly"),
        isBadExit = flags.contains("BadExit")
    )

    fun removeUnsuitableNodes(): Int {
        val countBefore = nodeCache.size
        nodeCache = nodeCache.filter { node ->
            node.isValid && node.isRunning && !node.isAuthority && !node.isMiddleOnly && !node.isBadExit
        }
        updateIndices()
        return countBefore - nodeCache.size
    }

    val isEmpty: Boolean get() = nodeCache.isEmpty()
    val nodeCount: Int get() = nodeCache.size
    val totalBandwidth: Long get() = nodeCache.sumOf { it.bandwidth.toLong() }
    val allNodes: List<CachedNode> get() = nodeCache

    fun selectNode(
        subsetRestrictionFlags: SubsetRestrictionFlags?,
        modMinBandwidth: Int,
        modMaxBandwidth: Int,
        excludeNodes: Set<Int>,
        useBandwidthWeight: Boolean
    ): IntArray {
        // Start with the most restrictive flag pool if available
        val pool = when {
            subsetRestrictionFlags?.guardFlag == FlagStatus.POSITIVE -> guardNodes
            subsetRestrictionFlags?.exitFlag == FlagStatus.POSITIVE -> exitNodes
            subsetRestrictionFlags?.stableFlag == FlagStatus.POSITIVE -> stableNodes
            else -> nodeCache
        }

        val candidates = pool.filter { node ->
            if (node.id in excludeNodes) return@filter false
            if (node.bandwidth !in modMinBandwidth..modMaxBandwidth) return@filter false

            if (subsetRestrictionFlags != null) {
                // Only check flags we didn't already filter by in 'pool' selection
                if (pool !== guardNodes && subsetRestrictionFlags.guardFlag == FlagStatus.POSITIVE && !node.isGuard) return@filter false
                if (subsetRestrictionFlags.guardFlag == FlagStatus.NEGATIVE && node.isGuard) return@filter false

                if (pool !== exitNodes && subsetRestrictionFlags.exitFlag == FlagStatus.POSITIVE && !node.isExit) return@filter false
                if (subsetRestrictionFlags.exitFlag == FlagStatus.NEGATIVE && node.isExit) return@filter false

                if (pool !== stableNodes && subsetRestrictionFlags.stableFlag == FlagStatus.POSITIVE && !node.isStable) return@filter false
                if (subsetRestrictionFlags.stableFlag == FlagStatus.NEGATIVE && node.isStable) return@filter false
            }
            true
        }

        if (candidates.isEmpty()) return intArrayOf(0, 0)

        return if (useBandwidthWeight) {
            val poolTotalBw = candidates.sumOf { it.bandwidth.toLong() }
            if (poolTotalBw == 0L) return candidates.random().let { intArrayOf(it.id, it.bandwidth) }

            val threshold = ThreadLocalRandom.current().nextDouble() * poolTotalBw
            var cumulativeBw = 0.0
            for ((id, bandwidth) in candidates) {
                cumulativeBw += bandwidth
                if (cumulativeBw >= threshold) {
                    return intArrayOf(id, bandwidth)
                }
            }
            // Fallback for rounding edge cases
            candidates.last().let { intArrayOf(it.id, it.bandwidth) }
        } else {
            val winner = candidates.random()
            intArrayOf(winner.id, winner.bandwidth)
        }
    }
}
