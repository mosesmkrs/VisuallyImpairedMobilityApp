package apis

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object secondaryContactApiClient {
    private const val BASE_URL = "http://192.168.100.153:8080/"

    val api: secondaryContactApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(secondaryContactApi::class.java)
    }
}
