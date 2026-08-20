package org.piramalswasthya.stoptb.helpers

import android.text.InputFilter
import android.text.Spanned

class LatinInputFilter : InputFilter {
    private val blockedPattern = Regex(
        "[^" +
                "A-Z" +
                "a-z" +
                "0-9" +
                " .,_@/()\n!#\$%&+=:;'\"?-" +
                "]"
    )

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val chunk = source.subSequence(start, end).toString()
        val filtered = chunk.replace(blockedPattern, "")
        return if (filtered.length == chunk.length) null else filtered
    }
}
