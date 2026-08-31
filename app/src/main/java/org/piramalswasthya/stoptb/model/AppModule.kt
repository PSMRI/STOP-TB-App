package org.piramalswasthya.stoptb.model

enum class AppModule {
    HOUSEHOLD,
    BENEFICIARIES,
    NON_HOUSEHOLD,
    TUBERCULOSIS,
    REFERRAL,

    // Multi-role "Counselling" tab only — no landing screens exist yet, ship as
    // "Coming soon" placeholder cards until real destinations are designed.
    COUNSELLING,
    CONTACT_TRACING,
    TB_TREATMENT_FOLLOWUP,
    TPT
}
