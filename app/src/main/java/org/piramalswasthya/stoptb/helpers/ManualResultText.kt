package org.piramalswasthya.stoptb.helpers

/**
 * The result text to store locally for a manually entered diagnostic result.
 *
 * An Invalid/Error result triggers an automatic repeat order, and once that order exists the old
 * text must not outlive it on the card - so it is normally dropped at the moment the result is
 * stored.
 *
 * That is wrong while the entry is still queued for replay. No repeat order has been raised yet
 * (the cascade waits for the backend to accept), and the stored text is the only thing
 * TBRepo.retryPendingManualResultSyncs has to submit. Dropping it there left the pending-sync
 * flag set with nothing to send: the replay skipped the row on every sweep, the result was never
 * submitted, and the repeat order the Invalid/Error exists to trigger was never raised either.
 *
 * So: keep the text while the entry is pending, and drop it once the backend has accepted it -
 * which is the same moment the repeat order is raised.
 */
fun manualResultTextToStore(
    localResult: String?,
    isInvalidOrError: Boolean,
    pendingSync: Boolean
): String? = if (isInvalidOrError && !pendingSync) null else localResult
