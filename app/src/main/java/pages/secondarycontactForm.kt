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
import APIs.secondaryContactApiClient
import APIs.secondaryContactRequest
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SecondaryContactForm(navController: NavController) {
    val context = LocalContext.current


    // Secondary Contact
    var secondaryName by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var secondaryNameError by remember { mutableStateOf<String?>(null) }
    var secondaryPhoneError by remember { mutableStateOf<String?>(null) }

    fun validateForm(): Boolean {
        var isValid = true


        // Validate secondary contact
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

        val newContact = secondaryContactRequest(
            contact_name = secondaryName,
            contact_phone = secondaryPhone,
            relationship = "secondary"
        )

        secondaryContactApiClient.api.createSecondaryEmergencyContact(newContact).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Contact saved!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Routes.homeScreen)
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
            text = "Emergency secondary contact",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Text("Secondary Contact", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = secondaryName,
            onValueChange = { secondaryName = it },
            label = { Text("Full Name") },
            isError = secondaryNameError != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (secondaryNameError != null) {
            Text(secondaryNameError!!, color = MaterialTheme.colorScheme.error)
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

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { navController.navigate(Routes.homeScreen) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
    }
}
