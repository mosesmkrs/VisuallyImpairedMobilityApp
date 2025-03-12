package com.example.newapp

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

@Composable
fun ContactFormScreen(navController: NavController) {
    val context = LocalContext.current

    // Primary Contact
    var primaryName by remember { mutableStateOf("") }
    var primaryPhone by remember { mutableStateOf("") }
    var primaryNameError by remember { mutableStateOf<String?>(null) }
    var primaryPhoneError by remember { mutableStateOf<String?>(null) }

    // Secondary Contact
    var secondaryName by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var secondaryNameError by remember { mutableStateOf<String?>(null) }
    var secondaryPhoneError by remember { mutableStateOf<String?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Emergency Contact Information Form",
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
            onClick = {
                if (validateForm()) {
                    Toast.makeText(context, "Form Submitted Successfully!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Routes.homeScreen)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
    }
}
