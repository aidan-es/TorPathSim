package io.github.aidan_es.torpathsim.parse.config

import io.github.aidan_es.torpathsim.simulateTor.BandwidthRule

object BandwidthRuleConverter {
    fun convert(value: String, name: String = "rule"): BandwidthRule {
        return when (value.lowercase()) {
            "greater" -> BandwidthRule.GREATER_THAN
            "less" -> BandwidthRule.LESS_THAN
            "irrelevant" -> BandwidthRule.IRRELEVANT
            else -> throw IllegalArgumentException("Option '$name' must be one of 'greater', 'less', or 'irrelevant', but got '$value'")
        }
    }
}
