import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import apis.GtfsDataHandler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class GtfsDataHandlerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testInitializeAndGetSuggestions() = runBlocking {
        val handler = GtfsDataHandler(context)
        handler.initialize()
        val suggestions = handler.getSuggestions("Nairobi")
        assertTrue(suggestions.isNotEmpty())
    }
}
