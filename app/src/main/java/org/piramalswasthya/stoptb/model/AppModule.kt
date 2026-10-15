package org.piramalswasthya.stoptb.model

enum class AppModule {
    HOUSEHOLD,
    BENEFICIARIES,
    NON_HOUSEHOLD,
    TUBERCULOSIS,
    REFERRAL,

    // Counselling-family modules — Counsellor role only.
    COUNSELLING,
    CONTACT_TRACING,
    TB_TREATMENT_FOLLOWUP,
    TPT
}
