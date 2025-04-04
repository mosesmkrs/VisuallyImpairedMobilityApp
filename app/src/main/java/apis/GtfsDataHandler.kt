package apis

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

/**
 * Handler for GTFS (General Transit Feed Specification) data to provide location suggestions
 * for the visually impaired mobility app.
 */
class GtfsDataHandler(private val context: Context) {
    private var stops: List<GtfsStop> = emptyList()
    private var routes: List<GtfsRoute> = emptyList()
    private var isInitialized = false

    /**
     * Initialize the GTFS data from the assets folder
     */
    suspend fun initialize() {
        if (isInitialized) return
        
        withContext(Dispatchers.IO) {
            try {
                // List all files in assets to debug
                val assetsList = context.assets.list("")
                Log.d("GtfsDataHandler", "Assets in root: ${assetsList?.joinToString(", ")}")
                
                // Load stops from stops.txt in assets (directly from root, not in gtfs folder)
                val stopsData = loadAssetFile("stops.txt")
                stops = parseStops(stopsData)
                
                // Load routes from routes.txt in assets
                val routesData = loadAssetFile("routes.txt")
                routes = parseRoutes(routesData)
                
                isInitialized = true
                Log.d("GtfsDataHandler", "GTFS data initialized with ${stops.size} stops and ${routes.size} routes")
            } catch (e: Exception) {
                Log.e("GtfsDataHandler", "Error initializing GTFS data: ${e.message}")
                e.printStackTrace()
                // Initialize with empty data if files are not available
                stops = emptyList()
                routes = emptyList()
            }
        }
    }

    /**
     * Get location suggestions based on user input
     */
    fun getSuggestions(input: String, maxResults: Int = 5): List<GtfsLocation> {
        // For debugging - log the input and initialization state
        Log.d("GtfsDataHandler", "Getting suggestions for: '$input', initialized: $isInitialized")
        
        // If input is too short or we're not initialized, provide sample data for testing
        if (!isInitialized) {
            Log.d("GtfsDataHandler", "Not initialized, returning sample data")
            return getSampleSuggestions(input)
        }
        
        if (input.length < 2) {
            Log.d("GtfsDataHandler", "Input too short, returning empty list")
            return emptyList()
        }
        
        val normalizedInput = input.lowercase(Locale.getDefault())
        val suggestions = mutableListOf<GtfsLocation>()
        
        // Add matching stops
        val matchingStops = stops
            .filter { 
                it.name.lowercase(Locale.getDefault()).contains(normalizedInput) || 
                it.description?.lowercase(Locale.getDefault())?.contains(normalizedInput) == true 
            }
            .map { GtfsLocation(it.name, it.lat, it.lon, LocationType.STOP, it.id) }
        
        suggestions.addAll(matchingStops)
        Log.d("GtfsDataHandler", "Found ${matchingStops.size} matching stops")
        
        // Add matching routes
        val matchingRoutes = routes
            .filter { it.name.lowercase(Locale.getDefault()).contains(normalizedInput) }
            .map { GtfsLocation(it.name, null, null, LocationType.ROUTE, it.id) }
        
        suggestions.addAll(matchingRoutes)
        Log.d("GtfsDataHandler", "Found ${matchingRoutes.size} matching routes")
        
        // Sort by relevance (exact matches first, then starts with, then contains)
        val result = suggestions
            .sortedWith(compareBy(
                { !it.name.lowercase(Locale.getDefault()).equals(normalizedInput) },
                { !it.name.lowercase(Locale.getDefault()).startsWith(normalizedInput) }
            ))
            .take(maxResults)
        
        Log.d("GtfsDataHandler", "Returning ${result.size} suggestions")
        return result
    }
    
    /**
     * Get sample suggestions for testing when GTFS data is not available
     */
    private fun getSampleSuggestions(input: String): List<GtfsLocation> {
        val normalizedInput = input.lowercase(Locale.getDefault())
        if (normalizedInput.length < 2) return emptyList()
        
        val sampleLocations = listOf(
            GtfsLocation("Nairobi Central Station", -1.2921, 36.8219, LocationType.STOP, "stop1"),
            GtfsLocation("Westlands Terminal", -1.2673, 36.8123, LocationType.STOP, "stop2"),
            GtfsLocation("Mombasa Road Bus Stop", -1.3182, 36.8286, LocationType.STOP, "stop3"),
            GtfsLocation("Karen Bus Terminal", -1.3139, 36.7062, LocationType.STOP, "stop4"),
            GtfsLocation("Thika Road Mall", -1.2192, 36.8880, LocationType.STOP, "stop5"),
            GtfsLocation("Route 34 - City Center to Kibera", null, null, LocationType.ROUTE, "route1"),
            GtfsLocation("Route 58 - Westlands to CBD", null, null, LocationType.ROUTE, "route2"),
            GtfsLocation("Route 23 - Nairobi to Mombasa", null, null, LocationType.ROUTE, "route3")
        )
        
        return sampleLocations
            .filter { it.name.lowercase(Locale.getDefault()).contains(normalizedInput) }
            .take(5)
    }
    
    /**
     * Get location details by ID
     */
    fun getLocationById(id: String, type: LocationType): GtfsLocation? {
        return when (type) {
            LocationType.STOP -> {
                val stop = stops.find { it.id == id }
                stop?.let { GtfsLocation(it.name, it.lat, it.lon, LocationType.STOP, it.id) }
            }
            LocationType.ROUTE -> {
                val route = routes.find { it.id == id }
                route?.let { GtfsLocation(it.name, null, null, LocationType.ROUTE, it.id) }
            }
        }
    }

