package apis


import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Headers
import java.time.LocalDateTime

data class User(
    val usersID: Int,
    val username: String,
    val email: String,
    val firebaseUUID: String,
    val createdAt: LocalDateTime,
)

interface UserApi {
    @POST("/users")
    @Headers("Content-Type: application/json")
    suspend fun createUser(@Body user: User): Response<ResponseBody>
}