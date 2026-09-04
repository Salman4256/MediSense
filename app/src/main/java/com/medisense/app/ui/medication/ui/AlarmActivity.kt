package com.medisense.app.ui.medication.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.repository.MedicationRepository
import com.medisense.app.databinding.ActivityAlarmBinding
import com.medisense.app.notification.MedicationNotificationManager
import com.medisense.app.notification.MedicationScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    @Inject lateinit var medicationDao: MedicationDao
    @Inject lateinit var medicationRepository: MedicationRepository
    @Inject lateinit var scheduler: MedicationScheduler

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var pulseAnimator: AnimatorSet? = null

    private var medicationId: Long = 0L
    private var userId: String = ""
    private var medicineName: String = "Medication"
    private var dosage: String = ""
    private var instructions: String = ""
    private var scheduledDate: Long = 0L
    private var scheduledTime: String = ""
    private var notificationId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        setupUI()
        startAlarmAlert()
        setupListeners()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun extractIntentData() {
        medicationId = intent.getLongExtra(MedicationNotificationManager.EXTRA_MEDICATION_ID, 0L)
        userId = intent.getStringExtra(MedicationNotificationManager.EXTRA_USER_ID) ?: ""
        medicineName = intent.getStringExtra(MedicationNotificationManager.EXTRA_MEDICATION_NAME) ?: "Medication"
        dosage = intent.getStringExtra(MedicationNotificationManager.EXTRA_DOSAGE) ?: ""
        instructions = intent.getStringExtra(MedicationNotificationManager.EXTRA_INSTRUCTIONS) ?: ""
        scheduledDate = intent.getLongExtra(MedicationNotificationManager.EXTRA_SCHEDULED_DATE, System.currentTimeMillis())
        scheduledTime = intent.getStringExtra(MedicationNotificationManager.EXTRA_SCHEDULED_TIME) ?: ""
        notificationId = intent.getIntExtra(MedicationNotificationManager.EXTRA_NOTIFICATION_ID, medicationId.toInt())
    }

    private fun setupUI() {
        binding.tvAlarmMedicationName.text = medicineName
        binding.tvAlarmDosageFood.text = if (dosage.isNotBlank()) dosage else "Scheduled dose"

        if (instructions.isNotBlank()) {
            binding.tvAlarmInstructions.visibility = View.VISIBLE
            binding.tvAlarmInstructions.text = "Note: $instructions"
        } else {
            binding.tvAlarmInstructions.visibility = View.GONE
        }

        // If data was minimal, fetch rich details from DB asynchronously
        if (medicationId > 0 && (dosage.isBlank() || medicineName == "Medication")) {
            lifecycleScope.launch(Dispatchers.IO) {
                val med = medicationDao.getMedicationById(medicationId, userId)
                if (med != null) {
                    withContext(Dispatchers.Main) {
                        medicineName = med.medicineName
                        dosage = "${med.dosage} ${med.dosageUnit}"
                        instructions = med.instructions
                        binding.tvAlarmMedicationName.text = medicineName
                        binding.tvAlarmDosageFood.text = dosage
                        if (instructions.isNotBlank()) {
                            binding.tvAlarmInstructions.visibility = View.VISIBLE
                            binding.tvAlarmInstructions.text = "Note: $instructions"
                        }
                    }
                }
            }
        }

        // Pulse icon animation
        val scaleX = ObjectAnimator.ofFloat(binding.cardIconPulse, "scaleX", 1.0f, 1.15f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(binding.cardIconPulse, "scaleY", 1.0f, 1.15f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun startAlarmAlert() {
        // 1. Play Alarm Sound
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback if media player fails
        }

        // 2. Start Repeating Vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (ignored: Exception) {}
    }

    private fun stopAlarmAlert() {
        try {
            pulseAnimator?.cancel()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (ignored: Exception) {}
    }

    private fun setupListeners() {
        binding.btnAlarmTaken.setOnClickListener {
            stopAlarmAlert()
            MedicationNotificationManager.dismissNotification(this, notificationId)

            lifecycleScope.launch(Dispatchers.IO) {
                medicationRepository.recordTaken(
                    medicationId = medicationId,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime
                )
                val med = medicationDao.getMedicationById(medicationId, userId)
                if (med != null && med.active) {
                    scheduler.scheduleNextReminder(med)
                }
            }

            Toast.makeText(this, "✅ $medicineName recorded as TAKEN", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnAlarmSnooze.setOnClickListener {
            stopAlarmAlert()
            MedicationNotificationManager.dismissNotification(this, notificationId)

            scheduler.scheduleSnooze(medicationId, 10 * 60 * 1000L)

            Toast.makeText(this, "⏰ Snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnAlarmSkip.setOnClickListener {
            stopAlarmAlert()
            MedicationNotificationManager.dismissNotification(this, notificationId)

            lifecycleScope.launch(Dispatchers.IO) {
                medicationRepository.recordSkipped(
                    medicationId = medicationId,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime
                )
                val med = medicationDao.getMedicationById(medicationId, userId)
                if (med != null && med.active) {
                    scheduler.scheduleNextReminder(med)
                }
            }

            Toast.makeText(this, "⏭️ $medicineName skipped", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        stopAlarmAlert()
        super.onDestroy()
    }
}
