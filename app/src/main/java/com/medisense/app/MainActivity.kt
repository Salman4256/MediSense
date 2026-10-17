package com.medisense.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = kotlin.math.max(insets.bottom, ime.bottom)
            view.setPadding(insets.left, insets.top, insets.right, bottomInset)
            windowInsets
        }

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
}
