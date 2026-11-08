package io.github.aidan_es.torpathsim.simulateTor

import io.github.aidan_es.torpathsim.NodeDataManager

class Client(
    private val nodeDataManager: NodeDataManager,
    private val ordering: IntArray,
    private val pathCount: Int,
    private val guardCount: Int,
    private val setOfSubsetRestrictionRules: ArrayList<SubsetRestrictionRules>,
    private val adversary: Adversary
) {
    val paths = mutableListOf<IntArray>()
    private val guardSet = mutableListOf<IntArray>()
    val bandwidths = mutableListOf<Int>()

    var countCompromisedPaths = 0L
        private set
    var countFailedPaths = 0L
        private set
    var countFailedGuards = 0L
        private set

    fun simulate() {
        repeat(guardCount) {
            val rules = setOfSubsetRestrictionRules[0]
            val excludeSet = guardSet.map { it[0] }.toSet()

            val node = nodeDataManager.selectNode(
                rules.subsetRestrictionFlags,
                rules.minimumBandwidth,
                rules.maximumBandwidth,
                excludeSet,
                true
            )

            if (node[0] == 0) {
                countFailedGuards++
            } else {
                guardSet.add(node)
            }
        }

        repeat(pathCount) {
            var minBandwidth: Int? = null
            var maxBandwidth: Int? = null
            val nodesInPath = IntArray(3)
            var pathBandwidth: Int? = null

            try {
                for (nodeNumber in ordering) {
                    val node: IntArray
                    if (nodeNumber == 1 && guardCount != 0) {
                        check(guardSet.isNotEmpty()) { "Guard set is empty" }
                        val exclusions = nodesInPath.filter { it != 0 }.toSet()
                        val availableGuards = guardSet.filter { it[0] !in exclusions }
                        check(availableGuards.isNotEmpty()) { "No eligible guard available" }
                        node = availableGuards[availableGuards.indices.random()]
                    } else {
                        val poolRules = setOfSubsetRestrictionRules[nodeNumber - 1]
                        var modMax = poolRules.maximumBandwidth
                        var modMin = poolRules.minimumBandwidth

                        if (poolRules.bandwidthRule == BandwidthRule.GREATER_THAN && maxBandwidth != null && maxBandwidth > modMin) {
                            modMin = maxBandwidth
                        }
                        if (poolRules.bandwidthRule == BandwidthRule.LESS_THAN && minBandwidth != null && minBandwidth < modMax) {
                            modMax = minBandwidth
                        }

                        val exclusions = nodesInPath.filter { it != 0 }.toSet()
                        node = nodeDataManager.selectNode(
                            poolRules.subsetRestrictionFlags,
                            modMin,
                            modMax,
                            exclusions,
                            true
                        )

                        check(node[0] != 0) { "Failed to select node" }
                    }

                    if (maxBandwidth == null || node[1] > maxBandwidth) maxBandwidth = node[1]
                    if (minBandwidth == null || node[1] < minBandwidth) minBandwidth = node[1]

                    nodesInPath[nodeNumber - 1] = node[0]
                    pathBandwidth = minBandwidth
                }

                bandwidths.add(pathBandwidth!!)
                paths.add(nodesInPath)

                if (adversary.isCompromisedPath(nodesInPath[0], nodesInPath[2])) {
                    countCompromisedPaths++
                }
            } catch (_: IllegalStateException) {
                countFailedPaths++
            }
        }
    }
}
