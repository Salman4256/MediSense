package com.medisense.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
