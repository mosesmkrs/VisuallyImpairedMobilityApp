package APIs

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call

data class secondaryContactRequest(
    val contact_name: String,
    val contact_phone: String,
    val relationship: String
)

interface secondaryContactApi {
    @POST("emergency")
    fun createSecondaryEmergencyContact(@Body contact: secondaryContactRequest): Call<Void>
}

