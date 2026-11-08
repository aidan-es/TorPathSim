package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.simulateTor.FlagStatus
import io.github.aidan_es.torpathsim.simulateTor.SubsetRestrictionFlags
import java.io.File
import kotlin.test.*

class NodeDataManagerTest {

    private fun createSampleConsensusFile(): File {
        val temp = File.createTempFile("sample_consensus_", ".txt")
        temp.writeText(
            """
            r relay1 Ag84 2026-02-21 13:00:00 1.1.1.1 9001 0
            s Fast Guard Running Stable Valid
            w Bandwidth=10000
            r relay2 Bg84 2026-02-21 13:00:00 1.1.1.2 9001 0
            s Exit Fast Running Valid
            w Bandwidth=5000
            r relay3 Cg84 2026-02-21 13:00:00 1.1.1.3 9001 0
            s Fast Running Valid
            w Bandwidth=8000
            r relay4 Dg84 2026-02-21 13:00:00 1.1.1.4 9001 0
            s Exit Fast Guard Running Stable Valid
            w Bandwidth=15000
            r authRelay Eg84 2026-02-21 13:00:00 1.1.1.5 9001 0
            s Authority Fast Running Valid
            w Bandwidth=20000
            r badRelay Fg84 2026-02-21 13:00:00 1.1.1.6 9001 0
            s BadExit Fast Running Valid
            w Bandwidth=7000
            r middleOnly Gg84 2026-02-21 13:00:00 1.1.1.7 9001 0
            s Fast MiddleOnly Running Valid
            w Bandwidth=6000
            r notRunning Hg84 2026-02-21 13:00:00 1.1.1.8 9001 0
            s Fast Valid
            w Bandwidth=4000
            """.trimIndent()
        )
        temp.deleteOnExit()
        return temp
    }

    @Test
    fun testParseConsensusAndFilterUnsuitableNodes() {
        val file = createSampleConsensusFile()
        val manager = NodeDataManager()
        manager.readFileIntoCache(file)

        assertEquals(8, manager.nodeCount)

        val removed = manager.removeUnsuitableNodes()
        // Removed: authRelay (Authority), badRelay (BadExit), middleOnly (MiddleOnly), notRunning (missing Running) = 4 nodes
        assertEquals(4, removed)
        assertEquals(4, manager.nodeCount)

        val remainingIds = manager.allNodes.map { it.id }.toSet()
        assertEquals(4, remainingIds.size)
    }

    @Test
    fun testSelectNodeByFlags() {
        val file = createSampleConsensusFile()
        val manager = NodeDataManager()
        manager.readFileIntoCache(file)
        manager.removeUnsuitableNodes()

        // Test Guard only (not exit)
        val guardRules = SubsetRestrictionFlags(FlagStatus.POSITIVE, FlagStatus.NEGATIVE, FlagStatus.IRRELEVANT)
        val selectedGuard = manager.selectNode(guardRules, 0, Int.MAX_VALUE, emptySet(), false)
        assertNotEquals(0, selectedGuard[0])
        val guardNode = manager.allNodes.first { it.id == selectedGuard[0] }
        assertTrue(guardNode.isGuard)
        assertFalse(guardNode.isExit)

        // Test Exit only (not guard)
        val exitRules = SubsetRestrictionFlags(FlagStatus.NEGATIVE, FlagStatus.POSITIVE, FlagStatus.IRRELEVANT)
        val selectedExit = manager.selectNode(exitRules, 0, Int.MAX_VALUE, emptySet(), false)
        assertNotEquals(0, selectedExit[0])
        val exitNode = manager.allNodes.first { it.id == selectedExit[0] }
        assertTrue(exitNode.isExit)
        assertFalse(exitNode.isGuard)

        // Test Dual Guard + Exit
        val dualRules = SubsetRestrictionFlags(FlagStatus.POSITIVE, FlagStatus.POSITIVE, FlagStatus.IRRELEVANT)
        val selectedDual = manager.selectNode(dualRules, 0, Int.MAX_VALUE, emptySet(), false)
        assertNotEquals(0, selectedDual[0])
        val dualNode = manager.allNodes.first { it.id == selectedDual[0] }
        assertTrue(dualNode.isGuard && dualNode.isExit)
    }

    @Test
    fun testSelectNodeBandwidthFiltering() {
        val file = createSampleConsensusFile()
        val manager = NodeDataManager()
        manager.readFileIntoCache(file)
        manager.removeUnsuitableNodes()

        // Relays: relay1 (10000), relay2 (5000), relay3 (8000), relay4 (15000)
        // Select node strictly between 6000 and 9000 -> only relay3 (8000) matches
        val selected = manager.selectNode(null, 6000, 9000, emptySet(), false)
        assertEquals(8000, selected[1])

        // Select with impossible bandwidth bounds -> returns [0, 0]
        val failed = manager.selectNode(null, 20000, 30000, emptySet(), false)
        assertEquals(0, failed[0])
        assertEquals(0, failed[1])
    }

    @Test
    fun testSelectNodeExclusions() {
        val file = createSampleConsensusFile()
        val manager = NodeDataManager()
        manager.readFileIntoCache(file)
        manager.removeUnsuitableNodes()

        val allIds = manager.allNodes.map { it.id }.toSet()
        val excludedAll = manager.selectNode(null, 0, Int.MAX_VALUE, allIds, false)
        assertEquals(0, excludedAll[0], "Should fail to select when all nodes are excluded")

        val leaveOneOut = allIds.first()
        val exclusions = allIds - leaveOneOut
        val selected = manager.selectNode(null, 0, Int.MAX_VALUE, exclusions, false)
        assertEquals(leaveOneOut, selected[0], "Should select the single non-excluded node")
    }

    @Test
    fun testIsEmptyPropertyAndEmptyDataset() {
        val manager = NodeDataManager()
        assertTrue(manager.isEmpty, "Newly created manager should be empty")

        val emptyFile = File.createTempFile("empty_consensus_", ".txt")
        emptyFile.deleteOnExit()
        manager.readFileIntoCache(emptyFile)
        assertTrue(manager.isEmpty, "Manager should remain empty after loading an empty file")

        // Load only unsuitable nodes (e.g. Authority)
        val unsuitableFile = File.createTempFile("unsuitable_consensus_", ".txt")
        unsuitableFile.writeText(
            """
            r authRelay Eg84 2026-02-21 13:00:00 1.1.1.5 9001 0
            s Authority Fast Running Valid
            w Bandwidth=20000
            """.trimIndent()
        )
        unsuitableFile.deleteOnExit()

        val manager2 = NodeDataManager()
        manager2.readFileIntoCache(unsuitableFile)
        assertFalse(manager2.isEmpty)
        val removed = manager2.removeUnsuitableNodes()
        assertEquals(1, removed)
        assertTrue(manager2.isEmpty, "Manager should be empty after removing unsuitable nodes")
    }
}

