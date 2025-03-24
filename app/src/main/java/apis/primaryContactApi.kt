package apis

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call

data class PrimaryContactRequest(
    val contact_name: String,
    val contact_phone: String,
    val relationship: String
)

interface primaryContactApi {
    @POST("emergency")
    fun createPrimaryContact(@Body contact: PrimaryContactRequest): Call<Void>
}

