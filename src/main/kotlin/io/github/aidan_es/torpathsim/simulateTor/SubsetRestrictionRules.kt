package io.github.aidan_es.torpathsim.simulateTor

@JvmRecord
data class SubsetRestrictionRules(
    val subsetRestrictionFlags: SubsetRestrictionFlags?,
    val minimumBandwidth: Int,
    val maximumBandwidth: Int,
    val bandwidthRule: BandwidthRule?
)
