package stochastic.dtmc

import com.google.common.truth.Truth.assertThat
import core.UserEquipmentStateManager
import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withUserEquipmentStateConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.symbol.ParameterSymbol
import core.symbol.Symbol
import org.junit.jupiter.api.Test
import core.policy.Action
import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig

class TestIndependentTransitionSymbolsCalculator {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters.singleQueue(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 5,
                tuNumberOfPackets = 4,
                cpuNumberOfSections = 3
            ),
            componentsConfig = UserEquipmentComponentsConfig.singleQueue(
                alpha = 0.1,
                beta = 0.9,
                etaConfig = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLocal = 1.5,
                pMax = 500.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters
        )

        return systemCofig
    }

    fun getSymbolMapping(systemConfig: OffloadingSystemConfig): Map<Symbol, Double> {
        val symbolMapping: Map<Symbol, Double> = mapOf(
            ParameterSymbol.Beta to systemConfig.beta,
            ParameterSymbol.BetaC to 1.0 - systemConfig.beta,
            ParameterSymbol.Alpha.singleQueue() to systemConfig.alpha.first(),
            ParameterSymbol.AlphaC.singleQueue() to 1.0 - systemConfig.alpha.first()
        )
        return symbolMapping
    }

    @Test
    fun testcase1() {
        val systemConfig = getSimpleConfig()
        val userEquipmentStateManager = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
        val symbolMapping = getSymbolMapping(systemConfig)
        val discreteTimeMarkovChain: DiscreteTimeMarkovChain = DTMCCreator(systemConfig.getStateManagerConfig()).create()
        val transitionCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)

        val itValue = transitionCalculator.getIndependentTransitionFraction(
            UserEquipmentState.singleQueue(2, 0, 0),
            UserEquipmentState.singleQueue(1, 1, 0),
            Action.AddToTransmissionUnit.singleQueue()
        )

        val expectedResult = (1.0 - systemConfig.beta) * (1.0 - systemConfig.alpha.first())

        assertThat(itValue)
            .isWithin(1e-6)
            .of(expectedResult)
    }

    @Test
    fun testDoubleLabel() {
        val stateConfig = UserEquipmentStateConfig.singleQueue(
            taskQueueCapacity = 10, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        )
        val systemConfig = getSimpleConfig().withUserEquipmentStateConfig(stateConfig)
        val symbolMapping = getSymbolMapping(systemConfig)

        val userEquipmentStateManager = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
        val discreteTimeMarkovChain: DiscreteTimeMarkovChain = DTMCCreator(systemConfig.getStateManagerConfig()).create()
        val itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)

        val itValue = itCalculator.getIndependentTransitionFraction(
            source = UserEquipmentState.singleQueue(1, 0, 0),
            dest = UserEquipmentState.singleQueue(1, 0, 0),
            action = Action.AddToTransmissionUnit.singleQueue()
        )
        val expectedValue = systemConfig.alpha.first() * systemConfig.beta

        assertThat(itValue)
            .isWithin(1e-6)
            .of(expectedValue)
    }

    private fun List<Symbol>.makeUnique() = sortedBy { it.toString() }
}