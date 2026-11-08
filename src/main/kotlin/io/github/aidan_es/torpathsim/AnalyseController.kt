package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.parse.cmd.CommandAnalyse
import io.github.aidan_es.torpathsim.parse.config.BandwidthRuleConverter
import io.github.aidan_es.torpathsim.parse.config.FlagStatusConverter
import io.github.aidan_es.torpathsim.parse.config.SelectByConverter
import io.github.aidan_es.torpathsim.simulateTor.*
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.ln

private class SelectParser : NoOpCliktCommand(name = "select") {
    val selectBy: SelectBy by option("--selectBy")
        .convert { SelectByConverter.convert(it) }
        .required()
    val amount: String by option("--amount")
        .required()
}

private class PoolParser : NoOpCliktCommand(name = "pool") {
    val guardFlag: FlagStatus by option("-g", "--guard")
        .convert { FlagStatusConverter.convert(it) }
        .default(FlagStatus.IRRELEVANT)
    val exitFlag: FlagStatus by option("-e", "--exit")
        .convert { FlagStatusConverter.convert(it) }
        .default(FlagStatus.IRRELEVANT)
    val stableFlag: FlagStatus by option("-s", "--stable")
        .convert { FlagStatusConverter.convert(it) }
        .default(FlagStatus.IRRELEVANT)
    val minBandwidth: Int by option("--minb")
        .int()
        .default(0)
    val maxBandwidth: Int by option("--maxb")
        .int()
        .default(Int.MAX_VALUE)
    val bandwidthRule: BandwidthRule by option("--brule")
        .convert { BandwidthRuleConverter.convert(it) }
        .default(BandwidthRule.IRRELEVANT)
}

class AnalyseController(private val nodeDataManager: NodeDataManager) {
    private val clients = mutableListOf<Client>()
    private var adversary: Adversary? = null

    private var totalFailedClients = 0L
    private var totalFailedPaths = 0L
    private var totalFailedGuards = 0L
    private var clientCount = 0L
    private var intendedPathsPerClient = 0L
    private var intendedGuardsPerClient = 0L
    private var totalTorNodes = 0L
    private var totalCompromisedPaths = 0L
    private var totalCompromisedClients = 0L
    private var totalCrashedClients = 0L

    fun parseArgs(analyse: CommandAnalyse) {
        runBlocking {
            loadNodeData(analyse.ld)
            parseAdversaryStrategyConfig(analyse.adversaryStrategy)
            parseRulesConfig(analyse.rules, analyse.clients, analyse.paths)
        }
    }

    private fun loadNodeData(ld: File) {
        if (!ld.exists()) throw FileNotFoundException("Dataset file '${ld.path}' does not exist.")
        terminal.println(TextColors.gray("Loading consensus data into memory..."))
        nodeDataManager.readFileIntoCache(ld)
        if (nodeDataManager.isEmpty) {
            throw IOException("Consensus dataset '${ld.name}' contains no valid Tor nodes.")
        }
        terminal.println(TextColors.gray("Loaded ${nodeDataManager.nodeCount} records from ${ld.name}."))

        val removed = nodeDataManager.removeUnsuitableNodes()
        terminal.println(TextColors.yellow("Removed $removed unsuitable nodes."))
        if (nodeDataManager.isEmpty) {
            throw IOException("Consensus dataset '${ld.name}' contains no suitable Tor nodes after filtering.")
        }
        this.totalTorNodes = nodeDataManager.nodeCount.toLong()
    }

