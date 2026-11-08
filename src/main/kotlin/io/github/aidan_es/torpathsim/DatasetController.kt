package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.parse.cmd.CommandData
import com.github.ajalt.mordant.rendering.TextColors
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DatasetController {
    private val directoryServers: Map<String, String> by lazy {
        loadDirectoryAuthorities()
    }

    private fun loadDirectoryAuthorities(): Map<String, String> {
        val file = File("configs/directory_authorities.toml")
        if (!file.exists()) {
            terminal.println(TextColors.yellow("Warning: 'configs/directory_authorities.toml' not found. Using default authorities."))
            return mapOf(
                "moria1" to "128.31.0.39:9131",
                "maatuska" to "171.25.193.9:443",
                "serge" to "66.111.2.131:9030",
                "tor26" to "217.196.147.77:80",
                "faravahar" to "216.218.219.41:80",
                "dannenberg" to "193.23.244.244:80",
                "dizum" to "45.66.33.45:80",
                "bastet" to "204.13.164.118:80",
                "gabelmoo" to "131.188.40.189:80",
                "longclaw" to "199.58.81.140:80"
            )
        }

        val authorities = mutableMapOf<String, String>()
        file.forEachLine { line ->
            val cleanLine = line.substringBefore("#").trim()
            if (cleanLine.isNotEmpty() && cleanLine.contains("=")) {
                val parts = cleanLine.split("=", limit = 2)
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim().trim('"')
                authorities[key] = value
            }
        }
        terminal.println(TextColors.gray("Loaded ${authorities.size} directory authorities from config."))
        return authorities
    }

    fun parseArgs(commandData: CommandData) {
        val downloadSource = commandData.dl
        val fileLocation = commandData.sv

        val serverIP = directoryServers[downloadSource.lowercase()] ?: downloadSource

        try {
            downloadConsensus(serverIP, fileLocation)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Failed to download consensus from $serverIP: ${e.message}", e)
        }

        terminal.println(TextColors.green("Download complete. Run 'analyse' to process this file."))
    }

    private fun downloadConsensus(serverIP: String, fileLocation: File) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
        val request = HttpRequest.newBuilder()
            .uri(URI("http://$serverIP/tor/status-vote/current/consensus"))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()

        val tempFile = File.createTempFile("consensus_", ".tmp")
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()))
            if (response.statusCode() != 200) {
                throw IOException("Server $serverIP returned HTTP ${response.statusCode()}")
            }

            val firstLine = tempFile.useLines { it.firstOrNull()?.trim() ?: "" }
            if (!firstLine.startsWith("network-status-version")) {
                throw IOException(
                    "Response from '$serverIP' is not a valid Tor consensus document " +
                        "(connection may have been intercepted or blocked by ISP/network filter). " +
                        "Try another authority such as 'moria1' or 'maatuska'."
                )
            }

            if (fileLocation.exists()) {
                fileLocation.delete()
            }
            tempFile.copyTo(fileLocation, overwrite = true)
        } finally {
            tempFile.delete()
        }
    }
}