package apis


import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.http.Headers
import java.time.LocalDateTime

data class UserRequest(
    val usersID: Int,
    val username: String,
    val email: String,
    val firebaseUUID: String,
    val createdAt: LocalDateTime,
)

interface UserApi {
    @POST("/users")
    @Headers("Content-Type: application/json")
    fun createUser(@Body user: UserRequest): Call<ResponseBody>
}