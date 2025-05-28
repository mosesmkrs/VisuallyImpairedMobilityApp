package pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import apis.GtfsDataHandler
import apis.GtfsLocation
import apis.MatatuRouteHandler
import apis.NearestStopResult
import com.example.newapp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import components.Footer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun MatatuPage(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var destinationText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var showSuggestions by remember { mutableStateOf(false) }
    var locationSuggestions by remember { mutableStateOf<List<GtfsLocation>>(emptyList()) }

    // Permission launcher declaration
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        locationPermissionGranted = isGranted
        if (isGranted) {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fetchUserLocationn(context, fusedLocationClient) { userLocation = it }
                }
            } catch (e: SecurityException) {
                Log.e("MatatuPage", "Security exception when fetching location after permission grant: ${e.message}")
                errorMessage = "Unable to access location. Please check your location settings."
                tts?.speak("Unable to access location. Please check your location settings.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        } else {
            errorMessage = "Location permission is required for navigation"
            tts?.speak("Location permission is required for navigation. Please enable location access.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // Initialize GTFS data handler
    val gtfsDataHandler = remember { GtfsDataHandler(context) }

    // Initialize Matatu Route handler
    val matatuRouteHandler = remember { MatatuRouteHandler(context, gtfsDataHandler) }

    // State for nearest matatu stop result
    var nearestStopResult by remember { mutableStateOf<NearestStopResult?>(null) }
    var showingDirectionsToStop by remember { mutableStateOf(false) }
    var walkingRouteToStop by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var matatuRoutePath by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var matatuRouteDirections by remember { mutableStateOf<List<String>>(emptyList()) }
    var matatuRouteDistance by remember { mutableStateOf("") }
    var matatuRouteDuration by remember { mutableStateOf("") }

    // State for walking directions
    var walkingDirections by remember { mutableStateOf<List<String>>(emptyList()) }
    var walkingDistance by remember { mutableStateOf("") }
    var walkingDuration by remember { mutableStateOf("") }
    var showDirectionsPanel by remember { mutableStateOf(false) }
    var currentDirectionIndex by remember { mutableStateOf(0) }
    var isReadingDirections by remember { mutableStateOf(false) }

    // State for matatu directions
    var currentMatatuDirectionIndex by remember { mutableStateOf(0) }

    // Add this at the top of the MatatuPage composable
    val repeatingAnnouncementScope = rememberCoroutineScope()
    var routeFindingJob: Job? by remember { mutableStateOf(null) }

    // Add location update throttling
    var lastLocationUpdate by remember { mutableStateOf(0L) }
    val locationUpdateInterval = 5000L // 5 seconds

    // Add route cache
    val routeCache = remember { mutableMapOf<String, List<GeoPoint>>() }
    val walkingRouteCache = remember { mutableMapOf<String, List<GeoPoint>>() }

    // Initialize GTFS data when the screen is first loaded
    LaunchedEffect(Unit) {
        try {
            // Initialize both handlers
            gtfsDataHandler.initialize()
            matatuRouteHandler.initialize()

            // Add some sample suggestions to test UI
            locationSuggestions = listOf(
                GtfsLocation("Nairobi Central Station", -1.2921, 36.8219, apis.LocationType.STOP, "stop1"),
                GtfsLocation("Westlands Terminal", -1.2673, 36.8123, apis.LocationType.STOP, "stop2")
            )
            Log.d("MatatuPage", "GTFS data and Matatu Route handler initialized")
        } catch (e: Exception) {
            Log.e("MatatuPage", "Error initializing data: ${e.message}")
            e.printStackTrace()
        }
    }

    tts = remember{
        TextToSpeech(context){
                status ->
            if(status == TextToSpeech.SUCCESS){
                tts?.language = Locale.US
                tts?.speak("You are on the matatu page", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
    // Cleanup TTS when the screen is removed
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Optimize location updates
    LaunchedEffect(Unit) {
        if (locationPermissionGranted) {
            try {
                // Check if we have the required permission
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setMinUpdateIntervalMillis(5000)
                        .setMaxUpdateDelayMillis(10000)
                        .build()

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        object : LocationCallback() {
                            override fun onLocationResult(locationResult: LocationResult) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastLocationUpdate >= locationUpdateInterval) {
                                    locationResult.lastLocation?.let { location ->
                                        userLocation = GeoPoint(location.latitude, location.longitude)
                                        lastLocationUpdate = currentTime
                                    }
                                }
                            }
                        },
                        Looper.getMainLooper()
                    )
                } else {
                    // Request permission if not granted
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            } catch (e: SecurityException) {
                Log.e("MatatuPage", "Security exception when requesting location updates: ${e.message}")
                // Request permission if we get a security exception
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } catch (e: Exception) {
                Log.e("MatatuPage", "Error requesting location updates: ${e.message}")
            }
        }
    }

    // Function to fetch walking route
    fun fetchWalkingRoute(
        start: GeoPoint,
        end: GeoPoint,
        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val coordinates = "[${start.longitude},${start.latitude}],[${end.longitude},${end.latitude}]"
        val url = "https://api.openrouteservice.org/v2/directions/foot-walking/geojson"

        val requestBody = """
            {
                "coordinates": [$coordinates],
                "instructions": true,
                "language": "en",
                "units": "m",
                "geometry_simplify": true
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "5b3ce3597851110001cf6248178595cb660342ec99701892a1530215")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create("application/json".toMediaType(), requestBody))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ROUTE_ERROR", "Failed to get route: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    try {
                        val json = JSONObject(responseBody.string())
                        val features = json.getJSONArray("features")
                        if (features.length() > 0) {
                            val feature = features.getJSONObject(0)
                            val properties = feature.getJSONObject("properties")
                            val summary = properties.getJSONObject("summary")

                            // Get the exact distance from the API response (in meters)
                            val distanceMeters = summary.getDouble("distance")
                            val durationMinutes = summary.getDouble("duration") / 60

                            val formattedDistance = formatDistance(distanceMeters)
                            val formattedDuration = formatDuration(durationMinutes)

                            // Extract route points
                            val geometry = feature.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")
                            val routePoints = mutableListOf<GeoPoint>()

                            for (i in 0 until coordinates.length()) {
                                val point = coordinates.getJSONArray(i)
                                routePoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
                            }

                            // Extract step-by-step instructions with accurate distances
                            val segments = properties.getJSONArray("segments")
                            val steps = segments.getJSONObject(0).getJSONArray("steps")
                            val instructions = mutableListOf<String>()

                            for (i in 0 until steps.length()) {
                                val step = steps.getJSONObject(i)
                                var instruction = step.getString("instruction")
                                val stepDistance = step.getDouble("distance")
                                val distanceStr = formatDistance(stepDistance)

                                // Replace any 'head ...' or 'continue ...' instruction with 'continue straight' for accessibility
                                val headOrContinueRegex = Regex("""^(head|continue)(\s+(north|south|east|west|northeast|northwest|southeast|southwest))?""", RegexOption.IGNORE_CASE)
                                if (headOrContinueRegex.containsMatchIn(instruction.trim())) {
                                    instruction = "continue straight"
                                }

                                // Format as: Continue straight for 39m, Turn left for 20m, etc.
                                val pattern = Regex("""^(.*)\((\d+\s*[a-zA-Z]*)\)""")
                                val match = pattern.find("$instruction ($distanceStr)")
                                val formattedInstruction = if (instruction.equals("continue straight", ignoreCase = true)) {
                                    // For "continue straight" use: Continue straight for 39m
                                    "Continue straight for $distanceStr"
                                } else if (match != null) {
                                    // For turns, use: In 50m turn right
                                    val action = match.groupValues[1].trim().removeSuffix(",")
                                    val distance = match.groupValues[2].trim()
                                    "In $distance $action"
                                } else {
                                    // Fallback
                                    "In $distanceStr $instruction"
                                }

                                instructions.add(formattedInstruction)
                            }

                            val onUtteranceCompletedListener = TextToSpeech.OnUtteranceCompletedListener { utteranceId ->
                                // After TTS finishes, load the instructions
                                onRouteReceived(routePoints, formattedDistance, formattedDuration, instructions)
                            }

                            // Set the listener to TTS
                            val params = HashMap<String, String>()
                            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "route_found_utterance"
                            tts?.setOnUtteranceCompletedListener(onUtteranceCompletedListener)

                            // Make the final announcement before calling the callback
                            tts?.speak(
                                "Route to nearest matatu stop is found. Follow these instructions.",
                                TextToSpeech.QUEUE_FLUSH,
                                params
                            )
//                            checkRouteAlerts(routePoints) { alerts ->
//                                if (alerts.isNotEmpty()) {
//                                    val alertMessage = alerts.joinToString("\n") { it.description }
//                                    tts?.speak(
//                                        "WARNING!!!: $alertMessage",
//                                        TextToSpeech.QUEUE_ADD,
//                                        null,
//                                        null
//                                    )
//                                }
//                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ROUTE_ERROR", "Error parsing response: ${e.message}")
                    }
                }
            }
        })
    }
    // Helper function to create matatu directions
    fun createMatatuDirections(
        routeName: String,
        startStopName: String,
        endStopName: String,
        duration: String,
        intermediateStops: List<String> = emptyList()
    ): List<String> {
        val directions = mutableListOf<String>()
        
        // Initial boarding instruction
        directions.add("Board matatu route $routeName at $startStopName")
        
        // Add simplified announcements for each intermediate stop
        if (intermediateStops.isNotEmpty()) {
            for (i in 0 until intermediateStops.size - 1) {
                val currentStop = intermediateStops[i]
                val nextStop = intermediateStops[i + 1]
                directions.add("You are at $currentStop, next stop is $nextStop")
            }
            
            // Add the last intermediate stop announcement
            if (intermediateStops.isNotEmpty()) {
                val lastIntermediateStop = intermediateStops.last()
                directions.add("You are at $lastIntermediateStop, next stop is $endStopName")
            }
        } else {
            // If no intermediate stops, just add a simple duration message
            directions.add("Stay on the matatu for approximately $duration")
        }
        
        // Final destination instructions
        directions.add("Look out for $endStopName stop")
        directions.add("Get off at $endStopName")
        
        return directions
    }

    // Function to fetch matatu route from nearest stop to destination stop
    fun fetchMatatuRoute(
        nearestStopResult: NearestStopResult,
        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
    ) {
        Log.d("MatatuPage", "Fetching matatu route from nearest stop to destination stop")
        val route = nearestStopResult.route
        val nearestStop = nearestStopResult.nearbyStop
        val destinationStop = nearestStopResult.destinationStop

        Log.d("MatatuPage", "Fetching matatu route from ${nearestStop.name} to ${destinationStop.name} on route ${route.name}")

        // Start the repeating announcement
        val repeatingAnnouncementScope = CoroutineScope(Dispatchers.Main)
//        val routeFindingJob = repeatingAnnouncementScope.launch {
//            try {
//                // Initial announcement
//                Log.d("MatatuPage", "Starting repeating announcement for matatu route")
//                tts?.speak(
//                    "Finding matatu route to ${destinationStop.name}, please wait",
//                    TextToSpeech.QUEUE_FLUSH,
//                    null,
//                    null
//                )
//
//                // Repeat every 6 seconds until cancelled
//                while (isActive) {
//                    kotlinx.coroutines.delay(6000) // Wait 6 seconds
//                    Log.d("MatatuPage", "Repeating announcement for matatu route")
//                    tts?.speak(
//                        "Finding matatu route to ${destinationStop.name}, please wait",
//                        TextToSpeech.QUEUE_FLUSH,
//                        null,
//                        null
//                    )
//                }
//            } catch (e: Exception) {
//                Log.e("MatatuPage", "Error in repeating announcement: ${e.message}")
//            }
//        }

        // Get the stops for this route from the GTFS data
        val routeStopIds = route.stops
        Log.d("MatatuPage", "Route ${route.name} has ${routeStopIds.size} stops")
        
        // Find the indices of our stops in the route
        val nearestStopIndex = routeStopIds.indexOf(nearestStop.id)
        val destinationStopIndex = routeStopIds.indexOf(destinationStop.id)
        
        Log.d("MatatuPage", "Stop indices in route: nearest=$nearestStopIndex, destination=$destinationStopIndex")
        
        // Create the route path using all stops between nearest and destination
        val matatuRoutePoints = mutableListOf<GeoPoint>()
        
        if (nearestStopIndex != -1 && destinationStopIndex != -1) {
            // Determine the direction (forward or backward along the route)
            val startIdx = minOf(nearestStopIndex, destinationStopIndex)
            val endIdx = maxOf(nearestStopIndex, destinationStopIndex)
            
            // Get all stops between nearest and destination (inclusive)
            val relevantStopIds = routeStopIds.subList(startIdx, endIdx + 1)
            Log.d("MatatuPage", "Using ${relevantStopIds.size} stops to create route path")
            
            // Get the actual stop objects for these IDs
            val stopsOnRoute = relevantStopIds.mapNotNull { stopId ->
                matatuRouteHandler.getStopById(stopId)
            }
            
            if (stopsOnRoute.isNotEmpty()) {
                Log.d("MatatuPage", "Found ${stopsOnRoute.size} stops for this route segment")
                
                // Create a realistic route that follows the road network
                val enhancedPath = mutableListOf<GeoPoint>()
                
                // Process each segment between consecutive stops
                for (i in 0 until stopsOnRoute.size - 1) {
                    val start = GeoPoint(stopsOnRoute[i].latitude, stopsOnRoute[i].longitude)
                    val end = GeoPoint(stopsOnRoute[i + 1].latitude, stopsOnRoute[i + 1].longitude)
                    
                    // Try to fetch a driving route between these stops to follow the road network
                    try {
                        // Use a synchronous version for simplicity
                        val url = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                        val client = OkHttpClient()
                        val request = Request.Builder().url(url).build()
                        
                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string()
                        
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            
                            if (json.has("routes") && json.getJSONArray("routes").length() > 0) {
                                val routeJson = json.getJSONArray("routes").getJSONObject(0)
                                val geometry = routeJson.getJSONObject("geometry")
                                val coordinates = geometry.getJSONArray("coordinates")
                                
                                // Extract the route points
                                val segmentPoints = mutableListOf<GeoPoint>()
                                for (j in 0 until coordinates.length()) {
                                    val point = coordinates.getJSONArray(j)
                                    // Note: GeoJSON format is [longitude, latitude]
                                    segmentPoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
                                }
                                
                                // Add the segment points to our path
                                if (segmentPoints.isNotEmpty()) {
                                    // If this isn't the first segment, skip the first point to avoid duplication
                                    val startIndex = if (enhancedPath.isEmpty()) 0 else 1
                                    enhancedPath.addAll(segmentPoints.subList(startIndex, segmentPoints.size))
                                    Log.d("MatatuPage", "Added ${segmentPoints.size} points from OSRM for segment $i")
                                    continue // Skip the fallback
                                }
                            }
                        }
                        
                        // If we get here, the OSRM request didn't give us usable points
                        Log.d("MatatuPage", "OSRM request didn't return usable points for segment $i, using fallback")
                        
                    } catch (e: Exception) {
                        Log.e("MatatuPage", "Error fetching route segment from OSRM: ${e.message}")
                    }
                    
                    // Fallback: Use our enhanced path generation
                    val intermediatePath = createRealisticRoutePath(start, end)
                    
                    // Skip the first point if this isn't the first segment (to avoid duplication)
                    val startIndex = if (enhancedPath.isEmpty()) 0 else 1
                    enhancedPath.addAll(intermediatePath.subList(startIndex, intermediatePath.size))
                    Log.d("MatatuPage", "Added ${intermediatePath.size} points from fallback for segment $i")
                }
                
                // Use the enhanced path
                matatuRoutePoints.clear()
                matatuRoutePoints.addAll(enhancedPath)
                Log.d("MatatuPage", "Created enhanced path with ${matatuRoutePoints.size} points")
                
            } else {
                Log.e("MatatuPage", "Could not find stops for the route segment")
                // Fallback to direct path
                matatuRoutePoints.add(GeoPoint(nearestStop.latitude, nearestStop.longitude))
                matatuRoutePoints.add(GeoPoint(destinationStop.latitude, destinationStop.longitude))
            }
        } else {
            Log.e("MatatuPage", "Stops not found in route, creating direct path")
            // Fallback to direct path
            matatuRoutePoints.add(GeoPoint(nearestStop.latitude, nearestStop.longitude))
            matatuRoutePoints.add(GeoPoint(destinationStop.latitude, destinationStop.longitude))
        }
        
        // Calculate distance and duration
        var totalDistance = 0.0
        for (i in 0 until matatuRoutePoints.size - 1) {
            totalDistance += calculateDistance(matatuRoutePoints[i], matatuRoutePoints[i + 1])
        }
        
        val distanceKm = totalDistance / 1000.0 // Convert meters to kilometers
        val durationMin = distanceKm * 3 // Assuming average speed of 20 km/h (3 minutes per km)
        
        val formattedDistance = String.format("%.2f Km", distanceKm)
        val formattedDuration = String.format("%.0f Minutes", durationMin)
        
        // Get intermediate stops for London Underground style announcements
        val intermediateStopNames = mutableListOf<String>()
        
        if (nearestStopIndex != -1 && destinationStopIndex != -1) {
            val startIdx = minOf(nearestStopIndex, destinationStopIndex)
            val endIdx = maxOf(nearestStopIndex, destinationStopIndex)
            
            // Get intermediate stop IDs (excluding start and end)
            val intermediateStopIds = routeStopIds.subList(startIdx + 1, endIdx)
            
            if (intermediateStopIds.isNotEmpty()) {
                // Get the actual stop objects
                val intermediateStops = intermediateStopIds.mapNotNull { stopId ->
                    matatuRouteHandler.getStopById(stopId)
                }
                
                // Add all intermediate stop names
                intermediateStopNames.addAll(intermediateStops.map { it.name })
            }
        }
        
        // Create directions using our enhanced function
        val directions = createMatatuDirections(
            routeName = route.name,
            startStopName = nearestStop.name,
            endStopName = destinationStop.name,
            duration = formattedDuration,
            intermediateStops = intermediateStopNames
        )
        
        // Cancel the repeating announcement now that we have the route
//        routeFindingJob.cancel()


        onRouteReceived(matatuRoutePoints, formattedDistance, formattedDuration, directions)
        
        // Set up a listener for when TTS finishes speaking
//        val onUtteranceCompletedListener = TextToSpeech.OnUtteranceCompletedListener { utteranceId ->
//            // After TTS finishes, load the instructions
//            onRouteReceived(matatuRoutePoints, formattedDistance, formattedDuration, directions)
//        }
//
//        // Set the listener to TTS
//        val params = HashMap<String, String>()
//        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "route_found_utterance"
//        tts?.setOnUtteranceCompletedListener(onUtteranceCompletedListener)
//
//        // Make the final announcement before calling the callback
//        tts?.speak(
//            "Route to ${destinationStop.name} is found. Follow these instructions.",
//            TextToSpeech.QUEUE_FLUSH,
//            params
//        )
    }

    // Function to fetch coordinates from a location name
    fun fetchCoordinate(destination: String, context: Context, onResult: (GeoPoint?) -> Unit) {
        val geocoder = android.location.Geocoder(context)
        try {
            val addresses = geocoder.getFromLocationName(destination, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                onResult(GeoPoint(address.latitude, address.longitude))
            } else {
                onResult(null)
            }
        } catch (e: Exception) {
            Log.e("MatatuPage", "Error geocoding address: ${e.message}")
            onResult(null)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            Log.d("MatatuPage", "Location permission granted, fetching user location")
            fetchUserLocationn(context, fusedLocationClient) { location -> 
                userLocation = location
                Log.d("MatatuPage", "User location received: $location")
            }
        } else {
            Log.d("MatatuPage", "Requesting location permission")
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_back_icon),
                contentDescription = "Back",
                tint = Color.Black,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Matatu Routes",
                fontSize = 22.sp,
                color = Color.Black,
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Show error message if any
        errorMessage?.let { error ->
            Text(
                text = error,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }
        OutlinedTextField(
            value = destinationText,
            onValueChange = { newText ->
                destinationText = newText
                // Get suggestions as user types
                coroutineScope.launch {
                    try {
                        val suggestions = gtfsDataHandler.getSuggestions(newText)
                        Log.d("MatatuPage", "Got ${suggestions.size} suggestions for '$newText'")
                        locationSuggestions = suggestions
                        // Always show suggestions if text is not empty and at least 2 chars
                        showSuggestions = newText.length >= 2
                        Log.d("MatatuPage", "showSuggestions set to $showSuggestions")
                    } catch (e: Exception) {
                        Log.e("MatatuPage", "Error getting suggestions: ${e.message}")
                        e.printStackTrace()
                    }
                }
            },
            placeholder = { Text("Enter your destination") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(1f),
            shape = RoundedCornerShape(20.dp),
            trailingIcon = {
                Button(
                    onClick = {
                        fetchCoordinate(destinationText, context) { location ->
                            if (location != null && userLocation != null) {
                                destinationLocation = location

                                // First, try to find the nearest matatu stop that serves this destination
                                val result = matatuRouteHandler.findNearestStopToDestination(
                                    userLocation!!, location
                                )

                                if (result != null) {
                                    nearestStopResult = result
                                    showingDirectionsToStop = true
                                    routePoints = emptyList() // Clear any existing direct route

                                    // Get walking directions to the nearest stop
                                    val nearestStopLocation = GeoPoint(result.nearbyStop.latitude, result.nearbyStop.longitude)
                                    fetchWalkingRoute(userLocation!!, nearestStopLocation) { newRoutePoints: List<GeoPoint>, distance: String, duration: String, directions: List<String> ->
                                        walkingRouteToStop = newRoutePoints
                                        walkingDirections = directions
                                        walkingDistance = distance
                                        walkingDuration = duration

                                        // UI update happens IMMEDIATELY after walking route is found!
                                        showDirectionsPanel = true
                                        currentDirectionIndex = 0
                                        // Optionally, announce the first walking instruction
                                        if (walkingDirections.isNotEmpty()) {
                                            tts?.speak(walkingDirections[0], TextToSpeech.QUEUE_FLUSH, null, null)
                                        }
                                    }
                                    // Fetch the matatu route in the background (do NOT wait for it)
                                    fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                        matatuRoutePath = matatuRoutePoints
                                        matatuRouteDirections = matatuDirections
                                        matatuRouteDistance = matatuDistance
                                        matatuRouteDuration = matatuDuration
                                        Log.d("MatatuPage", "Received matatu route with ${matatuRoutePoints.size} points")
                                        // Clear loading message if needed
                                        errorMessage = null
                                    }
                                }
                            } else {
                                errorMessage = "Invalid destination or location not available."
                            }
                        }
                    },
                    modifier = Modifier.padding(4.dp),
                            enabled = destinationText.trim().length >= 2 // Disable until destination has enough input
                ) {
                    Text("Find matatu route")
                }
            }
        )

        // Debug text to show suggestion state
        Text(
            text = "Suggestions: ${if (showSuggestions) "Visible" else "Hidden"} (${locationSuggestions.size} items)",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Suggestions dropdown
        if (showSuggestions) {
            Log.d("MatatuPage", "Rendering suggestions dropdown with ${locationSuggestions.size} items")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 200.dp)
                    .zIndex(2f),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (locationSuggestions.isEmpty()) {
                    // Show a message when no suggestions are available
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching locations found")
                    }
                } else {
                    LazyColumn {
                        items(locationSuggestions) { suggestion ->
                            SuggestionItem(
                                suggestion = suggestion,
                                onSuggestionSelected = { selectedSuggestion ->
                                    destinationText = selectedSuggestion.name
                                    showSuggestions = false
                                    
                                    // Provide feedback that we're processing the selection
                                    tts?.speak("Finding nearest stage to ${selectedSuggestion.name}, please wait", TextToSpeech.QUEUE_FLUSH, null, null)
                                    errorMessage = null // Clear any previous error messages

                                    // If we have coordinates for this suggestion, use them directly
                                    if (selectedSuggestion.lat != null && selectedSuggestion.lon != null) {
                                        val location = GeoPoint(selectedSuggestion.lat, selectedSuggestion.lon)
                                        destinationLocation = location
                                        
                                        Log.d("MatatuPage", "Selected location: ${selectedSuggestion.name} at ${selectedSuggestion.lat}, ${selectedSuggestion.lon}")

                                        if (userLocation != null) {
                                            Log.d("MatatuPage", "User location is available: $userLocation")

                                            try {
                                                // First, try to find the nearest matatu stop that serves this destination
                                                val result = matatuRouteHandler.findNearestStopToDestination(
                                                    userLocation!!, location
                                                )

                                                if (result != null) {
                                                    Log.d("MatatuPage", "Found nearest stop: ${result.nearbyStop.name} and destination stop: ${result.destinationStop.name}")
                                                    nearestStopResult = result
                                                    showingDirectionsToStop = true
                                                    routePoints = emptyList() // Clear any existing direct route

                                                    // Get walking directions to the nearest stop
                                                    val nearestStopLocation = GeoPoint(result.nearbyStop.latitude, result.nearbyStop.longitude)
                                                    fetchWalkingRoute(userLocation!!, nearestStopLocation) { newRoutePoints: List<GeoPoint>, distance: String, duration: String, directions: List<String> ->
                                                        walkingRouteToStop = newRoutePoints
                                                        walkingDirections = directions
                                                        walkingDistance = distance
                                                        walkingDuration = duration

                                                        Log.d("MatatuPage", "Received walking route with ${newRoutePoints.size} points")
                                                        
                                                        // UI update happens IMMEDIATELY after walking route is found!
                                                        showDirectionsPanel = true
                                                        currentDirectionIndex = 0
                                                        
                                                        // Optionally, announce the first walking instruction
                                                        if (walkingDirections.isNotEmpty()) {
                                                            tts?.speak(walkingDirections[0], TextToSpeech.QUEUE_FLUSH, null, null)
                                                        }
                                                        
                                                        // Now fetch the matatu route from nearest stop to destination stop
                                                        fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                                            matatuRoutePath = matatuRoutePoints
                                                            matatuRouteDirections = matatuDirections
                                                            matatuRouteDistance = matatuDistance
                                                            matatuRouteDuration = matatuDuration
                                                            
                                                            Log.d("MatatuPage", "Received matatu route with ${matatuRoutePoints.size} points")
                                                            
                                                            // Clear loading message
                                                            errorMessage = null
                                                            
                                                            // Combine walking and matatu directions for the complete journey
                                                            val completeDirections = mutableListOf<String>()
                                                            completeDirections.add("--- Walking to Matatu Stop ---")
                                                            completeDirections.addAll(directions)
                                                            completeDirections.add("--- Matatu Journey ---")
                                                            completeDirections.addAll(matatuDirections)
                                                            
                                                            // Update the UI
                                                            showDirectionsPanel = true
                                                            currentDirectionIndex = 0
                                                        }
                                                    }
                                                } else {
                                                    Log.e("MatatuPage", "Could not find a valid matatu route to the destination")
                                                    errorMessage = "Sorry, I couldn't find a matatu route to ${selectedSuggestion.name}. This location might not be served by any matatu routes in our database."
                                                    tts?.speak(
                                                        "Sorry, I couldn't find a matatu route to ${selectedSuggestion.name}. This location might not be served by any matatu routes in our database.",
                                                        TextToSpeech.QUEUE_FLUSH,
                                                        null,
                                                        null
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MatatuPage", "Error finding matatu route: ${e.message}")
                                                errorMessage = "An error occurred while searching for matatu routes. Please try again."
                                                tts?.speak(
                                                    "An error occurred while searching for matatu routes. Please try again.",
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    null
                                                )
                                            }
                                        } else {
                                            Log.e("MatatuPage", "User location is not available")
                                            errorMessage = "Your location is not available. Please enable location services."
                                            tts?.speak(
                                                "Your location is not available. Please enable location services.",
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                null
                                            )
                                        }
                                    } else {
                                        // Otherwise, geocode the name
                                        fetchCoordinate(selectedSuggestion.name, context) { location ->
                                            if (location != null) {
                                                destinationLocation = location
                                                if (userLocation != null) {
                                                    try {
                                                        // First, try to find the nearest matatu stop that serves this destination
                                                        val result = matatuRouteHandler.findNearestStopToDestination(
                                                            userLocation!!, location
                                                        )

                                                        if (result != null) {
                                                            Log.d("MatatuPage", "Found nearest stop: ${result.nearbyStop.name} and destination stop: ${result.destinationStop.name}")
                                                            nearestStopResult = result
                                                            showingDirectionsToStop = true
                                                            routePoints = emptyList() // Clear any existing direct route

                                                            // Get walking directions to the nearest stop
                                                            val nearestStopLocation = GeoPoint(result.nearbyStop.latitude, result.nearbyStop.longitude)
                                                            fetchWalkingRoute(userLocation!!, nearestStopLocation) { newRoutePoints: List<GeoPoint>, distance: String, duration: String, directions: List<String> ->
                                                                walkingRouteToStop = newRoutePoints
                                                                walkingDirections = directions
                                                                walkingDistance = distance
                                                                walkingDuration = duration
                                                                
                                                                Log.d("MatatuPage", "Received walking route with ${newRoutePoints.size} points")
                                                                
                                                                // UI update happens IMMEDIATELY after walking route is found!
                                                                showDirectionsPanel = true
                                                                currentDirectionIndex = 0
                                                                
                                                                // Optionally, announce the first walking instruction
                                                                if (walkingDirections.isNotEmpty()) {
                                                                    tts?.speak(walkingDirections[0], TextToSpeech.QUEUE_FLUSH, null, null)
                                                                }
                                                                
                                                                // Now fetch the matatu route from nearest stop to destination stop
                                                                fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                                                    matatuRoutePath = matatuRoutePoints
                                                                    matatuRouteDirections = matatuDirections
                                                                    matatuRouteDistance = matatuDistance
                                                                    matatuRouteDuration = matatuDuration
                                                                    
                                                                    Log.d("MatatuPage", "Received matatu route with ${matatuRoutePoints.size} points")
                                                                    
                                                                    // Clear loading message
                                                                    errorMessage = null
                                                                    
                                                                    // Combine walking and matatu directions for the complete journey
                                                                    val completeDirections = mutableListOf<String>()
                                                                    completeDirections.add("--- Walking to Matatu Stop ---")
                                                                    completeDirections.addAll(directions)
                                                                    completeDirections.add("--- Matatu Journey ---")
                                                                    completeDirections.addAll(matatuDirections)
                                                                    
                                                                    // Update the UI
                                                                    showDirectionsPanel = true
                                                                    currentDirectionIndex = 0
                                                                }
                                                            }
                                                        } else {
                                                            Log.e("MatatuPage", "Could not find a valid matatu route to the destination")
                                                            errorMessage = "Sorry, I couldn't find a matatu route to ${selectedSuggestion.name}. This location might not be served by any matatu routes in our database."
                                                            tts?.speak(
                                                                "Sorry, I couldn't find a matatu route to ${selectedSuggestion.name}. This location might not be served by any matatu routes in our database.",
                                                                TextToSpeech.QUEUE_FLUSH,
                                                                null,
                                                                null
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("MatatuPage", "Error finding matatu route: ${e.message}")
                                                        errorMessage = "An error occurred while searching for matatu routes. Please try again."
                                                        tts?.speak(
                                                            "An error occurred while searching for matatu routes. Please try again.",
                                                            TextToSpeech.QUEUE_FLUSH,
                                                            null,
                                                            null
                                                        )
                                                    }
                                                }
                                            } else {
                                                errorMessage = "Could not find coordinates for ${selectedSuggestion.name}"
                                                tts?.speak(
                                                    "Could not find coordinates for ${selectedSuggestion.name}. Please try a different location.",
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    null
                                                )
                                            }
                                        }
                                    }
                                },
                                tts = tts
                            )
                        }
                    }
                }
            }
        }
        // Directions panel
        if (showDirectionsPanel && walkingDirections.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE3F2FD))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Walking Directions to Nearest Matatu Stop",
                    fontSize = 18.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text("Distance: $walkingDistance, Duration: $walkingDuration", fontSize = 14.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                // Only show the current instruction
                if (currentDirectionIndex in walkingDirections.indices) {
                    DirectionItem(
                        direction = walkingDirections[currentDirectionIndex],
                        isActive = true,
                        onClick = { tts?.speak(walkingDirections[currentDirectionIndex], TextToSpeech.QUEUE_FLUSH, null, null) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (currentDirectionIndex > 0) {
                                currentDirectionIndex--
                                tts?.speak(walkingDirections[currentDirectionIndex], TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        },
                        enabled = currentDirectionIndex > 0
                    ) {
                        Text("Previous")
                    }
                    Button(
                        onClick = {
                            if (currentDirectionIndex < walkingDirections.size - 1) {
                                currentDirectionIndex++
                                tts?.speak(walkingDirections[currentDirectionIndex], TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        },
                        enabled = currentDirectionIndex < walkingDirections.size - 1
                    ) {
                        Text("Next")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Show button to proceed to matatu journey after last step
                if (currentDirectionIndex == walkingDirections.size - 1) {
                    Button(
                        onClick = {
                            showDirectionsPanel = false
                            tts?.speak("You have reached the matatu stop. Here are your matatu journey instructions.", TextToSpeech.QUEUE_FLUSH, null, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Matatu Journey")
                    }
                }
            }
        }

        // Show matatu journey instructions ONLY after user confirms arrival at stop
        if (!showDirectionsPanel && matatuRouteDirections.isNotEmpty()) {
            // Ensure the introductory statement and first instruction are read aloud when entering the matatu journey panel
            LaunchedEffect(key1 = !showDirectionsPanel) {
                if (matatuRouteDirections.isNotEmpty()) {
                    tts?.speak("You have arrived at the stop. Here are your matatu journey instructions.", TextToSpeech.QUEUE_FLUSH, null, null)
                    tts?.speak(matatuRouteDirections[0], TextToSpeech.QUEUE_ADD, null, null)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F8E9))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Matatu Journey Instructions",
                    fontSize = 18.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text("Distance: $matatuRouteDistance, Duration: $matatuRouteDuration", fontSize = 14.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                // Only show the current matatu instruction
                if (currentMatatuDirectionIndex in matatuRouteDirections.indices) {
                    DirectionItem(
                        direction = matatuRouteDirections[currentMatatuDirectionIndex],
                        isActive = true,
                        onClick = { tts?.speak(matatuRouteDirections[currentMatatuDirectionIndex], TextToSpeech.QUEUE_FLUSH, null, null) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (currentMatatuDirectionIndex > 0) {
                                currentMatatuDirectionIndex--
                                tts?.speak(matatuRouteDirections[currentMatatuDirectionIndex], TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        },
                        enabled = currentMatatuDirectionIndex > 0
                    ) {
                        Text("Previous")
                    }
                    Button(
                        onClick = {
                            if (currentMatatuDirectionIndex < matatuRouteDirections.size - 1) {
                                currentMatatuDirectionIndex++
                                tts?.speak(matatuRouteDirections[currentMatatuDirectionIndex], TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        },
                        enabled = currentMatatuDirectionIndex < matatuRouteDirections.size - 1
                    ) {
                        Text("Next")
                    }
                }
            }
        }

        //mapview
        Box(modifier = Modifier
            .fillMaxWidth()
            .size(width = 300.dp, height = 500.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
        ) {
            AndroidView(factory = { ctx ->
                MapView(ctx).apply {
                    Configuration.getInstance()
                        .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    controller.setZoom(18.0)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                }
            }, update = { mapView ->
                userLocation?.let { location ->
                    mapView.controller.setCenter(location)
                    mapView.overlays.clear()

                    val userMarker = Marker(mapView).apply {
                        position = location
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "You are here"
                    }
                    mapView.overlays.add(userMarker)

                    destinationLocation?.let { destination ->
                        val destinationMarker = Marker(mapView).apply {
                            position = destination
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Destination"
                        }
                        mapView.overlays.add(destinationMarker)
                    }

                    // If we're showing directions to a matatu stop
                    if (showingDirectionsToStop && nearestStopResult != null) {
                        Log.d("MatatuPage", "Showing matatu route directions")

                        // Add marker for the nearest matatu stop
                        val nearestStop = nearestStopResult!!.nearbyStop
                        val nearestStopLocation = GeoPoint(nearestStop.latitude, nearestStop.longitude)
                        val nearestStopMarker = Marker(mapView).apply {
                            position = nearestStopLocation
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "${nearestStop.name} (Matatu Stop)"
                            // Use a different icon or color for matatu stops
                            // If bus_icon doesn't exist, it will use the default marker
                            try {
                                icon = context.getDrawable(R.drawable.bookmark_icon)
                            } catch (e: Exception) {
                                Log.e("MatatuPage", "Error setting icon: ${e.message}")
                            }
                        }
                        mapView.overlays.add(nearestStopMarker)

                        // Add marker for the destination matatu stop
                        val destStop = nearestStopResult!!.destinationStop
                        val destStopLocation = GeoPoint(destStop.latitude, destStop.longitude)
                        val destStopMarker = Marker(mapView).apply {
                            position = destStopLocation
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "${destStop.name} (Destination Stop)"
                            // Use a different icon or color for matatu stops
                            try {
                                icon = context.getDrawable(R.drawable.bookmark_icon)
                            } catch (e: Exception) {
                                Log.e("MatatuPage", "Error setting icon: ${e.message}")
                            }
                        }
                        mapView.overlays.add(destStopMarker)

                        // Show walking route to the nearest stop
                        if (walkingRouteToStop.isNotEmpty()) {
                            Log.d("MatatuPage", "Drawing walking route with ${walkingRouteToStop.size} points")
                            
                            // Debug the first few and last few points of the walking route
                            for (i in walkingRouteToStop.indices) {
                                if (i < 3 || i > walkingRouteToStop.size - 4) {
                                    val point = walkingRouteToStop[i]
                                    Log.d("MatatuPage", "Walking route point $i: lat=${point.latitude}, lon=${point.longitude}")
                                }
                            }
                            
                            val walkingPolyline = Polyline().apply {
                                setPoints(walkingRouteToStop)
                                color = Color.Blue.hashCode()
                                width = 12f // Increased width for better visibility
                                isGeodesic = true // Make sure the line follows the curvature of the earth
                                outlinePaint.strokeWidth = 12f
                                // Create circular dots with larger size (20f for dot size, 30f for gap)
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 30f), 0f)
                            }
                            mapView.overlays.add(walkingPolyline)
                            
                            // Show matatu route from nearest stop to destination stop
                            if (matatuRoutePath.isNotEmpty()) {
                                Log.d("MatatuPage", "Drawing matatu route with ${matatuRoutePath.size} points")
                                
                                // Optimize route drawing
                                val routeKey = "${matatuRoutePath.first().latitude},${matatuRoutePath.first().longitude}-${matatuRoutePath.last().latitude},${matatuRoutePath.last().longitude}"
                                val cachedRoute = routeCache[routeKey]
                                
                                if (cachedRoute != null) {
                                    // Use cached route
                                    val polyline = Polyline().apply {
                                        setPoints(cachedRoute)
                                        this.color = Color.Blue.hashCode()
                                        width = 6f // Reduced width for matatu route
                                        isGeodesic = true
                                        outlinePaint.strokeWidth = 6f
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    }
                                    mapView.overlays.add(polyline)
                                } else {
                                    // Cache and draw new route
                                    routeCache[routeKey] = matatuRoutePath
                                    val polyline = Polyline().apply {
                                        setPoints(matatuRoutePath)
                                        this.color = Color.Blue.hashCode()
                                        width = 6f // Reduced width for matatu route
                                        isGeodesic = true
                                        outlinePaint.strokeWidth = 6f
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    }
                                    mapView.overlays.add(polyline)
                                }
                            }
                        }
                    }
                    // Show direct route if not showing matatu directions
                    else if (routePoints.isNotEmpty()) {
                        Log.d("MatatuPage", "Drawing direct route with ${routePoints.size} points")
                        
                        // Debug the first few and last few points of the direct route
                        for (i in routePoints.indices) {
                            if (i < 3 || i > routePoints.size - 4) {
                                val point = routePoints[i]
                                Log.d("MatatuPage", "Direct route point $i: lat=${point.latitude}, lon=${point.longitude}")
                            }
                        }
                        
                        val polyline = Polyline().apply {
                            setPoints(routePoints)
                            color = Color.Blue.hashCode()
                            width = 12f // Increased width for better visibility
                            isGeodesic = true // Make sure the line follows the curvature of the earth
                            outlinePaint.strokeWidth = 12f
                            // Create circular dots with larger size (20f for dot size, 30f for gap)
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 30f), 0f)
                        }
                        mapView.overlays.add(polyline)
                        
                        // Show matatu route from nearest stop to destination stop
                        if (matatuRoutePath.isNotEmpty()) {
                            Log.d("MatatuPage", "Drawing matatu route with ${matatuRoutePath.size} points")
                            
                            // Optimize route drawing
                            val routeKey = "${matatuRoutePath.first().latitude},${matatuRoutePath.first().longitude}-${matatuRoutePath.last().latitude},${matatuRoutePath.last().longitude}"
                            val cachedRoute = routeCache[routeKey]
                            
                            if (cachedRoute != null) {
                                // Use cached route
                                val polyline = Polyline().apply {
                                    setPoints(cachedRoute)
                                    this.color = Color.Blue.hashCode()
                                    width = 6f // Reduced width for matatu route
                                    isGeodesic = true
                                    outlinePaint.strokeWidth = 6f
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                }
                                mapView.overlays.add(polyline)
                            } else {
                                // Cache and draw new route
                                routeCache[routeKey] = matatuRoutePath
                                val polyline = Polyline().apply {
                                    setPoints(matatuRoutePath)
                                    this.color = Color.Blue.hashCode()
                                    width = 6f // Reduced width for matatu route
                                    isGeodesic = true
                                    outlinePaint.strokeWidth = 6f
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                }
                                mapView.overlays.add(polyline)
                            }
                        }
                    }
                    mapView.invalidate()
                }
            }, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)
    }
}

@SuppressLint("MissingPermission")
fun fetchUserLocationn(context: Context, fusedLocationClient: FusedLocationProviderClient, onLocationReceived: (GeoPoint) -> Unit) {
    // Create a location request for high accuracy
    val locationRequest = LocationRequest.Builder(5000)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()
    
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        // First try to get the last known location as it's faster
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                Log.d("MatatuPage", "Using last known location: ${location.latitude}, ${location.longitude}")
                onLocationReceived(GeoPoint(location.latitude, location.longitude))
            } else {
                // If last location is null, request a fresh location update
                Log.d("MatatuPage", "Last location is null, requesting location updates")
                
                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            Log.d("MatatuPage", "Received location update: ${location.latitude}, ${location.longitude}")
                            onLocationReceived(GeoPoint(location.latitude, location.longitude))
                            // Remove updates after getting a location
                            fusedLocationClient.removeLocationUpdates(this)
                            break
                        }
                    }
                }
                
                // Request location updates
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        }.addOnFailureListener { e ->
            Log.e("MatatuPage", "Error getting last location: ${e.message}")
            // Fallback to a default location in Nairobi if we can't get the user's location
            Log.d("MatatuPage", "Using default location in Nairobi")
            onLocationReceived(GeoPoint(-1.286389, 36.817223)) // Default to Nairobi city center
        }
    } else {
        Log.e("MatatuPage", "Location permission not granted")
        // Fallback to a default location in Nairobi
        onLocationReceived(GeoPoint(-1.286389, 36.817223)) // Default to Nairobi city center
    }
}

/**
 * Function to test route finding and diagnose issues
 */
private fun testRouteFind(
    startLocation: GeoPoint,
    destinationLocation: GeoPoint,
    context: Context,
    lifecycleScope: CoroutineScope
) {
    Log.d("MatatuPage", "TEST: Testing route finding from $startLocation to $destinationLocation")
    
    // Create a new GTFS data handler for testing
    val gtfsDataHandler = GtfsDataHandler(context)
    val routeHandler = MatatuRouteHandler(context, gtfsDataHandler)
    
    lifecycleScope.launch {
        try {
            // Initialize both handlers
            gtfsDataHandler.initialize()
            routeHandler.initialize()
            Log.d("MatatuPage", "TEST: Route handler initialized")
            
            // Find the nearest stop to the user
            val userStop = routeHandler.findNearestStop(startLocation)
            if (userStop != null) {
                Log.d("MatatuPage", "TEST: Nearest stop to user: ${userStop.name} (${userStop.id})")
                
                // Find the nearest stop to the destination
                val destStop = routeHandler.findNearestStop(destinationLocation)
                if (destStop != null) {
                    Log.d("MatatuPage", "TEST: Nearest stop to destination: ${destStop.name} (${destStop.id})")
                    
                    // Find routes for the user's stop
                    val userRoutes = routeHandler.findRoutesForStop(userStop.id)
                    Log.d("MatatuPage", "TEST: Found ${userRoutes.size} routes for user's stop")
                    
                    // Find routes for the destination stop
                    val destRoutes = routeHandler.findRoutesForStop(destStop.id)
                    Log.d("MatatuPage", "TEST: Found ${destRoutes.size} routes for destination stop")
                    
                    // Find common routes
                    val commonRoutes = userRoutes.filter { userRoute ->
                        destRoutes.any { destRoute -> destRoute.id == userRoute.id }
                    }
                    
                    Log.d("MatatuPage", "TEST: Found ${commonRoutes.size} common routes")
                    
                    if (commonRoutes.isNotEmpty()) {
                        val firstRoute = commonRoutes[0]
                        Log.d("MatatuPage", "TEST: First common route: ${firstRoute.id} - ${firstRoute.name}")
                        Log.d("MatatuPage", "TEST: Route stops: ${firstRoute.stops.joinToString()}")
                        
                        // Check if both stops are on this route
                        val userStopIndex = firstRoute.stops.indexOf(userStop.id)
                        val destStopIndex = firstRoute.stops.indexOf(destStop.id)
                        
                        Log.d("MatatuPage", "TEST: User stop index: $userStopIndex, Destination stop index: $destStopIndex")
                    }
                } else {
                    Log.e("MatatuPage", "TEST: Could not find nearest stop to destination")
                }
            } else {
                Log.e("MatatuPage", "TEST: Could not find nearest stop to user")
            }
        } catch (e: Exception) {
            Log.e("MatatuPage", "TEST: Error in test route finding: ${e.message}", e)
        }
    }
}

@Composable
fun SuggestionItem(
    suggestion: GtfsLocation,
    onSuggestionSelected: (GtfsLocation) -> Unit,
    tts: TextToSpeech?
) {
    val locationTypeText = when (suggestion.type) {
        apis.LocationType.STOP -> "Stop"
        apis.LocationType.ROUTE -> "Route"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onSuggestionSelected(suggestion)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = locationTypeText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
    Divider()
}

// Function to animate movement along a route
fun animateAlongRoute(
    mapView: MapView,
    route: List<GeoPoint>,
    color: Int,
    onComplete: () -> Unit = {}
) {
    if (route.isEmpty()) return
    
    // Create a marker for the vehicle
    val vehicleMarker = Marker(mapView).apply {
        position = route.first()
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = mapView.context.getDrawable(R.drawable.bookmark_icon)?.apply {
            setBounds(0, 0, 30, 30)
        }
        title = "Matatu"
    }
    mapView.overlays.add(vehicleMarker)
    
    // Draw the route polyline
    val polyline = Polyline().apply {
        setPoints(route)
        this.color = color
        width = 5f
        isGeodesic = true
    }
    mapView.overlays.add(polyline)
    
    // Optimize animation with fixed frame rate
    var currentIndex = 0
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    val frameRate = 16L // ~60 FPS
    
    val runnable = object : Runnable {
        override fun run() {
            if (currentIndex < route.size) {
                vehicleMarker.position = route[currentIndex]
                mapView.invalidate()
                currentIndex++
                
                // Use fixed frame rate instead of distance-based delay
                handler.postDelayed(this, frameRate)
            } else {
                // Animation complete
                mapView.overlays.remove(vehicleMarker)
                mapView.invalidate()
                onComplete()
            }
        }
    }
    
    // Start the animation
    handler.post(runnable)
}

/**
 * Create a realistic route path between two points
 */
fun createRealisticRoutePath(startPoint: GeoPoint, endPoint: GeoPoint): List<GeoPoint> {
    val result = mutableListOf<GeoPoint>()
    
    // Add the start point
    result.add(startPoint)
    
    // Calculate the direct distance between points
    val distance = calculateDistance(startPoint, endPoint)
    
    // Determine how many intermediate points to add based on distance
    val numPoints = max(2, min(10, (distance * 20).toInt()))
    
    // Create a more realistic path by adding multiple intermediate points
    // that follow a slightly curved path rather than a straight line
    
    // Calculate the midpoint between start and end
    val midLat = (startPoint.latitude + endPoint.latitude) / 2.0
    val midLon = (startPoint.longitude + endPoint.longitude) / 2.0
    
    // Calculate perpendicular offset for curve
    // (perpendicular to the direct line between points)
    val dx = endPoint.longitude - startPoint.longitude
    val dy = endPoint.latitude - startPoint.latitude
    val length = sqrt(dx * dx + dy * dy)
    
    // Normalize the perpendicular vector
    val perpX = -dy / length
    val perpY = dx / length
    
    // Create a curved path with multiple points
    for (i in 1 until numPoints) {
        val ratio = i.toDouble() / numPoints
        
        // Base position along straight line
        val baseLat = startPoint.latitude + (endPoint.latitude - startPoint.latitude) * ratio
        val baseLon = startPoint.longitude + (endPoint.longitude - startPoint.longitude) * ratio
        
        // Calculate offset for curve (maximum at midpoint, decreasing toward endpoints)
        // This creates a curved path that's more pronounced in the middle
        val curveRatio = 1.0 - abs(ratio - 0.5) * 2.0
        val curveStrength = distance * 0.05 // Adjust curve strength based on distance
        val offset = curveRatio * curveStrength
        
        // Apply offset perpendicular to direct path
        val offsetLat = perpY * offset
        val offsetLon = perpX * offset
        
        // Add some randomness to make it look more natural
        val randomFactor = 0.0002 * (Math.random() - 0.5)
        
        result.add(GeoPoint(
            baseLat + offsetLat + randomFactor,
            baseLon + offsetLon + randomFactor
        ))
    }
    
    // Add the end point
    result.add(endPoint)
    
    return result
}

/**
 * Add intermediate points between two locations
 */
fun addIntermediatePointsBetweenStops(routePoints: MutableList<GeoPoint>, start: GeoPoint, end: GeoPoint) {
    // Add 3-5 intermediate points to create a more realistic path
    val numPoints = 3 + (Math.random() * 2).toInt() // 3-4 points

    for (i in 1..numPoints) {
        val ratio = i.toFloat() / (numPoints + 1)
        val lat = start.latitude + (end.latitude - start.latitude) * ratio
        val lon = start.longitude + (end.longitude - start.longitude) * ratio

        // Add some randomness for a more realistic path
        val latOffset = (Math.random() * 0.0005 - 0.00025)
        val lonOffset = (Math.random() * 0.0005 - 0.00025)

        routePoints.add(GeoPoint(lat + latOffset, lon + lonOffset))
    }
}

/**
 * Calculate the distance between two points in kilometers
 */
fun calculateDistances(point1: GeoPoint, point2: GeoPoint): Double {
    val R = 6371.0 // Earth radius in kilometers

    val lat1 = Math.toRadians(point1.latitude)
    val lon1 = Math.toRadians(point1.longitude)
    val lat2 = Math.toRadians(point2.latitude)
    val lon2 = Math.toRadians(point2.longitude)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon/2) * Math.sin(dLon/2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))

    return R * c
}

/**
 * Read only the current direction to the user
 */
fun readCurrentDirection(tts: TextToSpeech, direction: String) {
    tts.speak(direction, TextToSpeech.QUEUE_FLUSH, null, null)
}

/**
 * Read the initial route announcement with destination information
 */
fun readRouteAnnouncement(tts: TextToSpeech, destinationName: String = "your destination") {
    // Announce that we've found a route to the nearest stop for the destination
//    tts.speak(
//        "Route to $destinationName is found. Follow these instructions.",
//        TextToSpeech.QUEUE_FLUSH,
//        null,
//        null
//    )

    // Add a delay using playSilentUtterance before the first instruction
    tts.playSilentUtterance(3000, TextToSpeech.QUEUE_ADD, null)
}

/**
 * Composable for displaying a single direction item
 */
@Composable
fun DirectionItem(
    direction: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) Color(0xFFE3F2FD) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Direction icon
        Icon(
            painter = painterResource(id = R.drawable.map_icon),
            contentDescription = null,
            tint = if (isActive) Color.Blue else Color.Gray,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 8.dp)
        )

        // Direction text
        Text(
            text = direction,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) Color.Black else Color.DarkGray
        )
    }
    Divider(thickness = 0.5.dp)
}