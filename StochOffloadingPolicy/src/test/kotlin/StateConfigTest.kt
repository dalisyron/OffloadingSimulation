import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import ue.UserEquipmentStateConfig
import ue.UserEquipmentStateConfig.Companion.allStates

class StateConfigTest {
    private val sampleStateConfig1 = UserEquipmentStateConfig(
        taskQueueCapacity = 5,
        tuNumberOfPackets = 4,
        cpuNumberOfSections = 3
    )

    @Test
    fun stateUniquenessTest() {
        val states = sampleStateConfig1.allStates()

        assertThat(states)
            .containsNoDuplicates()
    }
}