package apis

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.example.newapp.BuildConfig
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.BeginSignInResult
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class GoogleAuthClient(
    private val context: Context,
) {
    private val tag = "GoogleAuthClient: "
    private val auth = FirebaseAuth.getInstance()
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)
    
    // Server client ID from google-services.json
    private val serverClientId = "663604971731-j4vgnp262u3cu9l2vissqhakfcess5i4.apps.googleusercontent.com"

    // Get user information methods
    fun getUser(): FirebaseUser? = auth.currentUser
    
    fun getUserId(): String? = auth.currentUser?.uid
    
    fun getUserName(): String? = auth.currentUser?.displayName
    
    fun getUserEmail(): String? = auth.currentUser?.email
    
    fun getUserPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()

    // Check if user is signed in
    fun isSingedIn(): Boolean {
        if (auth.currentUser != null) {
            Log.d(tag, "User already signed in: ${auth.currentUser?.displayName}")
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
                    .setFilterByAuthorizedAccounts(false) // Show all accounts, not just previously authorized ones
                    .build()
            )
            .build()
    }

    // Handle sign-in result from activity
    suspend fun handleSignInResult(data: Intent): AuthResult? {
        try {
            val credential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken
            
            if (idToken != null) {
                Log.d(tag, "Got ID token from Google, authenticating with Firebase")
                return firebaseAuthWithGoogle(idToken)
            } else {
                Log.e(tag, "No ID token found in sign-in result")
                return null
            }
        } catch (e: ApiException) {
            Log.e(tag, "Error getting sign-in credential: ${e.statusCode} ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error handling sign-in result: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Authenticate with Firebase using Google ID token
    suspend fun firebaseAuthWithGoogle(idToken: String): AuthResult? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            Log.d(tag, "Firebase authentication successful for user: ${authResult.user?.displayName} (${authResult.user?.uid})")
            authResult
        } catch (e: Exception) {
            Log.e(tag, "Firebase authentication failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // Sign out from both Google and Firebase
    suspend fun signOut() {
        try {
            oneTapClient.signOut().await()
            auth.signOut()
            Log.d(tag, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error signing out: ${e.message}")
            e.printStackTrace()
        }
    }
}