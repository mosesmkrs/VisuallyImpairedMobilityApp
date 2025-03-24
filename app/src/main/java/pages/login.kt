package pages

import apis.UserApiClient
import apis.UserRequest
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import apis.GoogleAuthClient
import com.example.newapp.R
import com.example.newapp.Routes
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.gson.Gson
import java.time.LocalDateTime


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

        val userRequest = UserRequest(1,"ugfdhjx", "cgxkhGL", "gmail.com", LocalDateTime.now())

        val call = UserApiClient.api.createUser(userRequest)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Log.d("API_SUCCESS", "Response: ${response.body()?.string()}")
                } else {
                    // Log error response
                    Log.e("API_ERROR", "Error Code: ${response.code()}")
                    Log.e("API_ERROR", "Error Body: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // Log failure (e.g., No internet, timeout, etc.)
                Log.e("API_FAILURE", "Request Failed: ${t.message}", t)
            }
        })



    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img),
            contentDescription = "App Logo",
            modifier = Modifier
                .width(401.dp)
                .height(448.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Welcome to TembeaNami",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your navigation assistant!",
            color = Color.Gray,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(52.dp))
        Box(
            contentAlignment = Alignment.Center
        ) {
            if (isSignIn) {
                navController.navigate(Routes.ContactFormScreen)
                submitUser()
            } else {
                OutlinedButton(onClick = {
                    lifecycleOwner.lifecycleScope.launch {
                        isSignIn = googleAuthClient.signIn()
                        submitUser()
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
