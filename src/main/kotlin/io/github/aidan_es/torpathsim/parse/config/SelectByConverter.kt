package io.github.aidan_es.torpathsim.parse.config

import io.github.aidan_es.torpathsim.simulateTor.SelectBy

object SelectByConverter {
    fun convert(value: String, name: String = "selectBy"): SelectBy {
        return when (value.lowercase()) {
            "bandwidth" -> SelectBy.BANDWIDTH
            "nodes" -> SelectBy.NODES
            else -> throw IllegalArgumentException("Option '$name' must be 'bandwidth' or 'nodes', but got '$value'")
        }
    }
}
