package com.example.repository

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun appCheckProvider(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
