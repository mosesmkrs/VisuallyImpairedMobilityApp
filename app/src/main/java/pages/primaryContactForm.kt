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
import APIs.PrimaryContactRequest
import com.example.newapp.Routes
import APIs.primaryContactApiClient

@Composable
fun ContactFormScreen(navController: NavController) {
    val context = LocalContext.current

    // Primary Contact
    var primaryName by remember { mutableStateOf("") }
    var primaryPhone by remember { mutableStateOf("") }
    var primaryNameError by remember { mutableStateOf<String?>(null) }
    var primaryPhoneError by remember { mutableStateOf<String?>(null) }

    fun validateForm(): Boolean {
        var isValid = true

        // Validate primary contact
        if (primaryName.isBlank()) {
            primaryNameError = "Full name is required"
            isValid = false
        } else {
            primaryNameError = null
        }

        if (!primaryPhone.matches(Regex("^\\d{10}$"))) {
            primaryPhoneError = "Enter a valid 10-digit phone number"
            isValid = false
        } else {
            primaryPhoneError = null
        }

        return isValid
    }

    fun submitContact() {
        if (!validateForm()) return

        val newContact = PrimaryContactRequest(
            contact_name = primaryName,
            contact_phone = primaryPhone,
            relationship = "Primary"
        )

        primaryContactApiClient.api.createPrimaryContact(newContact).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Contact saved!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Routes.SecondaryContactForm)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(context, "Failed to save contact: $errorBody", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
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
            textAlign = TextAlign.Center
        )
        Text("Primary Contact", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = primaryName,
            onValueChange = { primaryName = it },
            label = { Text("Full Name") },
            isError = primaryNameError != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (primaryNameError != null) {
            Text(primaryNameError!!, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = primaryPhone,
            onValueChange = { primaryPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = primaryPhoneError != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (primaryPhoneError != null) {
            Text(primaryPhoneError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate(Routes.SecondaryContactForm) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}
