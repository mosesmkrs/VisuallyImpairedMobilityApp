package APIs

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object UserApiClient {
    private const val BASE_URL = "http://192.168.43.60:8080/"

    val api: UserApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserApi::class.java)
    }
}
