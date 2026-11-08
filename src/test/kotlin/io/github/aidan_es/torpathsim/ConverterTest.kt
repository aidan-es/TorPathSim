package io.github.aidan_es.torpathsim

import io.github.aidan_es.torpathsim.parse.cmd.CommandAnalyse
import io.github.aidan_es.torpathsim.parse.cmd.CommandData
import io.github.aidan_es.torpathsim.parse.config.BandwidthRuleConverter
import io.github.aidan_es.torpathsim.parse.config.FlagStatusConverter
import io.github.aidan_es.torpathsim.parse.config.SelectByConverter
import io.github.aidan_es.torpathsim.simulateTor.BandwidthRule
import io.github.aidan_es.torpathsim.simulateTor.FlagStatus
import io.github.aidan_es.torpathsim.simulateTor.SelectBy
import com.github.ajalt.clikt.core.parse
import java.io.File
import kotlin.test.*

class ConverterTest {

    @Test
    fun testBandwidthRuleConverterValidInputs() {
        assertEquals(BandwidthRule.GREATER_THAN, BandwidthRuleConverter.convert("greater", "rule"))
        assertEquals(BandwidthRule.GREATER_THAN, BandwidthRuleConverter.convert("GREATER", "rule"))
        assertEquals(BandwidthRule.LESS_THAN, BandwidthRuleConverter.convert("less", "rule"))
        assertEquals(BandwidthRule.LESS_THAN, BandwidthRuleConverter.convert("LESS", "rule"))
        assertEquals(BandwidthRule.IRRELEVANT, BandwidthRuleConverter.convert("irrelevant", "rule"))
        assertEquals(BandwidthRule.IRRELEVANT, BandwidthRuleConverter.convert("IRRELEVANT", "rule"))
    }

    @Test
    fun testBandwidthRuleConverterInvalidInputs() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthRuleConverter.convert("invalid", "rule")
        }
        assertFailsWith<IllegalArgumentException> {
            BandwidthRuleConverter.convert("", "rule")
        }
    }

    @Test
    fun testFlagStatusConverterPositive() {
        val positives = listOf("y", "yes", "t", "true", "1", "positive", "TRUE", "Yes", "POSITIVE")
        for (v in positives) {
            assertEquals(FlagStatus.POSITIVE, FlagStatusConverter.convert(v, "flag"), "Expected POSITIVE for '$v'")
        }
    }

    @Test
    fun testFlagStatusConverterNegative() {
        val negatives = listOf("n", "no", "f", "false", "0", "negative", "FALSE", "No", "NEGATIVE")
        for (v in negatives) {
            assertEquals(FlagStatus.NEGATIVE, FlagStatusConverter.convert(v, "flag"), "Expected NEGATIVE for '$v'")
        }
    }

    @Test
    fun testFlagStatusConverterIrrelevant() {
        val neutrals = listOf("irrelevant", "any", "foo", "", "unknown", "i")
        for (v in neutrals) {
            assertEquals(FlagStatus.IRRELEVANT, FlagStatusConverter.convert(v, "flag"), "Expected IRRELEVANT for '$v'")
        }
    }

    @Test
    fun testSelectByConverterValid() {
        assertEquals(SelectBy.BANDWIDTH, SelectByConverter.convert("bandwidth", "selectBy"))
        assertEquals(SelectBy.BANDWIDTH, SelectByConverter.convert("BANDWIDTH", "selectBy"))
        assertEquals(SelectBy.NODES, SelectByConverter.convert("nodes", "selectBy"))
        assertEquals(SelectBy.NODES, SelectByConverter.convert("NODES", "selectBy"))
    }

    @Test
    fun testSelectByConverterInvalid() {
        assertFailsWith<IllegalArgumentException> {
            SelectByConverter.convert("percent", "selectBy")
        }
        assertFailsWith<IllegalArgumentException> {
            SelectByConverter.convert("", "selectBy")
        }
    }

    @Test
    fun testCommandDataParsing() {
        val cmd = object : CommandData() {
            override fun run() {}
        }
        cmd.parse(listOf("-dl", "moria1", "-sv", "test_consensus"))
        assertEquals("moria1", cmd.dl)
        assertEquals(File("test_consensus"), cmd.sv)
    }

    @Test
    fun testCommandAnalyseParsing() {
        val cmd = object : CommandAnalyse() {
            override fun run() {}
        }
        cmd.parse(listOf("-ld", "test_consensus", "-r", "configs/modern_tor_config.txt", "-a", "configs/standard_adversary.txt", "-c", "100", "-p", "5"))
        assertEquals(File("test_consensus"), cmd.ld)
        assertEquals(File("configs/modern_tor_config.txt"), cmd.rules)
        assertEquals(File("configs/standard_adversary.txt"), cmd.adversaryStrategy)
        assertEquals(100, cmd.clients)
        assertEquals(5, cmd.paths)
    }
}
