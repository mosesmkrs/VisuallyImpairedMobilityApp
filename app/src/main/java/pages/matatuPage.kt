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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
                tts?.speak("You are on the Matatu Page. " +
                        "Double tap on the top of the screen to enter your destination." +
                        "Swipe right to move to the next tab. " +
                        "Swipe left to move back", TextToSpeech.QUEUE_FLUSH, null, null)
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
    // Voice Input Launcher
    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spokenText.isNullOrEmpty()) {
            destinationText = spokenText
            tts!!.speak("You entered $spokenText", TextToSpeech.QUEUE_FLUSH, null, null)
            
            // Get location suggestions based on spoken text
            coroutineScope.launch {
                val suggestions = gtfsDataHandler.getSuggestions(spokenText)
                locationSuggestions = suggestions
                showSuggestions = suggestions.isNotEmpty()
                
                // Announce the number of suggestions found
                if (suggestions.isNotEmpty()) {
                    tts!!.speak("Found ${suggestions.size} suggestions. Double tap on a suggestion to select it.", 
                        TextToSpeech.QUEUE_ADD, null, null)
                } else {
                    tts!!.speak("No suggestions found for $spokenText", 
                        TextToSpeech.QUEUE_ADD, null, null)
                }
            }
        }
    }
    val gestureDetector = Modifier
        .pointerInput(Unit) {
        detectHorizontalDragGestures { _, dragAmount ->
            if (dragAmount > 100) { // Swipe Right: Move to the next tab
                selectedTab = when (selectedTab) {
                    0 -> 1 // Maps → History
                    1 -> 2 // History → Saved Sites
                    else -> 2 // Stay on Saved Sites (no further right navigation)
                }
            } else if (dragAmount < -100) { // Swipe Left: Move to the previous tab
                selectedTab = when (selectedTab) {
                    2 -> 1 // Saved Sites → History
                    1 -> 0 // History → Maps
                    else -> 0 // Stay on Maps (no further left navigation)
                }
            }

            // Announce the switched tab
            val tabName = when (selectedTab) {
                0 -> "Maps"
                1 -> "History"
                else -> "Saved Sites"
            }
            tts!!.speak("Switched to $tabName", TextToSpeech.QUEUE_FLUSH, null, null)
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

    fun fetchRoutes(start: GeoPoint, end: GeoPoint, onRouteReceived: (List<GeoPoint>, List<String>) -> Unit) {
        val url = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ROUTE_ERROR", "Failed to get route: ${e.message}")
                errorMessage = "Error fetching route. Please try again."
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    val json = JSONObject(responseBody.string())
                    if (!json.has("routes") || json.getJSONArray("routes").length() == 0) {
                        errorMessage = "No route found. Please check the destination."
                        return
                    }

                    val route = json.getJSONArray("routes").getJSONObject(0)
                    val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                    val steps = route.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")

                    if (coordinates.length() < 2) {
                        errorMessage = "Route too short or invalid."
                        return
                    }

                    val routePoints = mutableListOf<GeoPoint>()
                    for (i in 0 until coordinates.length()) {
                        val point = coordinates.getJSONArray(i)
                        routePoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
                    }

                    val instructions = mutableListOf<String>()
                    for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        val instruction = step.getString("maneuver") + ": " + step.getString("name")
                        instructions.add(instruction)
                    }

                    errorMessage = null // Clear any previous errors
                    onRouteReceived(routePoints, instructions)
                } ?: run {
                    errorMessage = "Invalid response from server."
                }
            }
        })
    }

    fun fetchWalkingRoute(
        start: GeoPoint,
        end: GeoPoint,
        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
    ) {
        val apiKey = "456b9753-702c-48ca-91d4-1c21e1b015a9"
        val url = "https://graphhopper.com/api/1/route?point=${start.latitude},${start.longitude}&point=${end.latitude},${end.longitude}&profile=foot&points_encoded=false&key=$apiKey"

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

        // Icon Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .zIndex(1f),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(
                painter = painterResource(id = R.drawable.map_icon),
                contentDescription = "Map",
                tint = Color.Black
            )
            Icon(
                painter = painterResource(id = R.drawable.history_icon),
                contentDescription = "History",
                tint = Color.Black
            )
            Icon(
                painter = painterResource(id = R.drawable.bookmark_icon),
                contentDescription = "Bookmarks",
                tint = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                                    fetchWalkingRoute(userLocation!!, nearestStopLocation) { newRoutePoints, distance, duration, directions ->
                                        walkingRouteToStop = newRoutePoints
                                        walkingDirections = directions
                                        walkingDistance = distance
                                        walkingDuration = duration
                                        showDirectionsPanel = true
                                        currentDirectionIndex = 0
                                        
                                        // Announce the result to the user
                                        val announcement = "Found a matatu route to your destination. " +
                                            "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                            "${result.route.name} to ${result.destinationStop.name}."
                                        
                                        tts?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                        
                                        // After initial announcement, read the full route summary
                                        isReadingDirections = true
                                        readRouteSummary(tts!!, directions)
                                    }
                                } else {
                                    // If no matatu route is found, just show direct route
                                    showingDirectionsToStop = false
                                    nearestStopResult = null
                                    walkingRouteToStop = emptyList()
                                    fetchRoutes(userLocation!!, location) { newRoutePoints, instructions ->
                                        routePoints = newRoutePoints
                                        tts?.speak("Showing direct route to destination. No matatu routes found.", 
                                            TextToSpeech.QUEUE_FLUSH, null, null)
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
                                                fetchWalkingRoute(userLocation!!, nearestStopLocation) { newRoutePoints, distance, duration, directions ->
                                                    walkingRouteToStop = newRoutePoints
                                                    walkingDirections = directions
                                                    walkingDistance = distance
                                                    walkingDuration = duration
                                                    showDirectionsPanel = true
                                                    currentDirectionIndex = 0
                                                    
                                                    // Announce the result to the user
                                                    val announcement = "Found a matatu route to your destination. " +
                                                        "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                                        "${result.route.name} to ${result.destinationStop.name}."
                                                    
                                                    tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                                    
                                                    // After initial announcement, read the full route summary
                                                    isReadingDirections = true
                                                    readRouteSummary(tts!!, directions)
                                                }
                                            } else {
                                                // If no matatu route is found, just show direct route
                                                showingDirectionsToStop = false
                                                nearestStopResult = null
                                                walkingRouteToStop = emptyList()
                                                fetchRoutes(userLocation!!, location) { newRoutePoints, instructions ->
                                                    routePoints = newRoutePoints
                                                    tts!!.speak("Showing direct route to destination. No matatu routes found.", 
                                                        TextToSpeech.QUEUE_FLUSH, null, null)
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
                                                        fetchWalkingRoute(userLocation!!, nearestStopLocation) { newRoutePoints, distance, duration, directions ->
                                                            walkingRouteToStop = newRoutePoints
                                                            walkingDirections = directions
                                                            walkingDistance = distance
                                                            walkingDuration = duration
                                                            showDirectionsPanel = true
                                                            currentDirectionIndex = 0
                                                            
                                                            // Announce the result to the user
                                                            val announcement = "Found a matatu route to your destination. " +
                                                                "Walk $distance (about $duration) to ${result.nearbyStop.name} and take " +
                                                                "${result.route.name} to ${result.destinationStop.name}."
                                                            
                                                            tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
                                                            
                                                            // After initial announcement, read the full route summary
                                                            isReadingDirections = true
                                                            readRouteSummary(tts!!, directions)
                                                        }
                                                    } else {
                                                        // If no matatu route is found, just show direct route
                                                        showingDirectionsToStop = false
                                                        nearestStopResult = null
                                                        walkingRouteToStop = emptyList()
                                                        fetchRoutes(userLocation!!, location) { newRoutePoints, instructions ->
                                                            routePoints = newRoutePoints
                                                            tts!!.speak("Showing direct route to destination. No matatu routes found.", 
                                                                TextToSpeech.QUEUE_FLUSH, null, null)
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
                            val walkingPolyline = Polyline().apply {
                                setPoints(walkingRouteToStop)
                                color = Color.Green.hashCode()
                                width = 5f
                            }
                            mapView.overlays.add(walkingPolyline)
                        } else {
                            Log.d("MatatuPage", "No walking route points available")
                        }
                        
                        // Show matatu route using the actual route path
                        val route = nearestStopResult!!.route
                        Log.d("MatatuPage", "Route: ${route.name}, Stops: ${route.stops.size}, Path points: ${route.routePath.size}")
                        
                        // Create a separate polyline for the matatu route
                        val matatuRoutePoints = mutableListOf<GeoPoint>()
                        
                        // Always include the nearest stop and destination stop
                        matatuRoutePoints.add(nearestStopLocation)
                        
                        // Check if we have a route path
                        if (route.routePath.isNotEmpty()) {
                            Log.d("MatatuPage", "Using route path with ${route.routePath.size} points")
                            
                            // Get the indices of stops in the route
                            val routeStopIds = route.stops
                            val nearestStopIndex = routeStopIds.indexOf(nearestStop.id)
                            val destStopIndex = routeStopIds.indexOf(destStop.id)
                            
                            Log.d("MatatuPage", "Stop indices: nearest=${nearestStopIndex}, dest=${destStopIndex}")
                            
                            // If both stops are on the route
                            if (nearestStopIndex != -1 && destStopIndex != -1) {
                                // Add all stops between the nearest and destination stops
                                val stopsBetween = if (nearestStopIndex < destStopIndex) {
                                    routeStopIds.subList(nearestStopIndex + 1, destStopIndex)
                                } else {
                                    routeStopIds.subList(destStopIndex + 1, nearestStopIndex).reversed()
                                }
                                
                                Log.d("MatatuPage", "Stops between: ${stopsBetween.size}")
                                
                                // Add intermediate stops to the route
                                if (stopsBetween.isEmpty()) {
                                    Log.d("MatatuPage", "No intermediate stops, creating a realistic route")
                                    // If there are no intermediate stops, create a more realistic route
                                    // Clear the list and start fresh (keeping only the nearest stop)
                                    val start = matatuRoutePoints.first() // Save the nearest stop
                                    matatuRoutePoints.clear()
                                    matatuRoutePoints.add(start) // Add back the nearest stop
                                    
                                    // Create a realistic path with multiple points
                                    createRealisticRoutePath(matatuRoutePoints, nearestStopLocation, destStopLocation)
                                } else {
                                    Log.d("MatatuPage", "Adding ${stopsBetween.size} intermediate stops")
                                    // Add intermediate stops to the route
                                    for (stopId in stopsBetween) {
                                        // Get the stop from the matatuRouteHandler
                                        val stop = matatuRouteHandler.getStopById(stopId)
                                        if (stop != null) {
                                            matatuRoutePoints.add(GeoPoint(stop.latitude, stop.longitude))
                                            Log.d("MatatuPage", "Added intermediate stop: ${stop.name}")
                                        }
                                    }
                                    
                                    // Add intermediate points between stops for a smoother route
                                    val smoothedRoutePoints = mutableListOf<GeoPoint>()
                                    for (i in 0 until matatuRoutePoints.size - 1) {
                                        val start = matatuRoutePoints[i]
                                        val end = matatuRoutePoints[i + 1]
                                        
                                        smoothedRoutePoints.add(start)
                                        
                                        // Add points between each stop
                                        addIntermediatePointsBetweenStops(smoothedRoutePoints, start, end)
                                    }
                                    
                                    // Add the final point
                                    smoothedRoutePoints.add(matatuRoutePoints.last())
                                    matatuRoutePoints.clear()
                                    matatuRoutePoints.addAll(smoothedRoutePoints)
                                }
                                
                                Log.d("MatatuPage", "Created smoothed route with ${matatuRoutePoints.size} points")
                            } else {
                                Log.d("MatatuPage", "One or both stops not found on route, using fallback")
                                // Fallback: create a realistic route path
                                createRealisticRoutePath(matatuRoutePoints, nearestStopLocation, destStopLocation)
                            }
                        } else {
                            Log.d("MatatuPage", "No route path available, using fallback")
                            // Fallback: create a realistic route path
                            createRealisticRoutePath(matatuRoutePoints, nearestStopLocation, destStopLocation)
                        }
                        
                        // First add the destination stop
                        matatuRoutePoints.add(destStopLocation)
                        
                        // Then add a walking route from the destination stop to the actual destination
                        if (destinationLocation != null && !destStopLocation.equals(destinationLocation)) {
                            Log.d("MatatuPage", "Adding walking path from destination stop to actual destination")
                            // Create a walking path from the destination stop to the final destination
                            addIntermediatePointsBetweenStops(matatuRoutePoints, destStopLocation,
                                destinationLocation!!
                            )
                            // Add the actual destination
                            matatuRoutePoints.add(destinationLocation!!)
                        }
                        
                        Log.d("MatatuPage", "Final matatu route has ${matatuRoutePoints.size} points")
                        
                        // Split the route into matatu route and final walking segment if needed
                        if (destinationLocation != null && !destStopLocation.equals(destinationLocation)) {
                            // Find the index where the destination stop is in the route points
                            val destStopIndex = matatuRoutePoints.indexOfFirst { it.equals(destStopLocation) }
                            
                            if (destStopIndex != -1 && destStopIndex < matatuRoutePoints.size - 1) {
                                // Create the matatu route polyline (up to the destination stop)
                                val matatuSegmentPoints = matatuRoutePoints.subList(0, destStopIndex + 1)
                                val matatuPolyline = Polyline().apply {
                                    setPoints(matatuSegmentPoints)
                                    color = Color.Red.hashCode()
                                    width = 5f
                                }
                                mapView.overlays.add(matatuPolyline)
                                
                                // Create the final walking segment polyline (from destination stop to actual destination)
                                val walkingSegmentPoints = matatuRoutePoints.subList(destStopIndex, matatuRoutePoints.size)
                                val finalWalkingPolyline = Polyline().apply {
                                    setPoints(walkingSegmentPoints)
                                    color = Color.Blue.hashCode() // Use blue for the final walking segment
                                    width = 5f
                                }
                                mapView.overlays.add(finalWalkingPolyline)
                                
                                Log.d("MatatuPage", "Created split route: ${matatuSegmentPoints.size} points for matatu, ${walkingSegmentPoints.size} points for final walking")
                            } else {
                                // Fallback to showing the whole route in one color
                                val matatuPolyline = Polyline().apply {
                                    setPoints(matatuRoutePoints)
                                    color = Color.Red.hashCode()
                                    width = 5f
                                }
                                mapView.overlays.add(matatuPolyline)
                            }
                        } else {
                            // Just show the matatu route
                            val matatuPolyline = Polyline().apply {
                                setPoints(matatuRoutePoints)
                                color = Color.Red.hashCode()
                                width = 5f
                            }
                            mapView.overlays.add(matatuPolyline)
                        }
                        
                        // Add route information text to the map
                        val routeInfoMarker = Marker(mapView).apply {
                            position = GeoPoint(
                                (nearestStopLocation.latitude + destStopLocation.latitude) / 2,
                                (nearestStopLocation.longitude + destStopLocation.longitude) / 2
                            )
                            title = nearestStopResult!!.route.name
                            snippet = "From ${nearestStop.name} to ${destStop.name}"
                        }
                        mapView.overlays.add(routeInfoMarker)
                    } 
                    // Show direct route if not showing matatu directions
                    else if (routePoints.isNotEmpty()) {
                        val polyline = Polyline().apply {
                            setPoints(routePoints)
                            color = Color.Blue.hashCode()
                            width = 5f
                        }
                        mapView.overlays.add(polyline)
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
//
//fun fetchCoordinate(destination: String, context: Context, onResult: (GeoPoint?) -> Unit) {
//    val geocoder = android.location.Geocoder(context)
//    val addresses = geocoder.getFromLocationName(destination, 1)
//    if (addresses?.isNotEmpty() == true) {
//        val address = addresses[0]
//        onResult(GeoPoint(address.latitude, address.longitude))
//    } else {
//        onResult(null)
//    }
//}

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

/**
 * Read a summary of all route directions to the user
 */
/**
 * Create a realistic route path between two points
 */
fun createRealisticRoutePath(routePoints: MutableList<GeoPoint>, start: GeoPoint, end: GeoPoint) {
    Log.d("MatatuPage", "Creating realistic route path from ${start.latitude},${start.longitude} to ${end.latitude},${end.longitude}")
    
    // Calculate the direct distance between start and end
    val directDistance = calculateDistances(start, end)
    
    // Create a more complex path with multiple segments
    // First, create 2-3 major waypoints to make the route non-linear
    val numWaypoints = 2
    val waypoints = mutableListOf<GeoPoint>()
    
    for (i in 1..numWaypoints) {
        // Create waypoints that deviate from the direct path
        val ratio = i.toFloat() / (numWaypoints + 1)
        val lat = start.latitude + (end.latitude - start.latitude) * ratio
        val lon = start.longitude + (end.longitude - start.longitude) * ratio
        
        // Add significant deviation to create a realistic route
        // The deviation is proportional to the direct distance
        val deviationFactor = directDistance * 0.2 // 20% of the direct distance
        val latOffset = (Math.random() * deviationFactor - deviationFactor/2) / 111.0 // Convert km to degrees (approx)
        val lonOffset = (Math.random() * deviationFactor - deviationFactor/2) / (111.0 * Math.cos(Math.toRadians(lat)))
        
        waypoints.add(GeoPoint(lat + latOffset, lon + lonOffset))
        Log.d("MatatuPage", "Added major waypoint at ${lat + latOffset},${lon + lonOffset}")
    }
    
    // Now create segments between start, waypoints, and end
    val allPoints = mutableListOf<GeoPoint>().apply {
        add(start)
        addAll(waypoints)
        add(end)
    }
    
    // For each segment, add intermediate points
    for (i in 0 until allPoints.size - 1) {
        val segmentStart = allPoints[i]
        val segmentEnd = allPoints[i + 1]
        
        if (i > 0) { // Don't add the start point again for segments after the first
            routePoints.add(segmentStart)
        }
        
        // Add intermediate points for this segment
        addIntermediatePointsBetweenStops(routePoints, segmentStart, segmentEnd)
    }
    
    // Add the final destination point
    routePoints.add(end)
    
    Log.d("MatatuPage", "Created realistic route with ${routePoints.size} points")
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
