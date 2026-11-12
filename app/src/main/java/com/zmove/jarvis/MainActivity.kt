package com.zmove.jarvis

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zmove.jarvis.databinding.ActivityMainBinding
import com.zmove.jarvis.databinding.DialogAccessKeyBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "MainActivity"
        const val WAKE_WORD_MIC_DELAY_MS = 700L
        const val PICOVOICE_CONSOLE_URL = "https://console.picovoice.ai/"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: WakeWordPreferences
    private val commandProcessor = CommandProcessor()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var pendingAction: (() -> Unit)? = null
    private var updatingSwitch = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val action = pendingAction
            pendingAction = null
            if (result[Manifest.permission.RECORD_AUDIO] == true) {
                action?.invoke()
            } else {
                showStatus(getString(R.string.status_permission_denied))
                syncAlwaysListeningSwitch()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = WakeWordPreferences(this)

        binding.btnOuvir.setOnClickListener { requestPermissionsThenListen() }
        binding.switchSempre.setOnCheckedChangeListener { _, checked ->
            onAlwaysListeningChanged(checked)
        }
        binding.btnEngine.setOnClickListener { showEngineDialog() }
        binding.btnBattery.setOnClickListener { requestBatteryOptimizationExemption() }

        syncAlwaysListeningSwitch()

        if (intent?.getBooleanExtra(JarvisService.EXTRA_WAKE_WORD, false) == true) {
            binding.root.postDelayed({ requestPermissionsThenListen() }, WAKE_WORD_MIC_DELAY_MS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(JarvisService.EXTRA_WAKE_WORD, false)) {
            binding.root.postDelayed({ requestPermissionsThenListen() }, WAKE_WORD_MIC_DELAY_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        syncAlwaysListeningSwitch()
    }

    override fun onStop() {
        stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun onAlwaysListeningChanged(checked: Boolean) {
        if (updatingSwitch) return

        if (!checked) {
            stopAlwaysListening()
            return
        }

        if (preferences.engine == WakeWordEngineType.PORCUPINE && !preferences.hasAccessKey) {
            showAccessKeyDialog(startAfterSave = true, selectPorcupine = true)
            return
        }

        withAudioPermission { startAlwaysListening() }
    }

    private fun startAlwaysListening() {
        preferences.alwaysListening = true
        val intent = Intent(this, JarvisService::class.java)
            .setAction(JarvisService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        syncAlwaysListeningSwitch()
        showStatus(getString(R.string.status_always_on))
    }

    private fun stopAlwaysListening() {
        preferences.alwaysListening = false
        stopService(Intent(this, JarvisService::class.java))
        syncAlwaysListeningSwitch()
        showStatus(getString(R.string.status_always_off))
    }

    private fun restartAlwaysListening() {
        if (!preferences.alwaysListening) return
        stopService(Intent(this, JarvisService::class.java))
        binding.root.postDelayed({
            if (!preferences.alwaysListening) return@postDelayed
            val intent = Intent(this, JarvisService::class.java)
                .setAction(JarvisService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
        }, 400L)
    }

    private fun syncAlwaysListeningSwitch() {
        updatingSwitch = true
        binding.switchSempre.isChecked = preferences.alwaysListening
        updatingSwitch = false

        val engine = preferences.engine
        binding.btnBattery.isVisible = preferences.alwaysListening
        binding.btnEngine.text = getString(
            R.string.engine_button_format,
            getString(engineLabelRes(engine))
        )
        binding.txtWakeWordMode.text = when {
            !preferences.alwaysListening -> getString(R.string.wake_word_mode_off)
            engine == WakeWordEngineType.SHERPA -> getString(R.string.wake_word_mode_sherpa)
            engine == WakeWordEngineType.PORCUPINE -> getString(R.string.wake_word_mode_porcupine)
            else -> getString(R.string.wake_word_mode_speech)
        }
    }

    private fun showEngineDialog() {
        val engines = WakeWordEngineType.entries
        val labels = engines.map { getString(engineLabelRes(it)) }.toTypedArray()
        val selected = engines.indexOf(preferences.engine).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.engine_dialog_title)
            .setMessage(R.string.engine_dialog_message)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                selectEngine(engines[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectEngine(engine: WakeWordEngineType) {
        if (engine == WakeWordEngineType.PORCUPINE && !preferences.hasAccessKey) {
            showAccessKeyDialog(
                startAfterSave = preferences.alwaysListening,
                selectPorcupine = true
            )
            return
        }

        preferences.engine = engine
        if (preferences.alwaysListening) {
            restartAlwaysListening()
        }
        syncAlwaysListeningSwitch()
        showStatus(getString(R.string.engine_changed, getString(engineLabelRes(engine))))
    }

    private fun engineLabelRes(engine: WakeWordEngineType): Int = when (engine) {
        WakeWordEngineType.SHERPA -> R.string.engine_sherpa
        WakeWordEngineType.PORCUPINE -> R.string.engine_porcupine
        WakeWordEngineType.SPEECH -> R.string.engine_speech
    }

    private fun showAccessKeyDialog(startAfterSave: Boolean, selectPorcupine: Boolean = false) {
        val dialogBinding = DialogAccessKeyBinding.inflate(layoutInflater)
        dialogBinding.editAccessKey.setText(preferences.accessKey)
        dialogBinding.txtAccessKeyHelp.setOnClickListener { openUrl(PICOVOICE_CONSOLE_URL) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.access_key_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = dialogBinding.editAccessKey.text?.toString()?.trim().orEmpty()
                if (key.isBlank()) {
                    dialogBinding.inputAccessKey.error = getString(R.string.access_key_required)
                    return@setOnClickListener
                }

                dialogBinding.inputAccessKey.error = null
                preferences.accessKey = key
                if (selectPorcupine) {
                    preferences.engine = WakeWordEngineType.PORCUPINE
                }
                dialog.dismiss()
                Toast.makeText(this, R.string.access_key_saved, Toast.LENGTH_SHORT).show()

                when {
                    startAfterSave -> withAudioPermission { startAlwaysListening() }
                    preferences.alwaysListening -> restartAlwaysListening()
                    else -> syncAlwaysListeningSwitch()
                }
            }
        }

        dialog.setOnDismissListener {
            if (startAfterSave && !preferences.alwaysListening) {
                syncAlwaysListeningSwitch()
            }
        }

        dialog.show()
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
            Toast.makeText(this, R.string.battery_already_exempt, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestPermissionsThenListen() {
        withAudioPermission { startListeningSafely() }
    }

    private fun withAudioPermission(action: () -> Unit) {
        val missing = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            missing += Manifest.permission.RECORD_AUDIO
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }

        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startListeningSafely() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatus(getString(R.string.status_service_unavailable))
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(recognitionListener)
            }
        }

        pauseAlwaysListeningForCommand()

        isListening = true
        setListeningUi(true)
        showStatus(getString(R.string.status_listening))
        binding.txtHeard.text = getString(R.string.heard_empty)
        speechRecognizer?.startListening(Speech.createRecognizerIntent())
    }

    private fun stopListening() {
        val wasListening = isListening
        isListening = false
        setListeningUi(false)
        speechRecognizer?.cancel()
        if (wasListening) {
            resumeAlwaysListeningAfterCommand()
        }
    }

    private fun pauseAlwaysListeningForCommand() {
        if (!preferences.alwaysListening) return
        startService(
            Intent(this, JarvisService::class.java).setAction(JarvisService.ACTION_PAUSE)
        )
    }

    private fun resumeAlwaysListeningAfterCommand() {
        if (!preferences.alwaysListening) return
        startService(
            Intent(this, JarvisService::class.java).setAction(JarvisService.ACTION_RESUME)
        )
    }

    private fun setListeningUi(listening: Boolean) {
        binding.btnOuvir.isEnabled = !listening
        binding.btnOuvir.text = getString(
            if (listening) R.string.btn_ouvindo else R.string.btn_ouvir
        )
    }

    private fun handleTranscript(transcript: String) {
        binding.txtHeard.text = getString(R.string.heard_format, transcript)

        when (val command = commandProcessor.parse(transcript)) {
            is Command.OpenApp -> openApp(command)
            is Command.OpenUrl -> openUrl(command.url)
            is Command.SetTimer -> {
                TimerScheduler.schedule(this, command.seconds)
                showStatus(getString(R.string.status_timer_set, formatDuration(command.seconds)))
            }
            Command.CurrentTime -> {
                val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                showStatus(getString(R.string.status_time, now))
            }
            is Command.Unknown -> {
                showStatus(getString(R.string.status_unknown, transcript))
                Toast.makeText(this, R.string.toast_unknown_command, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openApp(command: Command.OpenApp) {
        val launchIntent = packageManager.getLaunchIntentForPackage(command.packageName)
        if (launchIntent == null) {
            showStatus(getString(R.string.status_app_not_installed, command.label))
            openUrl(command.storeUrl)
            return
        }

        try {
            startActivity(launchIntent)
            showStatus(getString(R.string.status_opening, command.label))
        } catch (_: ActivityNotFoundException) {
            showStatus(getString(R.string.status_app_not_installed, command.label))
            openUrl(command.storeUrl)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            showStatus(getString(R.string.status_opening_site))
        } catch (_: ActivityNotFoundException) {
            showStatus(getString(R.string.status_no_browser))
        }
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return when {
            hours > 0 -> getString(R.string.duration_hours, hours, minutes)
            minutes > 0 -> getString(R.string.duration_minutes, minutes)
            else -> getString(R.string.duration_seconds, remainingSeconds)
        }
    }

    private fun showStatus(message: String) {
        binding.txtStatus.text = message
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            showStatus(getString(R.string.status_listening))
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            showStatus(getString(R.string.status_processing))
        }

        override fun onError(error: Int) {
            if (!isListening) return
            isListening = false
            setListeningUi(false)
            showStatus(errorMessage(error))
            resumeAlwaysListeningAfterCommand()
        }

        override fun onResults(results: Bundle?) {
            if (!isListening) return
            isListening = false
            setListeningUi(false)

            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()

            if (transcript.isNullOrBlank()) {
                showStatus(getString(R.string.status_no_result))
            } else {
                handleTranscript(transcript)
            }

            resumeAlwaysListeningAfterCommand()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return
            binding.txtHeard.text = getString(R.string.status_heard_partial, transcript)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.error_network)

        SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.error_no_match)

        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.error_speech_timeout)

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> getString(R.string.error_busy)

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            getString(R.string.status_permission_denied)

        else -> getString(R.string.error_generic, error)
    }
}
