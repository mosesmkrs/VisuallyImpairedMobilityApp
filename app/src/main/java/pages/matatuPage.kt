package pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import apis.MatatuStop
import apis.NearestStopResult
import com.example.newapp.R
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.util.Locale
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

    // Function to fetch walking route
    fun fetchWalkingRoute(
        start: GeoPoint,
        end: GeoPoint,
        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
    ) {
        val apiKey = "456b9753-702c-48ca-91d4-1c21e1b015a9" // Replace with your GraphHopper API Key
        val url = "https://graphhopper.com/api/1/route?point=${start.latitude},${start.longitude}&point=${end.latitude},${end.longitude}&profile=foot&points_encoded=false&instructions=true&key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ROUTE_ERROR", "Failed to get route: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    val json = JSONObject(responseBody.string())

                    if (!json.has("paths") || json.getJSONArray("paths").length() == 0) {
                        Log.e("ROUTE_ERROR", "No route found.")
                        return
                    }

                    val path = json.getJSONArray("paths").getJSONObject(0)
                    val distanceMeters = path.getDouble("distance")
                    val timeMillis = path.getDouble("time")

                    val distanceKm = distanceMeters / 1000.0
                    val durationMin = timeMillis / (1000.0 * 60.0)

                    val formattedDistance = String.format("%.2f Km", distanceKm)
                    val formattedDuration = String.format("%.2f Minutes", durationMin)

                    // Extract route points
                    val coordinates = path.getJSONObject("points").getJSONArray("coordinates")
                    val routePoints = mutableListOf<GeoPoint>()
                    for (i in 0 until coordinates.length()) {
                        val point = coordinates.getJSONArray(i)
                        routePoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
                    }

                    // Extract step-by-step walking directions
                    val instructionsArray = path.getJSONArray("instructions")
                    val instructions = mutableListOf<String>()
                    for (i in 0 until instructionsArray.length()) {
                        val step = instructionsArray.getJSONObject(i)
                        val text = step.getString("text")
                        val distance = step.getDouble("distance") / 1000.0
                        val formattedStep = "$text (${String.format("%.2f Km", distance)})"
                        instructions.add(formattedStep)
                    }

                    onRouteReceived(routePoints, formattedDistance, formattedDuration, instructions)
                }
            }
        })
    }
    // Helper function to create a simple route path between two stops
    fun createSimpleRoutePath(startStop: MatatuStop, endStop: MatatuStop): List<GeoPoint> {
        val startPoint = GeoPoint(startStop.latitude, startStop.longitude)
        val endPoint = GeoPoint(endStop.latitude, endStop.longitude)

        // Create a more realistic path with some intermediate points
        return createRealisticRoutePath(startPoint, endPoint)
    }

    // Helper function to extract the relevant portion of a route path
    fun extractRelevantRoutePath(
        routePath: List<GeoPoint>,
        startPoint: GeoPoint,
        endPoint: GeoPoint
    ): List<GeoPoint> {
        // Find the closest points on the route path to the start and end points
        var closestStartIndex = 0
        var closestEndIndex = routePath.size - 1
        var minStartDistance = Double.MAX_VALUE
        var minEndDistance = Double.MAX_VALUE

        for (i in routePath.indices) {
            val point = routePath[i]

            val startDistance = calculateDistance(startPoint, point)
            if (startDistance < minStartDistance) {
                minStartDistance = startDistance
                closestStartIndex = i
            }

            val endDistance = calculateDistance(endPoint, point)
            if (endDistance < minEndDistance) {
                minEndDistance = endDistance
                closestEndIndex = i
            }
        }

        // Ensure start comes before end
        if (closestStartIndex > closestEndIndex) {
            val temp = closestStartIndex
            closestStartIndex = closestEndIndex
            closestEndIndex = temp
        }

        // Extract the relevant portion of the route
        val result = mutableListOf<GeoPoint>()

        // Add the exact start point
        result.add(startPoint)

        // Add the points from the route path
        for (i in closestStartIndex + 1 until closestEndIndex) {
            result.add(routePath[i])
        }

        // Add the exact end point
        result.add(endPoint)

        return result
    }

    // Helper function to calculate the total distance of a route
    fun calculateRouteDistance(routePath: List<GeoPoint>): Double {
        var totalDistance = 0.0

        for (i in 0 until routePath.size - 1) {
            totalDistance += calculateDistance(routePath[i], routePath[i + 1])
        }

        return totalDistance * 1000 // Convert to meters
    }

    // Helper function to create matatu directions
    fun createMatatuDirections(
        routeName: String,
        startStopName: String,
        endStopName: String,
        duration: String
    ): List<String> {
        val directions = mutableListOf<String>()

        directions.add("Board matatu route $routeName at $startStopName")
        directions.add("Stay on the matatu for approximately $duration")
        directions.add("Look out for $endStopName stop")
        directions.add("Get off at $endStopName")

        return directions
    }

    // Function to fetch matatu route from nearest stop to destination stop
    fun fetchMatatuRoute(
        nearestStopResult: NearestStopResult,
        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
    ) {
        val route = nearestStopResult.route
        val nearestStop = nearestStopResult.nearbyStop
        val destinationStop = nearestStopResult.destinationStop

        Log.d("MatatuPage", "Fetching matatu route from ${nearestStop.name} to ${destinationStop.name} on route ${route.name}")

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
        
        val distanceKm = totalDistance
        val durationMin = distanceKm * 3 // Assuming average speed of 20 km/h (3 minutes per km)
        
        val formattedDistance = String.format("%.2f Km", distanceKm)
        val formattedDuration = String.format("%.0f Minutes", durationMin)
        
        // Create directions based on the stops
        val directions = mutableListOf<String>()
        
        // Add boarding instruction
        directions.add("Board matatu route ${route.name} at ${nearestStop.name}")
        
        // Add intermediate stops if available
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
                
                if (intermediateStops.isNotEmpty()) {
                    // Add major intermediate stops (limit to 3 to avoid too much information)
                    val stopCount = minOf(3, intermediateStops.size)
                    val step = intermediateStops.size / (stopCount + 1)
                    
                    for (i in 0 until stopCount) {
                        val stopIndex = (i + 1) * step
                        if (stopIndex < intermediateStops.size) {
                            directions.add("Pass through ${intermediateStops[stopIndex].name}")
                        }
                    }
                }
            }
        }
        
        // Add journey duration
        directions.add("Stay on the matatu for approximately $formattedDuration")
        
        // Add destination instruction
        directions.add("Look out for ${destinationStop.name} stop")
        directions.add("Get off at ${destinationStop.name}")
        
        onRouteReceived(matatuRoutePoints, formattedDistance, formattedDuration, directions)
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

    // Voice Input Launcher
    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spokenText.isNullOrEmpty()) {
            destinationText = spokenText
            tts!!.speak("You entered $spokenText. Finding directions now.", TextToSpeech.QUEUE_FLUSH, null, null)

            // Get location suggestions based on spoken text
            coroutineScope.launch {
                val suggestions = gtfsDataHandler.getSuggestions(spokenText)
                locationSuggestions = suggestions
                showSuggestions = suggestions.isNotEmpty()

                if (suggestions.isNotEmpty()) {
                    // Use the first suggestion automatically
                    val firstSuggestion = suggestions[0] as GtfsLocation

                    tts!!.speak("Using ${firstSuggestion.name} as your destination.", TextToSpeech.QUEUE_ADD, null, null)

                    // Check for non-null latitude and longitude before creating GeoPoint
                    val destinationPoint = if (firstSuggestion.lat != null && firstSuggestion.lon != null) {
                        GeoPoint(firstSuggestion.lat, firstSuggestion.lon)
                    } else {
                        // Handle the case where lat or lon is null, e.g., show an error message or default location
                        tts?.speak("Invalid location coordinates.", TextToSpeech.QUEUE_FLUSH, null, null)
                        null
                    }

                    // Ensure fetchWalkingRoute and fetchRoutes are in scope
                    if (destinationPoint != null) {
                        destinationLocation = destinationPoint

                        // First, try to find the nearest matatu stop that serves this destination
                        val result = matatuRouteHandler.findNearestStopToDestination(
                            userLocation!!, destinationPoint
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
                                
                                // Now fetch the matatu route from nearest stop to destination stop
                                fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                    matatuRoutePath = matatuRoutePoints
                                    matatuRouteDirections = matatuDirections
                                    matatuRouteDistance = matatuDistance
                                    matatuRouteDuration = matatuDuration
                                    
                                    // Combine walking and matatu directions for the complete journey
                                    val completeDirections = mutableListOf<String>()
                                    completeDirections.add("--- Walking to Matatu Stop ---")
                                    completeDirections.addAll(directions)
                                    completeDirections.add("--- Matatu Journey ---")
                                    completeDirections.addAll(matatuDirections)
                                    
                                    // Update the UI
                                    showDirectionsPanel = true
                                    currentDirectionIndex = 0
                                    
                                    // Announce the result to the user
                                    val announcement = "Found a matatu route to your destination. " +
                                            "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                            "${result.route.name} to ${result.destinationStop.name}. " +
                                            "The matatu journey will take approximately $matatuDuration."
                                    
                                    tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                    
                                    // After initial announcement, read the full route summary
                                    isReadingDirections = true
                                    readRouteSummary(tts!!, completeDirections)
                                }
                            }
                        }
                    } else {
                        tts!!.speak("Your location is not available. Please enable location services.",
                            TextToSpeech.QUEUE_ADD, null, null)
                    }
                } else {
                    tts!!.speak("No suggestions found for $spokenText. Please try again with a different destination.",
                        TextToSpeech.QUEUE_ADD, null, null)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        locationPermissionGranted = isGranted
        if (isGranted) {
            fetchUserLocationn(context, fusedLocationClient) { userLocation = it }
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            fetchUserLocationn(context, fusedLocationClient) { userLocation = it }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit){
                detectTapGestures(
                    onDoubleTap = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        }
                        voiceInputLauncher.launch(intent)
                    })
            }
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_back_icon),
                contentDescription = "Back",
                tint = Color.Black,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() }
            )

            Text(
                text = "Matatu Routes",
                fontSize = 22.sp,
                color = Color.Black
            )

            Text(
                text = "Cancel",
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier.clickable { navController.popBackStack() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
//
//        // Icon Row
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp)
//                .zIndex(1f),
//            horizontalArrangement = Arrangement.SpaceAround
//        ) {
//            Icon(
//                painter = painterResource(id = R.drawable.map_icon),
//                contentDescription = "Map",
//                tint = Color.Black
//            )
//            Icon(
//                painter = painterResource(id = R.drawable.history_icon),
//                contentDescription = "History",
//                tint = Color.Black
//            )
//            Icon(
//                painter = painterResource(id = R.drawable.bookmark_icon),
//                contentDescription = "Bookmarks",
//                tint = Color.Black
//            )
//        }
//
//        Spacer(modifier = Modifier.height(12.dp))

        // Show error message if any
        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                fontSize = 16.sp,
                modifier = Modifier.padding(3.dp)
                    .zIndex(1f)
            )
        }
        // OutlinedTextField with Voice Input Trigger on Double Tap
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
            placeholder = { Text("Enter destination or double tap to speak") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(1f)
                .pointerInput(Unit){
                    detectTapGestures(
                        onDoubleTap = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            }
                            voiceInputLauncher.launch(intent)
                        }
                    )
                },
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
                                        
                                        // Now fetch the matatu route from nearest stop to destination stop
                                        fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                            matatuRoutePath = matatuRoutePoints
                                            matatuRouteDirections = matatuDirections
                                            matatuRouteDistance = matatuDistance
                                            matatuRouteDuration = matatuDuration
                                            
                                            // Combine walking and matatu directions for the complete journey
                                            val completeDirections = mutableListOf<String>()
                                            completeDirections.add("--- Walking to Matatu Stop ---")
                                            completeDirections.addAll(directions)
                                            completeDirections.add("--- Matatu Journey ---")
                                            completeDirections.addAll(matatuDirections)
                                            
                                            // Update the UI
                                            showDirectionsPanel = true
                                            currentDirectionIndex = 0
                                            
                                            // Announce the result to the user
                                            val announcement = "Found a matatu route to your destination. " +
                                                    "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                                    "${result.route.name} to ${result.destinationStop.name}. " +
                                                    "The matatu journey will take approximately $matatuDuration."
                                            
                                            tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                            
                                            // After initial announcement, read the full route summary
                                            isReadingDirections = true
                                            readRouteSummary(tts!!, completeDirections)
                                        }
                                    }
                                }
                            } else {
                                errorMessage = "Invalid destination or location not available."
                            }
                        }
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text("Go")
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

                                    // If we have coordinates for this suggestion, use them directly
                                    if (selectedSuggestion.lat != null && selectedSuggestion.lon != null) {
                                        val location = GeoPoint(selectedSuggestion.lat, selectedSuggestion.lon)
                                        destinationLocation = location

                                        if (userLocation != null) {
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
                                                    
                                                    // Now fetch the matatu route from nearest stop to destination stop
                                                    fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                                        matatuRoutePath = matatuRoutePoints
                                                        matatuRouteDirections = matatuDirections
                                                        matatuRouteDistance = matatuDistance
                                                        matatuRouteDuration = matatuDuration
                                                        
                                                        // Combine walking and matatu directions for the complete journey
                                                        val completeDirections = mutableListOf<String>()
                                                        completeDirections.add("--- Walking to Matatu Stop ---")
                                                        completeDirections.addAll(directions)
                                                        completeDirections.add("--- Matatu Journey ---")
                                                        completeDirections.addAll(matatuDirections)
                                                        
                                                        // Update the UI
                                                        showDirectionsPanel = true
                                                        currentDirectionIndex = 0
                                                        
                                                        // Announce the result to the user
                                                        val announcement = "Found a matatu route to your destination. " +
                                                                "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                                                "${result.route.name} to ${result.destinationStop.name}. " +
                                                                "The matatu journey will take approximately $matatuDuration."
                                                        
                                                        tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                                        
                                                        // After initial announcement, read the full route summary
                                                        isReadingDirections = true
                                                        readRouteSummary(tts!!, completeDirections)
                                                    }
                                                }
                                            }
                                        }

                                    } else {
                                        // Otherwise, geocode the name
                                        fetchCoordinate(selectedSuggestion.name, context) { location ->
                                            if (location != null) {
                                                destinationLocation = location
                                                if (userLocation != null) {
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
                                                            
                                                            // Now fetch the matatu route from nearest stop to destination stop
                                                            fetchMatatuRoute(result) { matatuRoutePoints: List<GeoPoint>, matatuDistance: String, matatuDuration: String, matatuDirections: List<String> ->
                                                                matatuRoutePath = matatuRoutePoints
                                                                matatuRouteDirections = matatuDirections
                                                                matatuRouteDistance = matatuDistance
                                                                matatuRouteDuration = matatuDuration
                                                                
                                                                // Combine walking and matatu directions for the complete journey
                                                                val completeDirections = mutableListOf<String>()
                                                                completeDirections.add("--- Walking to Matatu Stop ---")
                                                                completeDirections.addAll(directions)
                                                                completeDirections.add("--- Matatu Journey ---")
                                                                completeDirections.addAll(matatuDirections)
                                                                
                                                                // Update the UI
                                                                showDirectionsPanel = true
                                                                currentDirectionIndex = 0
                                                                
                                                                // Announce the result to the user
                                                                val announcement = "Found a matatu route to your destination. " +
                                                                        "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                                                        "${result.route.name} to ${result.destinationStop.name}. " +
                                                                        "The matatu journey will take approximately $matatuDuration."
                                                                
                                                                tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                                                
                                                                // After initial announcement, read the full route summary
                                                                isReadingDirections = true
                                                                readRouteSummary(tts!!, completeDirections)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                errorMessage = "Could not find coordinates for ${selectedSuggestion.name}"
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .zIndex(2f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header with summary info
                    Text(
                        text = "Walking Directions",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Distance: $walkingDistance • Duration: $walkingDuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Directions list
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(walkingDirections) { index, direction ->
                            DirectionItem(
                                direction = direction,
                                isActive = index == currentDirectionIndex,
                                onClick = {
                                    currentDirectionIndex = index
                                    tts?.speak(direction, TextToSpeech.QUEUE_FLUSH, null, null)
                                }
                            )
                        }
                    }

                    // Navigation controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
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

                    // Option buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                // Read all directions from the beginning
                                isReadingDirections = true
                                currentDirectionIndex = 0
                                readRouteSummary(tts!!, walkingDirections)
                            }
                        ) {
                            Text("Read All")
                        }

                        Button(
                            onClick = {
                                // Toggle directions panel
                                showDirectionsPanel = !showDirectionsPanel
                            }
                        ) {
                            Text("Hide")
                        }
                    }
                }
            }
        }

        //mapview
        Box(modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
        ) {
            AndroidView(factory = { ctx ->
                MapView(ctx).apply {
                    Configuration.getInstance()
                        .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    controller.setZoom(18.0)
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
                                color = Color.Green.hashCode()
                                width = 5f
                                isGeodesic = true // Make sure the line follows the curvature of the earth
                            }
                            mapView.overlays.add(walkingPolyline)
                            
                            // Add small markers at turning points to make the route more visible
                            for (i in walkingRouteToStop.indices) {
                                // Add markers at start, end, and some intermediate points
                                if (i == 0 || i == walkingRouteToStop.size - 1 || i % 10 == 0) {
                                    val point = walkingRouteToStop[i]
                                    val marker = Marker(mapView).apply {
                                        position = point
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        icon = context.getDrawable(R.drawable.map_icon)?.apply {
                                            setBounds(0, 0, 20, 20) // Make the marker smaller
                                        }
                                        title = if (i == 0) "Start" else if (i == walkingRouteToStop.size - 1) "Stop" else "Turn point"
                                    }
                                    mapView.overlays.add(marker)
                                }
                            }
                            
                            // Show matatu route from nearest stop to destination stop
                            if (matatuRoutePath.isNotEmpty()) {
                                Log.d("MatatuPage", "Drawing matatu route with ${matatuRoutePath.size} points")
                                
                                val matatuPolyline = Polyline().apply {
                                    setPoints(matatuRoutePath)
                                    color = Color.Red.hashCode() // Use red for matatu routes
                                    width = 5f
                                    isGeodesic = true
                                }
                                mapView.overlays.add(matatuPolyline)
                                
                                // Add markers at key points along the matatu route
                                for (i in matatuRoutePath.indices) {
                                    // Add markers at start, end, and some intermediate points
                                    if (i == 0 || i == matatuRoutePath.size - 1 || i % 15 == 0) {
                                        val point = matatuRoutePath[i]
                                        val marker = Marker(mapView).apply {
                                            position = point
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                            icon = context.getDrawable(R.drawable.bookmark_icon)?.apply {
                                                setBounds(0, 0, 20, 20) // Make the marker smaller
                                            }
                                            title = when(i) {
                                                0 -> "Board Matatu"
                                                matatuRoutePath.size - 1 -> "Alight Matatu"
                                                else -> "Matatu Route Point"
                                            }
                                        }
                                        mapView.overlays.add(marker)
                                    }
                                }
                                
                                // Add a text overlay with journey information
                                val journeyInfoMarker = Marker(mapView).apply {
                                    // Position the info between the nearest stop and destination stop
                                    position = matatuRoutePath[matatuRoutePath.size / 2]
                                    title = nearestStopResult!!.route.name
                                    snippet = "Distance: $matatuRouteDistance, Duration: $matatuRouteDuration"
                                }
                                mapView.overlays.add(journeyInfoMarker)
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
                            width = 5f
                            isGeodesic = true // Make sure the line follows the curvature of the earth
                        }
                        mapView.overlays.add(polyline)
                        
                        // Add small markers at turning points to make the route more visible
                        for (i in routePoints.indices) {
                            // Add markers at start, end, and some intermediate points
                            if (i == 0 || i == routePoints.size - 1 || i % 10 == 0) {
                                val point = routePoints[i]
                                val marker = Marker(mapView).apply {
                                    position = point
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = context.getDrawable(R.drawable.map_icon)?.apply {
                                        setBounds(0, 0, 20, 20) // Make the marker smaller
                                    }
                                    title = if (i == 0) "Start" else if (i == routePoints.size - 1) "Destination" else "Turn point"
                                }
                                mapView.overlays.add(marker)
                            }
                        }
                    }
                    mapView.invalidate()
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchUserLocationn(context: Context, fusedLocationClient: FusedLocationProviderClient, onLocationReceived: (GeoPoint) -> Unit) {
    //val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                onLocationReceived(GeoPoint(it.latitude, it.longitude))
            }
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
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onSuggestionSelected(suggestion)
                    },
                    onTap = {
                        // Speak the suggestion when tapped once
                        tts?.speak("${suggestion.name}, $locationTypeText",
                            TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                )
            },
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
 * Read a summary of all route directions to the user
 */
fun readRouteSummary(tts: TextToSpeech, directions: List<String>) {
    // First, announce that we're going to read the route summary
    tts.speak("Here's your route summary:", TextToSpeech.QUEUE_FLUSH, null, null)

    // Add a slight pause
    tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, null)

    // Read each direction with a pause between them
    for (i in directions.indices) {
        val direction = "Step ${i + 1}: ${directions[i]}"
        tts.speak(direction, TextToSpeech.QUEUE_ADD, null, null)
        tts.playSilentUtterance(300, TextToSpeech.QUEUE_ADD, null)
    }

    // Final message
    tts.speak("End of route summary. Follow these directions to reach the matatu stop.",
        TextToSpeech.QUEUE_ADD, null, null)
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