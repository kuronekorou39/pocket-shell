package com.rou39.pocketshell

import android.app.Application
import com.rou39.pocketshell.adb.AdbShell
import com.rou39.pocketshell.data.RecipeStore
import com.rou39.pocketshell.data.SettingsStore

class PocketShellApp : Application() {
    lateinit var recipes: RecipeStore
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        AdbShell.init(this)
        recipes = RecipeStore(this)
        settings = SettingsStore(this)
    }
}
