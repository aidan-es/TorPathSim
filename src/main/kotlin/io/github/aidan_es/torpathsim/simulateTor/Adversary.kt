package io.github.aidan_es.torpathsim.simulateTor

import io.github.aidan_es.torpathsim.NodeDataManager

class Adversary(
    selectBy: SelectBy?,
    numValue: String,
    percentages: List<Int>,
    subsetRestrictionRules: List<SubsetRestrictionRules>,
    nodeDataManager: NodeDataManager,
) {
    val compromisedNodes = mutableSetOf<Int>()
    var compromisedBandwidth = 0L
        private set

    private var compromiseEstablishmentFailure = false
    var targetCompromiseNodeCount = 0
        private set
    var targetCompromisedBandwidthCount = 0L
        private set

    init {
        when (selectBy) {
            SelectBy.NODES -> {
                val totalNodes = if (numValue.endsWith("%")) {
                    (numValue.removeSuffix("%").toDouble() / 100.0 * nodeDataManager.nodeCount).toInt()
                } else {
                    numValue.toInt()
                }
                percentages.indices.forEach { i ->
                    compromiseByNodes(
                        (totalNodes * (percentages[i] / 100.0)).toInt(),
                        subsetRestrictionRules[i],
                        nodeDataManager
                    )
                }
            }

            SelectBy.BANDWIDTH -> {
                val totalBw = if (numValue.endsWith("%")) {
                    (numValue.removeSuffix("%").toDouble() / 100.0 * nodeDataManager.totalBandwidth).toLong()
                } else {
                    numValue.toLong()
                }
                percentages.indices.forEach { i ->
                    compromiseByBandwidth(
                        (totalBw * (percentages[i] / 100.0)).toLong(),
                        subsetRestrictionRules[i],
                        nodeDataManager
                    )
                }
            }

            null -> {}
        }
    }

    private fun compromiseByNodes(count: Int, rules: SubsetRestrictionRules, nodeDataManager: NodeDataManager) {
        targetCompromiseNodeCount += count
        repeat(count) {
            val node = nodeDataManager.selectNode(
                rules.subsetRestrictionFlags,
                rules.minimumBandwidth,
                rules.maximumBandwidth,
                compromisedNodes,
                false
            )
            if (node[0] == 0) {
                compromiseEstablishmentFailure = true
            } else {
                compromisedNodes.add(node[0])
                compromisedBandwidth += node[1]
            }
        }
    }

    private fun compromiseByBandwidth(targetBw: Long, rules: SubsetRestrictionRules, nodeDataManager: NodeDataManager) {
        targetCompromisedBandwidthCount += targetBw
        var currentPoolBw = 0L

        while (currentPoolBw < targetBw) {
            val remainingBw = targetBw - currentPoolBw
            val effectiveMaxBw = if (remainingBw < rules.maximumBandwidth) {
                remainingBw.toInt()
            } else {
                rules.maximumBandwidth
            }

            var node = nodeDataManager.selectNode(
                rules.subsetRestrictionFlags,
                rules.minimumBandwidth,
                effectiveMaxBw,
                compromisedNodes,
                false
            )

            // If no node fits under remaining budget and we haven't picked any node yet, allow picking one
            if (node[0] == 0 && currentPoolBw == 0L) {
                node = nodeDataManager.selectNode(
                    rules.subsetRestrictionFlags,
                    rules.minimumBandwidth,
                    rules.maximumBandwidth,
                    compromisedNodes,
                    false
                )
            }

            if (node[0] == 0) {
                if (currentPoolBw == 0L) {
                    compromiseEstablishmentFailure = true
                }
                break
            }

            val nodeBw = node[1].toLong()
            compromisedNodes.add(node[0])
            compromisedBandwidth += nodeBw
            currentPoolBw += nodeBw
        }
    }

    fun isCompromisedPath(firstNode: Int, lastNode: Int) =
        firstNode in compromisedNodes && lastNode in compromisedNodes

    fun hasCompromiseEstablishmentFailed() = compromiseEstablishmentFailure
}
