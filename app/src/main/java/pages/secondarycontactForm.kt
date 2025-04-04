package pages

import retrofit2.Call
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.newapp.Routes
import apis.secondaryContactApiClient
import apis.secondaryContactRequest
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import apis.PrimaryContactRequest
import apis.primaryContactApiClient
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecondaryContactForm(navController: NavController) {
    val context = LocalContext.current
    val tts = remember { TextToSpeech(context) { } }
    val scope = rememberCoroutineScope()


    // Secondary Contact
    var secondaryName by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var secondaryNameError by remember { mutableStateOf<String?>(null) }
    var secondaryPhoneError by remember { mutableStateOf<String?>(null) }
    var contactSuggestions by remember { mutableStateOf(listOf<String>()) }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }





    fun validateForm(): Boolean {
        var isValid = true
        if (secondaryName.isBlank()) {
           secondaryNameError = "Full name is required"
            isValid = false
        } else {
            secondaryNameError = null
        }

        if (!secondaryPhone.matches(Regex("^\\d{10}$"))) {
            secondaryPhoneError = "Enter a valid 10-digit phone number"
            isValid = false
        } else {
            secondaryPhoneError = null
        }
        return isValid
    }

    fun submitContact() {
        if (!validateForm()) return
        val newContact = PrimaryContactRequest(secondaryName, secondaryPhone, "Secondary")
        primaryContactApiClient.api.createPrimaryContact(newContact).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    tts.speak("Secondary contact saved successfully", TextToSpeech.QUEUE_FLUSH, null, null)
                    navController.navigate(Routes.SecondaryContactForm)
                } else {
                    Toast.makeText(context, "Failed to save contact", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun fetchSuggestions(query: String) {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null
        )
        val suggestions = mutableListOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                suggestions.add(name)
            }
        }
        contactSuggestions = suggestions.distinct()
        cursor?.close()
    }

    fun fetchPhoneNumber(name: String) {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val phoneNumber = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                secondaryPhone = phoneNumber.replace("\\s".toRegex(), "")
                tts.speak("Phone number for $name is $secondaryPhone", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                tts.speak("No phone number found for $name", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        cursor?.close()
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the full name of the primary contact")
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    secondaryName = matches[0]
                    fetchSuggestions(secondaryName)
                    tts.speak("Showing suggestions for ${matches[0]}", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            override fun onError(error: Int) { Toast.makeText(context, "Speech Error", Toast.LENGTH_SHORT).show() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    fun goHome() {
        tts.speak("Going back to homepage", TextToSpeech.QUEUE_FLUSH, null, null)
        navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { startVoiceInput() }, // 👈 Single Tap triggers voice input
                    onDoubleTap = { submitContact() },
                    onLongPress = { goHome() }
                )
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Emergency Primary Contact",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        Text("Primary Contact", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = secondaryName,
            onValueChange = {
                secondaryName = it
                fetchSuggestions(it)
            },
            label = { Text("Full Name") },
            isError = secondaryNameError != null,
            trailingIcon = {
                IconButton(onClick = { startVoiceInput() }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice Input")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (secondaryNameError != null) {
            Text(secondaryNameError!!, color = MaterialTheme.colorScheme.error)
        }

        DropdownMenu(
            expanded = contactSuggestions.isNotEmpty(),
            onDismissRequest = { contactSuggestions = emptyList() }
        ) {
            contactSuggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        secondaryName = name
                        contactSuggestions = emptyList()
                        scope.launch { fetchPhoneNumber(name) }
                    }
                )
            }
        }

        OutlinedTextField(
            value = secondaryPhone,
            onValueChange = { secondaryPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = secondaryPhoneError != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (secondaryPhoneError != null) {
            Text(secondaryPhoneError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                submitContact()
                navController.navigate(Routes.homeScreen)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
    }
}
