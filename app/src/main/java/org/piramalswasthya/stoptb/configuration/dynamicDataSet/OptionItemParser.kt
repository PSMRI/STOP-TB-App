package org.piramalswasthya.stoptb.configuration.dynamicDataSet

import org.piramalswasthya.stoptb.model.dynamicEntity.FormFieldDto
import org.piramalswasthya.stoptb.model.dynamicEntity.OptionItem


object OptionItemParser {

    fun parse(raw: Any?): List<OptionItem>? {
        if (raw == null) return null
        if (raw !is List<*>) return null

        val result = raw.mapNotNull { item ->
            when (item) {
                is OptionItem -> item
                is Map<*, *> -> {
                    val value = item["value"]?.toString() ?: return@mapNotNull null
                    val label = item["label"]?.toString() ?: value
                    OptionItem(label = label, value = value)
                }
                is String -> OptionItem(label = item, value = item)
                else -> null
            }
        }
        return result.ifEmpty { null }
    }
}

fun FormFieldDto.optionItems(): List<OptionItem>? = OptionItemParser.parse(options)
 