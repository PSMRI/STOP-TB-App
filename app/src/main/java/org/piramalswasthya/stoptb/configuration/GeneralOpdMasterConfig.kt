package org.piramalswasthya.stoptb.configuration

/** Release switch for General OPD master data. See docs/General-OPD-Master-Mode.md. */
object GeneralOpdMasterConfig {
    // Enable only when the next release is ready to use backend master IDs.
    // false: bundled lists only; no master API requests or API-cache reads/writes.
    // true: API refresh + cached masters, with bundled lists as fallback.
    const val USE_API_MASTERS = false
}
