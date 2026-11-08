package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.parse.cmd.CommandAnalyse
import io.github.aidan_es.torpathsim.parse.cmd.CommandData
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.terminal.Terminal

val terminal = Terminal()

class TorPathSimCommand : NoOpCliktCommand(name = "TorPathSim") {
    override fun help(context: Context): String = "TorPathSim: Game-theoretic Tor path simulation tool"
}

fun main(args: Array<String>) {
    val nodeDataManager = NodeDataManager()
    TorPathSimCommand()
        .subcommands(CommandData(), CommandAnalyse(nodeDataManager))
        .main(args)
}
