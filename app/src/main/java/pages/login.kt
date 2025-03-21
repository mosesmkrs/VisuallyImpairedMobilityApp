package pages

import APIs.GoogleAuthClient
import APIs.UserApiClient
import APIs.UserRequest
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.example.newapp.R
import com.example.newapp.Routes
import kotlinx.coroutines.launch
import retrofit2.Call


@Composable
fun GoogleSignInScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController
) {
    val context = LocalContext.current
    var isSignIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }
    val userId by remember { mutableStateOf(googleAuthClient.getUserId()) }
    val userName by remember { mutableStateOf(googleAuthClient.getUserName() ?: "Unknown") }
    val userEmail by remember { mutableStateOf(googleAuthClient.getUserEmail() ?: "No Email") }


    fun submitUser() {

        val newUser = UserRequest(
            firebaseuid = userId,
            username = userName,
            email = userEmail,
        )

        UserApiClient.api.createUser(newUser).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Contact saved!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Routes.SecondaryContactForm)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(context, "Failed to save user: $errorBody", Toast.LENGTH_LONG).show()
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
            .background(Color.White)
            .padding(16.dp),
            //.padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img),
            contentDescription = "App Logo",
            modifier = Modifier
//                .width(401.dp)
//                .height(448.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Welcome to TembeaNami",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f) // Allow to grow
        )

        Spacer(modifier = Modifier.height(12.dp))

        //Subtitle

        Text(
            text = "Your navigation assistant!",
            color = Color.Gray,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(52.dp))

        //Sign In Button

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (isSignIn) {
                navController.navigate(Routes.ContactFormScreen)
            } else {
                OutlinedButton(onClick = {
                    lifecycleOwner.lifecycleScope.launch {
                        isSignIn = googleAuthClient.signIn()
                    }
                }) {
                    Text(
                        text = "Sign In With Google",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
