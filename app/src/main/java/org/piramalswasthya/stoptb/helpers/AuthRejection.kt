package org.piramalswasthya.stoptb.helpers

/**
 * Common-API login statusCodes that mean the credentials or the account were refused
 * (OutputResponse USERID_FAILURE, PASSWORD_FAILURE, PREVILAGE_FAILURE). Any other failure
 * (5000/5005-5007, a malformed or missing body) is transient and must not clear the login.
 */
val AUTH_REJECTED_STATUS_CODES = setOf(5002, 5003, 5004)

/** The server refused this login (bad credentials, locked account, or another user). */
class AuthRejectedException(message: String) : IllegalStateException(message)
