package stochastic.policy

import com.google.common.truth.Truth
import core.cartesianProduct
import core.policy.GreedyLocalFirstPolicy
import core.policy.GreedyOffloadFirstPolicy
import core.policy.LocalOnlyPolicy
import core.policy.TransmitOnlyPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import simulation.simulation.Simulator
import stochastic.lp.RangedOptimalPolicyFinder

sealed class AlphaRange {

    abstract fun toList(): List<Double>

    data class Constant(val value: Double) : AlphaRange() {
        init {
            require(value > 0 && value <= 1.0)
        }

        override fun toList(): List<Double> = listOf(value)
    }

    data class Variable(val start: Double, val end: Double, val count: Int) : AlphaRange() {
        init {
            require(start > 0 && start <= 1.0)
            require(end > 0 && end <= 1.0)
            if (count == 1) {
                require(start == end)
            }
        }

        override fun toList(): List<Double> {
            if (count == 1) {
                return listOf(start)
            }
            return (0 until count).map { i ->
                start + i * ((end - start) / (count - 1))
            }
        }
    }
}

class MultiQueueRangedAlphaTester (
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaRanges: List<AlphaRange>,
    private val precision: Int,
    private val simulationTicks: Int,
    private val assertionsEnabled: Boolean
) {

    init {
        require(alphaRanges.isNotEmpty())
    }

    data class Result(
        val alphaRanges: List<AlphaRange>,
        val localOnlyDelays: List<Double>,
        val offloadOnlyDelays: List<Double>,
        val greedyOffloadFirstDelays: List<Double>,
        val greedyLocalFirstDelays: List<Double>,
        val stochasticDelays: List<Double>,
    )

    fun run(): Result {
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })

        val localOnlyDelays: MutableList<Double> = mutableListOf()
        val offloadOnlyDelays: MutableList<Double> = mutableListOf()
        val greedyOffloadFirstDelays: MutableList<Double> = mutableListOf()
        val greedyLocalFirstDelays: MutableList<Double> = mutableListOf()
        val stochasticDelays: MutableList<Double> = mutableListOf()

        alphaCombinations.forEachIndexed { index, alpha: List<Double> ->
            val delayResult: AlphaDelayResult = getDelaysForAlpha(alpha)

            if (assertionsEnabled) {
                validateAlphaDelayResult(delayResult)
            }

            with(delayResult) {
                localOnlyDelays.add(localOnlyDelay)
                offloadOnlyDelays.add(localOnlyDelay)
                greedyOffloadFirstDelays.add(greedyOffloadFirstDelay)
                greedyLocalFirstDelays.add(greedyLocalFirstDelay)
                stochasticDelays.add(stochasticDelay)
            }
        }

        return Result(
            alphaRanges = alphaRanges,
            localOnlyDelays = localOnlyDelays,
            offloadOnlyDelays = offloadOnlyDelays,
            greedyOffloadFirstDelays = greedyOffloadFirstDelays,
            greedyLocalFirstDelays = greedyLocalFirstDelays,
            stochasticDelays = stochasticDelays
        )
    }

    private fun validateAlphaDelayResult(delayResult: AlphaDelayResult) {
        val errorWindowMultiplier = 0.99
        with(Truth.assertThat(delayResult.stochasticDelay * errorWindowMultiplier)) {
            isLessThan(delayResult.localOnlyDelay)
            isLessThan(delayResult.offloadOnlyDelay)
            isLessThan(delayResult.greedyOffloadFirstDelay)
            isLessThan(delayResult.greedyLocalFirstDelay)
        }
    }

    private fun getDelaysForAlpha(alpha: List<Double>): AlphaDelayResult {
        val config = baseSystemConfig.withAlpha(alpha)
        val simulator = Simulator(config)
        val stochastic = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config, precision)
        println("Running simulations for alpha = $alpha")

        val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks).averageDelay
        val offloadOnlyDelay = simulator.simulatePolicy(TransmitOnlyPolicy, simulationTicks).averageDelay
        val greedyOffloadFirstDelay =
            simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks).averageDelay
        val greedyLocalFirstDelay =
            simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay
        val stochasticDelay = simulator.simulatePolicy(stochastic, simulationTicks).averageDelay

        return AlphaDelayResult(
            localOnlyDelay = localOnlyDelay,
            offloadOnlyDelay = offloadOnlyDelay,
            greedyOffloadFirstDelay = greedyOffloadFirstDelay,
            greedyLocalFirstDelay = greedyLocalFirstDelay,
            stochasticDelay = stochasticDelay
        )
    }

    internal data class AlphaDelayResult(
        val localOnlyDelay: Double,
        val offloadOnlyDelay: Double,
        val greedyOffloadFirstDelay: Double,
        val greedyLocalFirstDelay: Double,
        val stochasticDelay: Double
    )
}
