package io.github.aidan_es.torpathsim.parse.cmd

import io.github.aidan_es.torpathsim.AnalyseController
import io.github.aidan_es.torpathsim.NodeDataManager
import io.github.aidan_es.torpathsim.terminal
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.red
import java.io.File

open class CommandAnalyse(private val nodeDataManager: NodeDataManager = NodeDataManager()) : CliktCommand(name = "analyse") {
    override fun help(context: Context): String = "Analyse a dataset"

    val ld: File by option(
        "-l", "-ld", "--loadData",
        help = "Path to the dataset to load"
    ).file().required()

    val rules: File by option(
        "-r", "--rules",
        help = "Path to the rules configuration file"
    ).file().required()

    val adversaryStrategy: File by option(
        "-a", "--advstrat",
        help = "Path to the adversary strategy file"
    ).file().required()

    val clients: Int by option(
        "-c", "--clients",
        help = "Number of clients to simulate"
    ).int().default(50)

    val paths: Int by option(
        "-p", "--paths",
        help = "Number of paths per client"
    ).int().default(10)

    override fun run() {
        try {
            AnalyseController(nodeDataManager).parseArgs(this)
        } catch (e: Exception) {
            terminal.println(red("Analysis Error: ${e.message}"))
            throw ProgramResult(1)
        }
    }
}
