import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import apis.MatatuRouteHandler
import apis.GtfsDataHandler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class MatatuRouteHandlerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gtfsDataHandler = GtfsDataHandler(context)

    @Test
    fun testFindNearestStop() = runBlocking {
        val handler = MatatuRouteHandler(context, gtfsDataHandler)
        handler.initialize()
        val location = GeoPoint(-1.2921, 36.8219)
        val stop = handler.findNearestStop(location)
        assertNotNull(stop)
    }

    @Test
    fun testFindNearestStopToDestination() = runBlocking {
        val handler = MatatuRouteHandler(context, gtfsDataHandler)
        handler.initialize()
        val userLocation = GeoPoint(-1.2921, 36.8219)
        val destinationLocation = GeoPoint(-1.2833, 36.8167)
        val result = handler.findNearestStopToDestination(userLocation, destinationLocation)
        assertTrue(result == null || (result.nearbyStop != null && result.destinationStop != null && result.route != null))
    }
}
