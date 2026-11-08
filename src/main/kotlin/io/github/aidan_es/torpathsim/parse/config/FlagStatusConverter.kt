package io.github.aidan_es.torpathsim.parse.config

import io.github.aidan_es.torpathsim.simulateTor.FlagStatus

object FlagStatusConverter {
    fun convert(value: String, name: String = "flag"): FlagStatus {
        return when (value.lowercase()) {
            "y", "yes", "t", "true", "1", "positive" -> FlagStatus.POSITIVE
            "n", "no", "f", "false", "0", "negative" -> FlagStatus.NEGATIVE
            else -> FlagStatus.IRRELEVANT
        }
    }
}
