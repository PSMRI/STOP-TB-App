package org.piramalswasthya.stoptb.configuration

import android.text.InputFilter
import android.text.Spanned

/** Allows letters, digits and spaces, including when pasting into the field. */
object AlphanumericInputFilter : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val input = source.subSequence(start, end).toString()
        val filtered = input.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == ' ' }
        return if (filtered == input) null else filtered
    }
}
