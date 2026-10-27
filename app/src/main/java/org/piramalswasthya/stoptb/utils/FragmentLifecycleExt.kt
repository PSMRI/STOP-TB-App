package org.piramalswasthya.stoptb.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Collects [block] only while the fragment's VIEW is at least STARTED.
 *
 * Use this instead of `lifecycleScope.launch { flow.collect { ... } }` in onViewCreated.
 * Fragment.lifecycleScope outlives the view: every time the user navigates to a form and back,
 * onViewCreated runs again and the old collectors keep running, so each DB change is processed
 * N times. This scope is cancelled in onDestroyView and paused while the screen is not visible.
 */
fun Fragment.launchOnView(block: suspend CoroutineScope.() -> Unit): Job =
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
