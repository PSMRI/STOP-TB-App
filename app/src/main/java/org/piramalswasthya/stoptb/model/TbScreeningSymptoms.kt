package org.piramalswasthya.stoptb.model

/**
 * The only part of TB_SCREENING the beneficiary lists read: the ten symptom answers plus the
 * stored asymptomatic fallback, which together decide the "Presumptive / Asymptomatic" label on
 * a card.
 *
 * The lists used to observe `SELECT * FROM TB_SCREENING` and materialise the whole 50-column
 * entity for every beneficiary on the device - 9,987 of them on a camp tablet - including four
 * `List<>` columns that each cost a TypeConverter parse per row. None of that ever reached the
 * screen. Projecting to these eleven values keeps the same query observable without the width.
 */
data class TbScreeningSymptoms(
    val benId: Long,
    val coughMoreThan2Weeks: Boolean?,
    val bloodInSputum: Boolean?,
    val feverMoreThan2Weeks: Boolean?,
    val riseOfFever: Boolean?,
    val lossOfAppetite: Boolean?,
    val lossOfWeight: Boolean?,
    val nightSweats: Boolean?,
    val historyOfTb: Boolean?,
    val takingAntiTBDrugs: Boolean?,
    val familySufferingFromTB: Boolean?,
    val asymptomatic: String?
)
