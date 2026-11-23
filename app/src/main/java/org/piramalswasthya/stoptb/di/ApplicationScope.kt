package org.piramalswasthya.stoptb.di

import javax.inject.Qualifier

/**
 * A process-wide CoroutineScope (SupervisorJob + Dispatchers.Default), provided by AppModule.
 *
 * Use it to share Room Flows between screens with
 * `shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)`: one query runs per
 * table invalidation no matter how many collectors there are, and it stops 5 s after the last
 * collector goes away. Don't launch long-running work in it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
