package APIs

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object primaryContactApiClient {
    private const val BASE_URL = "http://192.168.100.153:8080/"

    val api: primaryContactApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(primaryContactApi::class.java)
    }
}
