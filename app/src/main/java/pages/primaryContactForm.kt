package pages

import android.Manifest
import android.app.Application
import android.provider.ContactsContract
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import apis.GoogleAuthClient
import com.example.newapp.Routes
import com.example.newapp.SQL.PC.PrimaryContact
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.users.UserViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ContactFormScreen(navController: NavController,googleAuthClient: GoogleAuthClient,) {
    val context = LocalContext.current
    var tts = remember { TextToSpeech(context) { } }
    val scope = rememberCoroutineScope()
    var primaryName by remember { mutableStateOf("") }
    var primaryPhone by remember { mutableStateOf("") }
    var primaryNameError by remember { mutableStateOf<String?>(null) }
    var primaryPhoneError by remember { mutableStateOf<String?>(null) }
    var contactSuggestions by remember { mutableStateOf(listOf<String>()) }
    var dbSaveSuccess by remember { mutableStateOf(true) }

// Initialize TTS
    tts = remember{
        TextToSpeech(context){
                status ->
            if(status == TextToSpeech.SUCCESS){
                tts.language = Locale.getDefault()
                tts.speak("You are on the Primary Emergency Contact form", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
    // Cleanup TTS when the screen is removed
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
    // Initialize ViewModels
    val userViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(UserViewModel::class.java)
    }

    val contactViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(pCViewModel::class.java)
    }

    // Get current user ID
    val currentFirebaseUUID = googleAuthClient.getUserId() ?: ""
    var currentUserID by remember { mutableStateOf(0) }

    // Load current user ID from Firebase UUID
    LaunchedEffect(currentFirebaseUUID) {
        if (currentFirebaseUUID.isNotEmpty()) {
            val user = userViewModel.getUserByFirebaseUUID(currentFirebaseUUID)
            if (user != null) {
                currentUserID = user.userID
                // Check if primary contact already exists
                val existingContact = contactViewModel.getPrimaryContact(currentUserID)
                if (existingContact != null) {
                    primaryName = existingContact.contactname
                    primaryPhone = existingContact.contactnumber
                    Log.d("ContactForm", "Loaded existing primary contact: $primaryName")
                }
            }
        }
    }



    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Contacts permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun validateForm(): Boolean {
        var isValid = true
        if (primaryName.isBlank()) {
            primaryNameError = "Full name is required"
            isValid = false
        } else {
            primaryNameError = null
        }

        // Accept international formats like +2547123456789 or regular 10-digit numbers
        if (!primaryPhone.matches(Regex("^(\\+\\d{1,15}|\\d{10,15})$"))) {
            primaryPhoneError = "Enter a valid phone number (10+ digits, can include + prefix)"
            isValid = false
        } else {
            primaryPhoneError = null
        }
        return isValid
    }

    fun submitContact() {
        if (!validateForm()) return

        // Save to SQLite
        scope.launch {
            try {
                if (currentUserID > 0) {
                    val contact = PrimaryContact(
                        userID = currentUserID,
                        contactname = primaryName,
                        contactnumber = primaryPhone
                    )

                    val result = contactViewModel.insertOrUpdateContact(contact)
                    if (result > 0) {
                        dbSaveSuccess = true
                        Log.d("ContactForm", "Primary contact saved to SQLite: $primaryName")
                        tts.speak("Primary contact saved successfully", TextToSpeech.QUEUE_FLUSH, null, null)
                        navController.navigate(Routes.SecondaryContactForm)
                    } else {
                        dbSaveSuccess = false
                        Log.e("ContactForm", "Failed to save contact to SQLite")
                        Toast.makeText(context, "Failed to save contact", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("ContactForm", "Cannot save contact: User not logged in or user ID not found")
                    Toast.makeText(context, "Please log in to save contacts", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                dbSaveSuccess = false
                Log.e("ContactForm", "Error saving contact to SQLite: ${e.message}")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
                primaryPhone = phoneNumber.replace("\\s".toRegex(), "")
                tts.speak("Phone number for $name", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                tts.speak("No phone number found for $name", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        cursor?.close()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Emergency Primary Contact",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Primary Contact", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = primaryName,
            onValueChange = {
                primaryName = it
                fetchSuggestions(it)
            },
            label = { Text("Full Name") },
            isError = primaryNameError != null,
            modifier = Modifier
                .fillMaxWidth()

        )
        if (primaryNameError != null) {
            Text(primaryNameError!!, color = MaterialTheme.colorScheme.error)
        }

        DropdownMenu(
            expanded = contactSuggestions.isNotEmpty(),
            onDismissRequest = { contactSuggestions = emptyList() }
        ) {
            contactSuggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        primaryName = name
                        contactSuggestions = emptyList()
                        scope.launch { fetchPhoneNumber(name) }
                    }
                )
            }
        }

        OutlinedTextField(
            value = primaryPhone,
            onValueChange = { primaryPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = primaryPhoneError != null,
            modifier = Modifier
                .fillMaxWidth()
        )
        if (primaryPhoneError != null) {
            Text(primaryPhoneError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                submitContact()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
    }
}