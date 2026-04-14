package com.gourav.investnest

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// this annotation triggers the hilt code generation and serves as the application level dependency container
@HiltAndroidApp
class InvestNestApplication : Application()
