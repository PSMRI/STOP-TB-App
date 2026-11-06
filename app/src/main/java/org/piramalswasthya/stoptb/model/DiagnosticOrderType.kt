package org.piramalswasthya.stoptb.model

enum class DiagnosticOrderType(val wireValue: String) {
    XRAY_CHEST("XRAY_CHEST"),
    SPUTUM_TRUENAT("SPUTUM_TRUENAT"),
    MDR_RIF("MDR_RIF"),
    LIQUID_CULTURE("LIQUID_CULTURE");

    companion object {
        fun fromWireValue(value: String?): DiagnosticOrderType? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}
