package org.piramalswasthya.stoptb.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrderEnumsTest {

    @Test
    fun `OrderAction names match the values the quick form and nav arg rely on`() {
        assertThat(OrderAction.entries.map { action -> action.name }).containsExactly(
            "REFER", "COMPLETE", "NOT_CONDUCTED", "REORDER", "COMPLETE_RIF", "POLL",
            "RETRY_POLL", "RETRY_PUSH", "CHECK_PUSH_ACTIVE", "RETRY_RIF_POLL", "VIEW",
            "VIEW_RIF", "ENTER_LC", "VIEW_LC", "NONE", "NO_RESULT"
        )
    }

    @Test
    fun `OrderAction fromName round-trips and rejects unknown or null`() {
        OrderAction.entries.forEach { action ->
            assertThat(OrderAction.fromName(action.name)).isEqualTo(action)
        }
        assertThat(OrderAction.fromName("complete")).isNull()
        assertThat(OrderAction.fromName("")).isNull()
        assertThat(OrderAction.fromName(null)).isNull()
    }

    @Test
    fun `DiagnosticOrderType wire values match the backend testType strings`() {
        assertThat(DiagnosticOrderType.XRAY_CHEST.wireValue).isEqualTo("XRAY_CHEST")
        assertThat(DiagnosticOrderType.SPUTUM_TRUENAT.wireValue).isEqualTo("SPUTUM_TRUENAT")
        assertThat(DiagnosticOrderType.MDR_RIF.wireValue).isEqualTo("MDR_RIF")
        assertThat(DiagnosticOrderType.LIQUID_CULTURE.wireValue).isEqualTo("LIQUID_CULTURE")
        assertThat(DiagnosticOrderType.entries).hasSize(4)
    }

    @Test
    fun `DiagnosticOrderType fromWireValue is case-insensitive like the old string checks`() {
        DiagnosticOrderType.entries.forEach { orderType ->
            assertThat(DiagnosticOrderType.fromWireValue(orderType.wireValue)).isEqualTo(orderType)
            assertThat(DiagnosticOrderType.fromWireValue(orderType.wireValue.lowercase())).isEqualTo(orderType)
        }
        assertThat(DiagnosticOrderType.fromWireValue("UNKNOWN")).isNull()
        assertThat(DiagnosticOrderType.fromWireValue(null)).isNull()
    }
}
