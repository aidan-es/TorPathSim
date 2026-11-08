package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.simulateTor.*
import java.io.File
import kotlin.test.*

class AdversaryTest {

    private fun setupManager(): NodeDataManager {
        val temp = File.createTempFile("adv_consensus_", ".txt")
        temp.writeText(
            """
            r node1 A 2026-02-21 13:00:00 1.1.1.1 9001 0
            s Guard Running Valid
            w Bandwidth=1000
            r node2 B 2026-02-21 13:00:00 1.1.1.2 9001 0
            s Guard Running Valid
            w Bandwidth=2000
            r node3 C 2026-02-21 13:00:00 1.1.1.3 9001 0
            s Exit Running Valid
            w Bandwidth=3000
            r node4 D 2026-02-21 13:00:00 1.1.1.4 9001 0
            s Exit Running Valid
            w Bandwidth=4000
            r node5 E 2026-02-21 13:00:00 1.1.1.5 9001 0
            s Running Valid
            w Bandwidth=5000
            """.trimIndent()
        )
        temp.deleteOnExit()
        val manager = NodeDataManager()
        manager.readFileIntoCache(temp)
        manager.removeUnsuitableNodes()
        return manager
    }

    @Test
    fun testCompromiseByNodeCount() {
        val manager = setupManager()
        val rules = listOf(
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.POSITIVE, FlagStatus.IRRELEVANT, FlagStatus.IRRELEVANT),
                0,
                Int.MAX_VALUE,
                BandwidthRule.IRRELEVANT
            )
        )

        // Compromise 2 guard nodes
        val adversary = Adversary(SelectBy.NODES, "2", listOf(100), rules, manager)

        assertEquals(2, adversary.compromisedNodes.size)
        assertFalse(adversary.hasCompromiseEstablishmentFailed())
        // Both compromised nodes must be guards (node1 and node2)
        val allNodesMap = manager.allNodes.associateBy { it.id }
        assertTrue(adversary.compromisedNodes.all { allNodesMap[it]?.isGuard == true })
    }

    @Test
    fun testCompromiseByBandwidthPercentage() {
        val manager = setupManager()
        // Total bandwidth = 1000 + 2000 + 3000 + 4000 + 5000 = 15000
        // Target 20% = 3000 bytes/sec
        val rules = listOf(
            SubsetRestrictionRules(
                null,
                0,
                Int.MAX_VALUE,
                BandwidthRule.IRRELEVANT
            )
        )

        val adversary = Adversary(SelectBy.BANDWIDTH, "20%", listOf(100), rules, manager)

        assertTrue(adversary.compromisedNodes.isNotEmpty())
        assertTrue(adversary.compromisedBandwidth <= 3000 || adversary.compromisedNodes.size == 1)
        assertFalse(adversary.hasCompromiseEstablishmentFailed())
    }

    @Test
    fun testIsCompromisedPath() {
        val manager = setupManager()
        val rules = listOf(
            SubsetRestrictionRules(null, 0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT)
        )
        val adversary = Adversary(SelectBy.NODES, "2", listOf(100), rules, manager)
        val compromisedList = adversary.compromisedNodes.toList()
        val nodeA = compromisedList[0]
        val nodeB = compromisedList[1]
        val nonCompromised = manager.allNodes.first { it.id !in adversary.compromisedNodes }.id

        // Path compromised only when BOTH entry (first) and exit (last) are compromised
        assertTrue(adversary.isCompromisedPath(nodeA, nodeB))
        assertTrue(adversary.isCompromisedPath(nodeB, nodeA))
        assertFalse(adversary.isCompromisedPath(nodeA, nonCompromised))
        assertFalse(adversary.isCompromisedPath(nonCompromised, nodeB))
        assertFalse(adversary.isCompromisedPath(nonCompromised, nonCompromised))
    }

    @Test
    fun testCompromiseEstablishmentFailureWhenNodesExhausted() {
        val manager = setupManager()
        // Only 2 guards exist in the dataset; attempt to compromise 10 guards
        val rules = listOf(
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.POSITIVE, FlagStatus.IRRELEVANT, FlagStatus.IRRELEVANT),
                0,
                Int.MAX_VALUE,
                BandwidthRule.IRRELEVANT
            )
        )

        val adversary = Adversary(SelectBy.NODES, "10", listOf(100), rules, manager)
        assertTrue(adversary.hasCompromiseEstablishmentFailed())
        assertEquals(2, adversary.compromisedNodes.size)
    }
}
