package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "t_dynamic_form")
data class DynamicFormEntity(
    @PrimaryKey val formId: Int,
    val formUuid: String,
    val formName: String,
    val formType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val followUpDelayDays : Int = 15,
    // Contact Tracing fields (nullable, unused by existing forms)
    val definition: String? = null,
    val triggerRuleJson: String? = null,
    val globalRuleJson: String? = null,
    val enabledIfJson: String? = null
)
