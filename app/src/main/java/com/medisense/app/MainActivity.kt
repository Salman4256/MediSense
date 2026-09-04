package com.medisense.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.medisense.app.data.repository.MedicationRepository
import com.medisense.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var medicationRepository: MedicationRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerMissedDoseCheck()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val bottomInset = kotlin.math.max(systemBars.bottom, ime.bottom)
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            windowInsets
        }

        checkAndRequestNotificationPermissions()
        triggerMissedDoseCheck()
    }

    override fun onResume() {
        super.onResume()
        triggerMissedDoseCheck()
    }

    private fun triggerMissedDoseCheck() {
        lifecycleScope.launch {
            try {
                medicationRepository.checkAndHandleMissedDoses(applicationContext)
            } catch (ignored: Exception) {}
        }
    }

    private fun checkAndRequestNotificationPermissions() {
        // 1. Android 13+ (API 33+) POST_NOTIFICATIONS Runtime Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Android 12+ (API 31+) SCHEDULE_EXACT_ALARM check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == false) {
                // Settings check
            }
        }
    }
}
