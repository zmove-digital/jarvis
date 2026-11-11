package com.zmove.jarvis

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zmove.jarvis.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private companion object {
        const val WAKE_WORD_MIC_DELAY_MS = 700L
    }

    private lateinit var binding: ActivityMainBinding
    private val commandProcessor = CommandProcessor()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val action = pendingAction
            pendingAction = null
            if (result[Manifest.permission.RECORD_AUDIO] == true) {
                action?.invoke()
            } else {
                showStatus(getString(R.string.status_permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOuvir.setOnClickListener { requestPermissionsThenListen() }
        binding.btnServico.setOnClickListener { startContinuousListening() }

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

    override fun onStop() {
        stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun requestPermissionsThenListen() {
        withAudioPermission { startListeningSafely() }
    }

    private fun startContinuousListening() {
        withAudioPermission {
            stopListening()
            val serviceIntent = Intent(this, JarvisService::class.java)
                .setAction(JarvisService.ACTION_START)
            ContextCompat.startForegroundService(this, serviceIntent)
            showStatus(getString(R.string.status_service_started))
        }
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

        isListening = true
        setListeningUi(true)
        showStatus(getString(R.string.status_listening))
        binding.txtHeard.text = getString(R.string.heard_empty)
        speechRecognizer?.startListening(Speech.createRecognizerIntent())
    }

    private fun stopListening() {
        isListening = false
        setListeningUi(false)
        speechRecognizer?.cancel()
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
