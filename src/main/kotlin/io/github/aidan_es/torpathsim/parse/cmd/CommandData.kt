package io.github.aidan_es.torpathsim.parse.cmd

import io.github.aidan_es.torpathsim.DatasetController
import io.github.aidan_es.torpathsim.terminal
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.red
import java.io.File

open class CommandData : CliktCommand(name = "data") {
    override fun help(context: Context): String = "Download and process consensus data"

    val dl: String by option(
        "-d", "-dl", "--downloadSource",
        help = "Directory server name or IP to download from"
    ).required()

    val sv: File by option(
        "-s", "-sv", "--saveLocation",
        help = "File path to save the downloaded data"
    ).file().required()

    override fun run() {
        try {
            DatasetController().parseArgs(this)
        } catch (e: Exception) {
            terminal.println(red("Error: ${e.message}"))
            throw ProgramResult(1)
        }
    }
}