    private fun parseAdversaryStrategyConfig(adversaryStrategy: File) {
        if (!adversaryStrategy.canRead()) throw FileNotFoundException("Cannot read file ${adversaryStrategy.path}")

        val lines = adversaryStrategy.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val selectLine = lines.firstOrNull { it.startsWith("select") }
            ?: throw IOException("Adversary strategy file must contain a 'select' line.")

        val poolLines = lines.filterNot { it.startsWith("select") }

        val selectArgs = selectLine.substringAfter("select").trim()
            .split("\\s+".toRegex()).filter { it.isNotBlank() }
        val selectParser = SelectParser()
        selectParser.parse(selectArgs)

        val percentages = ArrayList<Int>()
        val subsetRestrictionRules = ArrayList<SubsetRestrictionRules>()
        var percentageSum = 0

        for (line in poolLines) {
            val parts = line.split("\\s+".toRegex(), limit = 2)
            val percentage = parts[0].toInt()
            val args = if (parts.size > 1) {
                parts[1].trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            percentageSum += percentage
            percentages.add(percentage)

            val poolParams = PoolParser()
            poolParams.parse(args)
            subsetRestrictionRules.add(
                SubsetRestrictionRules(
                    SubsetRestrictionFlags(poolParams.guardFlag, poolParams.exitFlag, poolParams.stableFlag),
                    poolParams.minBandwidth,
                    poolParams.maxBandwidth,
                    poolParams.bandwidthRule
                )
            )
        }

        if (percentageSum != 100) throw IOException("Proportions for adversary selection pools must sum to 100.")
        this.adversary = Adversary(selectParser.selectBy, selectParser.amount, percentages, subsetRestrictionRules, nodeDataManager)
    }

    private suspend fun parseRulesConfig(rules: File, clientCount: Int, pathCount: Int) {
        if (!rules.canRead()) throw FileNotFoundException("Cannot read file ${rules.path}")

        val rulesLines = rules.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split("\\s+".toRegex(), limit = 2)
                if (parts.size == 2) parts[0].lowercase() to parts[1].trim() else parts[0].lowercase() to ""
            }

        val ordering = rulesLines["order"]?.let {
            val elements = it.split("\\s+".toRegex()).filter { s -> s.isNotBlank() }.map { s -> s.toInt() }
            if (elements.size != 3 || !elements.containsAll(listOf(1, 2, 3))) {
                throw IOException("Invalid order for node selection (1 2 3).")
            }
            elements.toIntArray()
        } ?: intArrayOf(1, 2, 3)

        val guardCount = rulesLines["guards"]?.toIntOrNull() ?: 3

        val setOfSubsetRestrictionRules = ArrayList<SubsetRestrictionRules>()
        for (i in 1..3) {
            val rawArgs = rulesLines.getOrDefault("pool$i", "")
            val args = if (rawArgs.isBlank()) {
                emptyList()
            } else {
                rawArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
            }
            val poolParams = PoolParser()
            poolParams.parse(args)
            setOfSubsetRestrictionRules.add(
                SubsetRestrictionRules(
                    SubsetRestrictionFlags(poolParams.guardFlag, poolParams.exitFlag, poolParams.stableFlag),
                    poolParams.minBandwidth,
                    poolParams.maxBandwidth,
                    poolParams.bandwidthRule
                )
            )
        }

        this.intendedPathsPerClient = pathCount.toLong()
        this.intendedGuardsPerClient = guardCount.toLong()

        constructModel(ordering, clientCount, pathCount, guardCount, setOfSubsetRestrictionRules)
        analyseModel()
    }

    private suspend fun constructModel(
        ordering: IntArray,
        clientCount: Int,
        pathCount: Int,
        guardCount: Int,
        setOfSubsetRestrictionRules: ArrayList<SubsetRestrictionRules>
    ) = coroutineScope {
        terminal.println("Simulating $clientCount clients using coroutines...")

        val deferredClients = (1..clientCount).map {
            async(Dispatchers.Default) {
                val client =
                    Client(nodeDataManager, ordering, pathCount, guardCount, setOfSubsetRestrictionRules, adversary!!)
                client.simulate()
                client
            }
        }

        deferredClients.forEach { deferred ->
            try {
                clients.add(deferred.await())
            } catch (_: Exception) {
                totalCrashedClients++
            }
        }
    }