    /**
     * Parse GTFS stops data
     */
    private fun parseStops(data: String): List<GtfsStop> {
        val lines = data.split("\n")
        if (lines.isEmpty()) return emptyList()
        
        val headerLine = lines[0]
        val headers = headerLine.split(",")
        
        val stopIdIndex = headers.indexOf("stop_id")
        val stopNameIndex = headers.indexOf("stop_name")
        val stopDescIndex = headers.indexOf("stop_desc")
        val stopLatIndex = headers.indexOf("stop_lat")
        val stopLonIndex = headers.indexOf("stop_lon")
        
        if (stopIdIndex == -1 || stopNameIndex == -1 || stopLatIndex == -1 || stopLonIndex == -1) {
            Log.e("GtfsDataHandler", "Invalid stops.txt format")
            return emptyList()
        }
        
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            
            val values = line.split(",")
            if (values.size <= maxOf(stopIdIndex, stopNameIndex, stopLatIndex, stopLonIndex)) {
                return@mapNotNull null
            }
            
            try {
                GtfsStop(
                    id = values[stopIdIndex],
                    name = values[stopNameIndex],
                    description = if (stopDescIndex != -1 && stopDescIndex < values.size) values[stopDescIndex] else null,
                    lat = values[stopLatIndex].toDouble(),
                    lon = values[stopLonIndex].toDouble()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Parse GTFS routes data
     */
    private fun parseRoutes(data: String): List<GtfsRoute> {
        val lines = data.split("\n")
        if (lines.isEmpty()) return emptyList()
        
        val headerLine = lines[0]
        val headers = headerLine.split(",")
        
        val routeIdIndex = headers.indexOf("route_id")
        val routeNameIndex = headers.indexOf("route_long_name")
        val routeShortNameIndex = headers.indexOf("route_short_name")
        
        if (routeIdIndex == -1 || (routeNameIndex == -1 && routeShortNameIndex == -1)) {
            Log.e("GtfsDataHandler", "Invalid routes.txt format")
            return emptyList()
        }
        
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            
            val values = line.split(",")
            if (values.size <= maxOf(routeIdIndex, routeNameIndex.takeIf { it != -1 } ?: 0, routeShortNameIndex.takeIf { it != -1 } ?: 0)) {
                return@mapNotNull null
            }
            
            try {
                val name = when {
                    routeNameIndex != -1 -> values[routeNameIndex]
                    routeShortNameIndex != -1 -> values[routeShortNameIndex]
                    else -> "Unknown Route"
                }
                
                GtfsRoute(
                    id = values[routeIdIndex],
                    name = name
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Load file from assets folder
     */
    private fun loadAssetFile(fileName: String): String {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append('\n')
            }
            
            reader.close()
            stringBuilder.toString()
        } catch (e: IOException) {
            Log.e("GtfsDataHandler", "Error reading asset file $fileName: ${e.message}")
            ""
        }
    }
    
    /**
     * Load GTFS data from a remote API
     */
    suspend fun loadFromApi(apiUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url(apiUrl)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("GtfsDataHandler", "API request failed: ${response.code}")
                    return@withContext false
                }
                
                val jsonData = response.body?.string() ?: return@withContext false
                parseApiResponse(jsonData)
                isInitialized = true
                true
            } catch (e: Exception) {
                Log.e("GtfsDataHandler", "Error loading from API: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Parse API response containing GTFS data
     */
    private fun parseApiResponse(jsonData: String) {
        try {
            val json = JSONObject(jsonData)
            
            // Parse stops
            val stopsArray = json.optJSONArray("stops") ?: JSONArray()
            val parsedStops = mutableListOf<GtfsStop>()
            
            for (i in 0 until stopsArray.length()) {
                val stopJson = stopsArray.getJSONObject(i)
                parsedStops.add(
                    GtfsStop(
                        id = stopJson.getString("stop_id"),
                        name = stopJson.getString("stop_name"),
                        description = stopJson.optString("stop_desc", null),
                        lat = stopJson.getDouble("stop_lat"),
                        lon = stopJson.getDouble("stop_lon")
                    )
                )
            }
            
            // Parse routes
            val routesArray = json.optJSONArray("routes") ?: JSONArray()
            val parsedRoutes = mutableListOf<GtfsRoute>()
            
            for (i in 0 until routesArray.length()) {
                val routeJson = routesArray.getJSONObject(i)
                parsedRoutes.add(
                    GtfsRoute(
                        id = routeJson.getString("route_id"),
                        name = routeJson.optString("route_long_name", routeJson.optString("route_short_name", "Unknown Route"))
                    )
                )
            }
            
            stops = parsedStops
            routes = parsedRoutes
            
            Log.d("GtfsDataHandler", "Parsed ${stops.size} stops and ${routes.size} routes from API")
        } catch (e: Exception) {
            Log.e("GtfsDataHandler", "Error parsing API response: ${e.message}")
        }
    }
}

/**
 * GTFS Stop data class
 */
data class GtfsStop(
    val id: String,
    val name: String,
    val description: String?,
    val lat: Double,
    val lon: Double
)

/**
 * GTFS Route data class
 */
data class GtfsRoute(
    val id: String,
    val name: String
)

/**
 * GTFS Location data class for suggestions
 */
data class GtfsLocation(
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val type: LocationType,
    val id: String
)

/**
 * Location type enum
 */
enum class LocationType {
    STOP,
    ROUTE
}
