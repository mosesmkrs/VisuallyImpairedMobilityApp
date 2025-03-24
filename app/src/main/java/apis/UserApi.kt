package APIs

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call

data class UserRequest(
    val firebaseuid: String?,
    val username: String,
    val email: String,
)

interface UserApi {
    @POST("users")
    fun createUser(@Body contact: UserRequest): Call<Void>
}

