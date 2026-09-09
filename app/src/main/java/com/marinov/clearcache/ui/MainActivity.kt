package com.marinov.clearcache.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.marinov.clearcache.R
import com.marinov.clearcache.logic.AlarmScheduler
import com.marinov.clearcache.logic.RootHelper

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        RootHelper.requestRootPermission()
        requestBatteryOptimizationAndNotifications()
        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePowerModeState()
        AlarmScheduler.scheduleAllAggressively(this)
    }

    private fun setupUI() {
        setDashboardItem(R.id.btn_battery, R.drawable.ic_battery, getString(R.string.item_battery), getString(R.string.item_battery_desc))
        setDashboardItem(R.id.btn_storage, R.drawable.ic_storage, getString(R.string.item_storage), getString(R.string.item_storage_desc))
        setDashboardItem(R.id.btn_clean_cache, android.R.drawable.ic_menu_delete, getString(R.string.item_clean_cache), getString(R.string.item_clean_cache_desc))
        setDashboardItem(R.id.btn_power_mode, R.drawable.ic_performance, getString(R.string.item_power_mode), getString(R.string.item_power_mode_desc))
        setDashboardItem(R.id.btn_auto_reboot, R.drawable.ic_reboot, getString(R.string.item_auto_reboot), getString(R.string.item_auto_reboot_desc))
        setDashboardItem(R.id.btn_auto_clean, R.drawable.ic_auto_clean, getString(R.string.item_auto_clean), getString(R.string.item_auto_clean_desc))

        // NOVO ITEM
        setDashboardItem(R.id.btn_battery_protection, R.drawable.ic_battery_protection, getString(R.string.item_battery_protection), getString(R.string.item_battery_protection_desc))
    }

    private fun setDashboardItem(layoutId: Int, iconRes: Int, title: String, subtitle: String) {
        val container = findViewById<View>(layoutId)
        container.findViewById<ImageView>(R.id.item_icon).setImageResource(iconRes)
        container.findViewById<TextView>(R.id.item_title).text = title
        container.findViewById<TextView>(R.id.item_subtitle).text = subtitle
    }

    private fun setupListeners() {
        findViewById<LinearLayout>(R.id.btn_battery).setOnClickListener {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            if (intent.resolveActivity(packageManager) != null) startActivity(intent) else startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        }
        findViewById<LinearLayout>(R.id.btn_storage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        }
        findViewById<LinearLayout>(R.id.btn_clean_cache).setOnClickListener {
            startActivity(Intent(this, CleanCacheDialogActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btn_auto_reboot).setOnClickListener {
            startActivity(Intent(this, RebootActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btn_auto_clean).setOnClickListener {
            startActivity(Intent(this, AutoCleanCacheActivity::class.java))
        }

        // NOVO LISTENER
        findViewById<LinearLayout>(R.id.btn_battery_protection).setOnClickListener {
            startActivity(Intent(this, BatteryProtectionActivity::class.java))
        }
    }

    private fun updatePowerModeState() {
        val btnPowerMode = findViewById<LinearLayout>(R.id.btn_power_mode)
        val powerAppPackage = "com.marinov.powermanagement"
        val intent = packageManager.getLaunchIntentForPackage(powerAppPackage)

        if (intent != null) {
            btnPowerMode.alpha = 1.0f
            btnPowerMode.isEnabled = true
            btnPowerMode.setOnClickListener { startActivity(intent) }
        } else {
            btnPowerMode.alpha = 0.5f
            btnPowerMode.isEnabled = false
            btnPowerMode.setOnClickListener(null)
        }
    }

    private fun requestBatteryOptimizationAndNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try { startActivity(intent) } catch (e: Exception) { Log.e("Main", "Erro ao pedir restrição de bateria", e) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}