package com.livesac.manager

import android.app.Application
import com.livesac.manager.data.AppDatabase
import com.livesac.manager.data.PrefsSecretStore
import com.livesac.manager.data.Repository
import com.livesac.manager.domain.AdminAuth

class LiveSacApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }
    val adminAuth: AdminAuth by lazy { AdminAuth(PrefsSecretStore(this)) }
}