    private fun analyseModel() {
        this.clientCount = clients.size.toLong()

        for (client in clients) {
            if (client.countFailedGuards != 0L || client.countFailedPaths != 0L) {
                totalFailedClients++
                totalFailedPaths += client.countFailedPaths
                totalFailedGuards += client.countFailedGuards
            }
            if (client.countCompromisedPaths != 0L) {
                totalCompromisedClients++
                totalCompromisedPaths += client.countCompromisedPaths
            }
        }

        val pathCount = (intendedPathsPerClient * clientCount) - totalFailedPaths

        terminal.println(TextStyles.bold("\nAnalysis complete:"))
        terminal.println("Created $clientCount clients.")
        if (totalCrashedClients > 0) terminal.println(TextColors.red("$totalCrashedClients clients crashed during simulation."))

        if (totalFailedClients != 0L) {
            terminal.println(TextColors.yellow("Attempted to select $intendedGuardsPerClient guards and $intendedPathsPerClient paths per client."))
            terminal.println(TextColors.red("Of which, $totalFailedClients clients failed. In total $totalFailedGuards guards and $totalFailedPaths paths could not be completed ($pathCount paths in total)."))
        } else {
            terminal.println(TextColors.green("Successfully selected $intendedGuardsPerClient guards and $intendedPathsPerClient paths per client. ($pathCount paths in total)."))
        }

        adversary?.let { adv ->
            val compromisedNodesCount = adv.compromisedNodes.size
            val compromisedPercent = (compromisedNodesCount.toDouble() / totalTorNodes) * 100.0

            terminal.println("\nAdversary Control:")
            terminal.println("Compromised $compromisedNodesCount nodes (${"%.2f".format(compromisedPercent)}%). Total Bandwidth: ${adv.compromisedBandwidth} bytes/sec.")

            if (adv.hasCompromiseEstablishmentFailed()) {
                terminal.println(TextColors.red("Warning: Adversary node selection failed due to lack of suitable nodes."))
                if (adv.targetCompromiseNodeCount > 0) {
                    terminal.println(TextColors.red("Attempted to compromise ${adv.targetCompromiseNodeCount} nodes, but only achieved $compromisedNodesCount."))
                }
                if (adv.targetCompromisedBandwidthCount > 0L) {
                    terminal.println(TextColors.red("Attempted to compromise ${adv.targetCompromisedBandwidthCount} bytes/sec, but only achieved ${adv.compromisedBandwidth} bytes/sec."))
                }
            }
        }

        val compromisedClientPercent = (totalCompromisedClients.toDouble() / clientCount) * 100.0
        val compromisedPathPercent = (totalCompromisedPaths.toDouble() / pathCount) * 100.0
        terminal.println(TextColors.red("\n$totalCompromisedClients (${"%.2f".format(compromisedClientPercent)}%) clients experienced compromise."))
        terminal.println(TextColors.red("Total $totalCompromisedPaths (${"%.2f".format(compromisedPathPercent)}%) paths compromised."))

        val netNodes = nodeDataManager.allNodes
        val netCount = netNodes.size
        val netAvgBw = if (netCount > 0) netNodes.sumOf { it.bandwidth.toLong() }.toDouble() / netCount else 0.0
        val netGuardPct = if (netCount > 0) netNodes.count { it.isGuard }.toDouble() / netCount * 100 else 0.0
        val netExitPct = if (netCount > 0) netNodes.count { it.isExit }.toDouble() / netCount * 100 else 0.0
        val netBothPct =
            if (netCount > 0) netNodes.count { it.isGuard && it.isExit }.toDouble() / netCount * 100 else 0.0

        terminal.println()
        terminal.println(table {
            borderType = BorderType.ASCII
            header { row("Group", "Count", "Avg Bandwidth", "Guard %", "Exit %", "Guard & Exit %") }
            body {
                row(
                    "Network",
                    netCount,
                    "%.2f".format(netAvgBw),
                    "%.2f%%".format(netGuardPct),
                    "%.2f%%".format(netExitPct),
                    "%.2f%%".format(netBothPct)
                )

                adversary?.let { adv ->
                    val advNodes = netNodes.filter { it.id in adv.compromisedNodes }
                    val advCount = advNodes.size
                    val advAvgBw =
                        if (advCount > 0) advNodes.sumOf { it.bandwidth.toLong() }.toDouble() / advCount else 0.0
                    val advGuardPct =
                        if (advCount > 0) advNodes.count { it.isGuard }.toDouble() / advCount * 100 else 0.0
                    val advExitPct = if (advCount > 0) advNodes.count { it.isExit }.toDouble() / advCount * 100 else 0.0
                    val advBothPct = if (advCount > 0) advNodes.count { it.isGuard && it.isExit }
                        .toDouble() / advCount * 100 else 0.0

                    row(
                        "Adversary",
                        advCount,
                        "%.2f".format(advAvgBw),
                        "%.2f%%".format(advGuardPct),
                        "%.2f%%".format(advExitPct),
                        "%.2f%%".format(advBothPct)
                    )
                }
            }
        })

        val allPaths = clients.flatMap { it.paths }
        val allBandwidths = clients.flatMap { it.bandwidths }

        val nodeUsageCount = mutableMapOf<Int, Int>()
        for (path in allPaths) {
            for (nodeId in path) {
                nodeUsageCount[nodeId] = nodeUsageCount.getOrDefault(nodeId, 0) + 1
            }
        }

        val allNodesMap = netNodes.associateBy { it.id }
        var totalEffectiveBandwidth = 0.0

        for (path in allPaths) {
            val effBws = path.map { nodeId ->
                val bw = allNodesMap[nodeId]?.bandwidth ?: 0
                val usage = nodeUsageCount[nodeId] ?: 1
                bw.toDouble() / usage
            }
            totalEffectiveBandwidth += effBws.minOrNull() ?: 0.0
        }

        val avgPathBandwidth = if (allBandwidths.isNotEmpty()) allBandwidths.average() else 0.0
        val avgEffectivePathBandwidth = if (allPaths.isNotEmpty()) totalEffectiveBandwidth / allPaths.size else 0.0
        val maxNodeUsage = nodeUsageCount.values.maxOrNull() ?: 0

        terminal.println(TextStyles.bold("\nPath Utility:"))
        terminal.println("Average Path Bandwidth (Naive): ${"%.2f".format(avgPathBandwidth)} bytes/sec")
        terminal.println("Average Effective Path Bandwidth (Load-balanced): ${"%.2f".format(avgEffectivePathBandwidth)} bytes/sec")
        terminal.println("Most Overwhelmed Node Load: $maxNodeUsage connections")

        // Gini Coefficient of Node Load (Inequality of usage/bandwidth ratio)
        val sortedRatios = netNodes.map { node ->
            val usage = nodeUsageCount[node.id] ?: 0
            if (node.bandwidth > 0) usage.toDouble() / node.bandwidth else 0.0
        }.sorted()
        val n = sortedRatios.size
        val sumRatios = sortedRatios.sum()
        val gini = if (sumRatios > 0 && n > 0) {
            val weightedSum = sortedRatios.withIndex().sumOf { (idx, x) -> (2.0 * (idx + 1) - n - 1) * x }
            weightedSum / (n * sumRatios)
        } else 0.0

        // Shannon Entropy (Selection Diversity)
        val totalUsages = nodeUsageCount.values.sum().toDouble()
        val entropy = if (totalUsages > 0) {
            nodeUsageCount.values.sumOf { count ->
                val p = count / totalUsages
                if (p > 0) -p * (ln(p) / ln(2.0)) else 0.0
            }
        } else 0.0
        val maxEntropy = if (netCount > 0) ln(netCount.toDouble()) / ln(2.0) else 0.0
        val diversityScore = if (maxEntropy > 0) entropy / maxEntropy else 0.0

        terminal.println("Gini Coefficient of Node Load: ${"%.4f".format(gini)} (0 = perfect equality, 1 = maximum inequality)")
        terminal.println(
            "Selection Diversity (Shannon Entropy): ${"%.4f".format(entropy)} bits (Score: ${
                "%.2f%%".format(
                    diversityScore * 100
                )
            })"
        )
    }
}