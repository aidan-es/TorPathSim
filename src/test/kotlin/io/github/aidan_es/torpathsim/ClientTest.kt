package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.simulateTor.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientTest {

    private fun createConsensusFile(): File {
        val temp = File.createTempFile("client_consensus_", ".txt")
        temp.writeText(
            """
            r guard1 G1 2026-02-21 13:00:00 1.1.1.1 9001 0
            s Guard Running Valid
            w Bandwidth=8000
            r guard2 G2 2026-02-21 13:00:00 1.1.1.2 9001 0
            s Guard Running Valid
            w Bandwidth=12000
            r guard3 G3 2026-02-21 13:00:00 1.1.1.3 9001 0
            s Guard Running Valid
            w Bandwidth=15000
            r guard4 G4 2026-02-21 13:00:00 1.1.1.4 9001 0
            s Guard Running Valid
            w Bandwidth=20000
            r middle1 M1 2026-02-21 13:00:00 1.1.1.5 9001 0
            s Running Valid
            w Bandwidth=25000
            r middle2 M2 2026-02-21 13:00:00 1.1.1.6 9001 0
            s Running Valid
            w Bandwidth=30000
            r exit1 E1 2026-02-21 13:00:00 1.1.1.7 9001 0
            s Exit Running Valid
            w Bandwidth=14000
            r exit2 E2 2026-02-21 13:00:00 1.1.1.8 9001 0
            s Exit Running Valid
            w Bandwidth=18000
            """.trimIndent()
        )
        temp.deleteOnExit()
        return temp
    }

    private fun setupManager(): NodeDataManager {
        val manager = NodeDataManager()
        manager.readFileIntoCache(createConsensusFile())
        manager.removeUnsuitableNodes()
        return manager
    }

    private fun createStandardRules(): ArrayList<SubsetRestrictionRules> {
        return arrayListOf(
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.POSITIVE, FlagStatus.NEGATIVE, FlagStatus.IRRELEVANT),
                0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT
            ),
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.IRRELEVANT, FlagStatus.IRRELEVANT, FlagStatus.IRRELEVANT),
                0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT
            ),
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.NEGATIVE, FlagStatus.POSITIVE, FlagStatus.IRRELEVANT),
                0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT
            )
        )
    }

    @Test
    fun testSuccessfulPathCreation() {
        val manager = setupManager()
        val rules = createStandardRules()
        val adversary = Adversary(SelectBy.NODES, "0", listOf(100), rules, manager)

        val client = Client(
            nodeDataManager = manager,
            ordering = intArrayOf(3, 1, 2),
            pathCount = 20,
            guardCount = 2,
            setOfSubsetRestrictionRules = rules,
            adversary = adversary
        )

        client.simulate()

        assertEquals(20, client.paths.size)
        assertEquals(0L, client.countFailedPaths)
        assertEquals(0L, client.countFailedGuards)
        assertEquals(20, client.bandwidths.size)

        val allNodesMap = manager.allNodes.associateBy { it.id }

        for (path in client.paths) {
            assertEquals(3, path.size)
            // Distinct nodes (no duplicate hops in a circuit)
            assertEquals(3, path.toSet().size, "All 3 hops in a circuit must be unique")

            val entryNode = allNodesMap[path[0]]!!
            val exitNode = allNodesMap[path[2]]!!

            assertTrue(entryNode.isGuard, "Hop 1 must be a guard")
            assertFalse(entryNode.isExit, "Hop 1 must not be an exit")
            assertTrue(exitNode.isExit, "Hop 3 must be an exit")
            assertFalse(exitNode.isGuard, "Hop 3 must not be a guard")
        }
    }

    @Test
    fun testGuardPinningAcrossMultipleCircuits() {
        val manager = setupManager()
        val rules = createStandardRules()
        val adversary = Adversary(SelectBy.NODES, "0", listOf(100), rules, manager)

        val guardCount = 2
        val client = Client(
            nodeDataManager = manager,
            ordering = intArrayOf(3, 1, 2),
            pathCount = 40,
            guardCount = guardCount,
            setOfSubsetRestrictionRules = rules,
            adversary = adversary
        )

        client.simulate()

        assertEquals(40, client.paths.size)
        val entryNodesUsed = client.paths.map { it[0] }.toSet()
        // Despite 40 circuits created and 4 guards available, entry nodes must never exceed guardCount
        assertTrue(
            entryNodesUsed.size <= guardCount,
            "Client must only use guards from its pinned guard set of size $guardCount, but used ${entryNodesUsed.size}"
        )
    }

    @Test
    fun testGPathBandwidthRulesEnforcement() {
        val manager = setupManager()
        // gPath rules:
        // Hop 3 (Exit) chosen first: IRRELEVANT
        // Hop 1 (Guard) chosen second: LESS_THAN (guard bw <= exit bw)
        // Hop 2 (Middle) chosen third: GREATER_THAN (middle bw >= max(guard bw, exit bw))
        val gPathRules = arrayListOf(
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.POSITIVE, FlagStatus.NEGATIVE, FlagStatus.IRRELEVANT),
                0, Int.MAX_VALUE, BandwidthRule.LESS_THAN
            ),
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.IRRELEVANT, FlagStatus.IRRELEVANT, FlagStatus.IRRELEVANT),
                0, Int.MAX_VALUE, BandwidthRule.GREATER_THAN
            ),
            SubsetRestrictionRules(
                SubsetRestrictionFlags(FlagStatus.NEGATIVE, FlagStatus.POSITIVE, FlagStatus.IRRELEVANT),
                0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT
            )
        )

        val adversary = Adversary(SelectBy.NODES, "0", listOf(100), gPathRules, manager)

        val client = Client(
            nodeDataManager = manager,
            ordering = intArrayOf(3, 1, 2),
            pathCount = 30,
            guardCount = 0, // No guard pinning to test fresh LESS_THAN selection per circuit
            setOfSubsetRestrictionRules = gPathRules,
            adversary = adversary
        )

        client.simulate()

        val allNodesMap = manager.allNodes.associateBy { it.id }

        for (path in client.paths) {
            val entryBw = allNodesMap[path[0]]!!.bandwidth
            val middleBw = allNodesMap[path[1]]!!.bandwidth
            val exitBw = allNodesMap[path[2]]!!.bandwidth

            // Guard bandwidth must be <= Exit bandwidth
            assertTrue(
                entryBw <= exitBw,
                "In gPath, entry bandwidth ($entryBw) must be <= exit bandwidth ($exitBw)"
            )
            // Middle bandwidth must be >= max(entryBw, exitBw)
            assertTrue(
                middleBw >= maxOf(entryBw, exitBw),
                "In gPath, middle bandwidth ($middleBw) must be >= max($entryBw, $exitBw)"
            )
        }
    }

    @Test
    fun testAdversaryCompromiseCounting() {
        val manager = setupManager()
        val rules = createStandardRules()

        // Explicitly compromise 1 guard and 1 exit
        val adversary = Adversary(SelectBy.NODES, "2", listOf(50, 50), rules, manager)
        assertFalse(adversary.hasCompromiseEstablishmentFailed())

        val client = Client(
            nodeDataManager = manager,
            ordering = intArrayOf(3, 1, 2),
            pathCount = 50,
            guardCount = 2,
            setOfSubsetRestrictionRules = rules,
            adversary = adversary
        )

        client.simulate()

        assertEquals(50, client.paths.size)

        var manualCompromiseCount = 0L
        for (path in client.paths) {
            if (adversary.isCompromisedPath(path[0], path[2])) {
                manualCompromiseCount++
            }
        }

        assertEquals(manualCompromiseCount, client.countCompromisedPaths)
    }

    @Test
    fun testHandlingUnsatisfiableRulesFailsGracefully() {
        val manager = setupManager()
        // Impossible rule: minimum bandwidth higher than any node in the network (e.g. 999,999)
        val impossibleRules = arrayListOf(
            SubsetRestrictionRules(null, 999999, Int.MAX_VALUE, BandwidthRule.IRRELEVANT),
            SubsetRestrictionRules(null, 0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT),
            SubsetRestrictionRules(null, 0, Int.MAX_VALUE, BandwidthRule.IRRELEVANT)
        )
        val adversary = Adversary(SelectBy.NODES, "0", listOf(100), impossibleRules, manager)

        val client = Client(
            nodeDataManager = manager,
            ordering = intArrayOf(1, 2, 3),
            pathCount = 10,
            guardCount = 2,
            setOfSubsetRestrictionRules = impossibleRules,
            adversary = adversary
        )

        client.simulate()

        // Guard selection fails because no node satisfies min bandwidth 999999
        assertEquals(2L, client.countFailedGuards)
        // Path creation fails because guard set is empty / hop 1 cannot be selected
        assertEquals(10L, client.countFailedPaths)
        assertEquals(0, client.paths.size)
    }
}
