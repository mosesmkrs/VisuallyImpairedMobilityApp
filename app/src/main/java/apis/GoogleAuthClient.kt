package apis

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.BeginSignInResult
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class GoogleAuthClient(
    private val context: Context,
) {
    private val tag = "GoogleAuthClient: "
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)

    // Server client ID from google-services.json
    private val serverClientId = "663604971731-j4vgnp262u3cu9l2vissqhakfcess5i4.apps.googleusercontent.com"

    // User information
    private var currentUser: GoogleUser? = null

    data class GoogleUser(
        val id: String,
        val name: String,
        val email: String,
        val photoUrl: String
    )

    // Get user information methods
    fun getUser(): GoogleUser? {
        Log.d(tag, "Getting user: ${currentUser?.id}")
        return currentUser
    }

    fun getUserId(): String? {
        Log.d(tag, "Getting user ID: ${currentUser?.id}")
        return currentUser?.id
    }

    fun getUserName(): String? {
        Log.d(tag, "Getting user name: ${currentUser?.name}")
        return currentUser?.name
    }

    fun getUserEmail(): String? {
        Log.d(tag, "Getting user email: ${currentUser?.email}")
        return currentUser?.email
    }

    fun getUserPhotoUrl(): String? {
        Log.d(tag, "Getting user photo URL: ${currentUser?.photoUrl}")
        return currentUser?.photoUrl
    }

    fun isSingedIn(): Boolean {
        if (currentUser != null) {
            return true
        }
        return false
    }

    // Begin sign-in process and return IntentSender for activity to launch
    suspend fun signIn(): IntentSender? {
        if (isSingedIn()) {
            Log.d(tag, "User already signed in, no need to start sign-in flow")
            return null
        }

        try {
            val signInRequest = googleSignInRequest()
            val result = oneTapClient.beginSignIn(signInRequest).await()
            Log.d(tag, "Sign-in request successful, returning intent sender")
            return result.pendingIntent.intentSender
        } catch (e: Exception) {
            when {
                e is ApiException && e.statusCode == CommonStatusCodes.CANCELED -> {
                    Log.d(tag, "One Tap dialog was closed by user")
                }
                e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR -> {
                    Log.e(tag, "One Tap encountered a network error")
                }
                e is CancellationException -> throw e
                else -> {
                    Log.e(tag, "Unexpected error during sign-in: ${e.message}")
                    e.printStackTrace()
                }
            }
            return null
        }
    }

    // Create Google sign-in request
    private fun googleSignInRequest(): BeginSignInRequest {
        return BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()
    }

    // Handle sign-in result from activity
    suspend fun handleSignInResult(data: Intent): Boolean {
        try {
            val credential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken

            if (idToken != null) {
                Log.d(tag, "Got ID token from Google")

                // Create user object from Google credentials
                currentUser = GoogleUser(
                    id = credential.id ?: "",
                    name = credential.displayName ?: "",
                    email = credential.id ?: "",
                    photoUrl = credential.profilePictureUri?.toString() ?: ""
                )

                Log.d(tag, "User signed in successfully: ${currentUser?.name}")
                Log.d(tag, "Google ID: ${currentUser?.id}")
                Log.d(tag, "Email: ${currentUser?.email}")
                return true
            } else {
                Log.e(tag, "No ID token found in sign-in result")
                return false
            }
        } catch (e: ApiException) {
            Log.e(tag, "Error getting sign-in credential: ${e.statusCode} ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error handling sign-in result: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // Sign out from Google
    suspend fun signOut() {
        try {
            oneTapClient.signOut().await()
            currentUser = null
            Log.d(tag, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error signing out: ${e.message}")
            e.printStackTrace()
        }
    }
}