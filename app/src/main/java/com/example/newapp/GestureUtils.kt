import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object GestureUtils {

    fun provideAudioFeedback(tts: TextToSpeech, message: String, isError: Boolean, context: Context) {
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (isError) {
            // Play error tone
            provideHapticFeedback(vibrator, true)
        } else {
            // Play success tone
            provideHapticFeedback(vibrator, false)
        }
    }
    fun provideHapticFeedback(vibrator: Vibrator, isError: Boolean) {
        val duration = if (isError) 200L else 100L // Use Long values
        val pattern = if (isError) longArrayOf(0L, duration, 100L, duration) else longArrayOf(0L, duration)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    @Composable
    fun registerVolumeDownReceiver(onVolumeDown: () -> Unit) {
        val context = LocalContext.current
        val volumeDownReceiver = remember {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
                        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            onVolumeDown()
                        }
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            ContextCompat.registerReceiver(
                context,
                volumeDownReceiver,
                IntentFilter(Intent.ACTION_MEDIA_BUTTON),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose {
                context.unregisterReceiver(volumeDownReceiver)
            }
        }
    }

    @Composable
    fun registerPowerButtonReceiver(onPowerButton: () -> Unit) {
        val context = LocalContext.current
        val powerButtonReceiver = remember {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
                        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (event?.keyCode == KeyEvent.KEYCODE_POWER && event.repeatCount == 2) {
                            onPowerButton()
                        }
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            ContextCompat.registerReceiver(
                context,
                powerButtonReceiver,
                IntentFilter(Intent.ACTION_MEDIA_BUTTON),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose {
                context.unregisterReceiver(powerButtonReceiver)
            }
        }
    }
}
