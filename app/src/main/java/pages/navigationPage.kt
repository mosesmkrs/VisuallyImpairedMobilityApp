package pages

// Add these imports at the top
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.newapp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import components.Footer
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
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


data class Alert(
    val id: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float
)

@Composable
fun NavigationPage(navController: NavController) {
    // Add these state variables at the top of NavigationPage composable
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var destinationText by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var selectedTab by remember { mutableStateOf(0) } //0: Maps , 1:History, 2:Bookmarked
    var showTripDetails by remember { mutableStateOf(false) }
    var tripDistance by remember { mutableStateOf("") }
    var tripDuration by remember { mutableStateOf("") }
    var tripInstructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentInstruction by remember { mutableStateOf("Loading directions...") }
    var isLoading by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(listOf<Alert>()) }
    var showDialog by remember { mutableStateOf(false) }
    val gtfsDataHandler = remember { GtfsDataHandler(context) }
    var locationSuggestions by remember { mutableStateOf<List<GtfsLocation>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var routeAlerts by remember { mutableStateOf<List<ORSAlert>>(emptyList()) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var nextStepDistance by remember { mutableStateOf<String?>(null) }
    var lastAnnouncedStep by remember { mutableStateOf(-1) }
    var showDirectionsPanel by remember { mutableStateOf(false) }
    var currentDirectionIndex by remember { mutableStateOf(0) }

    // Add these at the top of the NavigationPage composable
    val repeatingAnnouncementScope = rememberCoroutineScope()
    var routeFindingJob: Job? by remember { mutableStateOf(null) }

    // Function to update navigation - move inside NavigationPage
    fun updateNavigation(currentLocation: GeoPoint) {
        if (!isNavigating || routePoints.isEmpty() || tripInstructions.isEmpty()) return

        // Find the closest point on the route
        var minDistance = Double.MAX_VALUE
        var closestPointIndex = 0

        routePoints.forEachIndexed { index, point ->
            val distance = calculateDistance(currentLocation, point)
            if (distance < minDistance) {
                minDistance = distance
                closestPointIndex = index
            }
        }

        // Update current step based on progress
        val newStepIndex = (closestPointIndex * tripInstructions.size / routePoints.size)
            .coerceIn(0, tripInstructions.size - 1)

        if (newStepIndex != currentStepIndex) {
            currentStepIndex = newStepIndex

            // Announce new step if we haven't announced it yet
            if (currentStepIndex > lastAnnouncedStep) {
                val currentInstruction = tripInstructions[currentStepIndex]
                tts?.speak(currentInstruction, TextToSpeech.QUEUE_FLUSH, null, null)
                lastAnnouncedStep = currentStepIndex
            }
        }

        // Calculate distance to next step
        if (currentStepIndex < tripInstructions.size - 1) {
            val nextStepPoint = routePoints[((currentStepIndex + 1) * routePoints.size / tripInstructions.size)
                .coerceIn(0, routePoints.size - 1)]
            val distanceToNext = calculateDistance(currentLocation, nextStepPoint)
            nextStepDistance = formatDistance(distanceToNext)
        }

        // Check if we've reached the destination
        if (minDistance < 20 && currentStepIndex >= tripInstructions.size - 1) {
            tts?.speak("You have reached your destination", TextToSpeech.QUEUE_FLUSH, null, null)
            isNavigating = false
            currentStepIndex = 0
            lastAnnouncedStep = -1
            nextStepDistance = null
        }
    }

    tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak("You are on the navigation Page"
                    , TextToSpeech.QUEUE_FLUSH, null, null)
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

    // Request Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        locationPermissionGranted = isGranted
        if (isGranted) {
            fetchUserLocation(context, fusedLocationClient) { userLocation = it }
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            fetchUserLocation(context, fusedLocationClient) { userLocation = it }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Update location tracking
    LaunchedEffect(isNavigating) {
        fetchUserLocation(context, fusedLocationClient) { location ->
            userLocation = location

            if (isNavigating) {
                updateNavigation(location)
            }

            if (routePoints.isNotEmpty() && tripInstructions.isNotEmpty()) {
                val newInstruction = getClosestInstruction(location, routePoints, tripInstructions)
                if (newInstruction != currentInstruction) {
                    currentInstruction = newInstruction
                    tts?.speak(currentInstruction, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }

            location.let { loc ->
                fetchOverpassData(loc.latitude, loc.longitude) { newAlerts ->
                    alerts = newAlerts
                    Log.d("ALERTS_DATA", alerts.toString())
                }
            }
        }
    }

    // Initialize GTFS data
    LaunchedEffect(Unit) {
        try {
            gtfsDataHandler.initialize()
        } catch (e: Exception) {
            Log.e("NavigationPage", "Error initializing GTFS data: ${e.message}")
        }
    }

    fun fetchRouteORS(
        start: GeoPoint,
        end: GeoPoint,
        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
    ) {
        // Start the repeating announcement
        val routeFindingJob = repeatingAnnouncementScope.launch {
            try {
                // Initial announcement
                Log.d("NavigationPage", "Starting repeating announcement for route finding")
                tts?.speak(
                    "Finding route to $destinationText, please wait",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
                
                // Repeat every 6 seconds until cancelled
                while (isActive) {
                    kotlinx.coroutines.delay(6000) // Wait 6 seconds
                    Log.d("NavigationPage", "Repeating announcement for route finding")
                    tts?.speak(
                        "Finding route to $destinationText, please wait",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e("NavigationPage", "Error in repeating announcement: ${e.message}")
            }
        }

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
                routeFindingJob.cancel()
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

                            // Cancel the repeating announcement
                            routeFindingJob.cancel()

                            // Set up a listener for when TTS finishes speaking
                            val onUtteranceCompletedListener = TextToSpeech.OnUtteranceCompletedListener { utteranceId ->
                                when (utteranceId) {
                                    "route_found_utterance" -> {
                                        // After route found announcement, update the UI and read first instruction
                                        onRouteReceived(routePoints, formattedDistance, formattedDuration, instructions)
                                        
                                        // Read the first instruction after a short delay
                                        tts?.playSilentUtterance(1000, TextToSpeech.QUEUE_ADD, null)
                                        if (instructions.isNotEmpty()) {
                                            tts?.speak(
                                                instructions[0],
                                                TextToSpeech.QUEUE_ADD,
                                                null,
                                                null
                                            )
                                        }
                                    }
                                }
                            }

                            // Set the listener to TTS
                            val params = HashMap<String, String>()
                            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "route_found_utterance"
                            tts?.setOnUtteranceCompletedListener(onUtteranceCompletedListener)

                            // Make the final announcement before calling the callback
                            tts?.speak(
                                "Route to $destinationText is found. Follow these instructions.",
                                TextToSpeech.QUEUE_FLUSH,
                                params
                            )

                            checkRouteAlerts(routePoints) { alerts ->
                                if (alerts.isNotEmpty()) {
                                    val alertMessage = alerts.joinToString("\n") { it.description }
                                    tts?.speak(
                                        "Route alerts: $alertMessage",
                                        TextToSpeech.QUEUE_ADD,
                                        null,
                                        null
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ROUTE_ERROR", "Error parsing response: ${e.message}")
                        routeFindingJob.cancel()
                    }
                }
            }
        })
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
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
                text = "Navigation Page",
                fontSize = 22.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Show error message if any
        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                fontSize = 16.sp,
                modifier = Modifier.padding(3.dp)
                    //.zIndex(1f)
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
                        Log.d("NavigationPage", "Got ${suggestions.size} suggestions for '$newText'}")
                        locationSuggestions = suggestions
                        //Always show suggestions if text is not empty and at least 2 chars
                        showSuggestions = newText.length >= 2
                        Log.d("NavigationPage", "Show suggestions set to $showSuggestions")
                    } catch (e: Exception) {
                        Log.e("NavigationPage", "Error getting suggestions: ${e.message}")
                        e.printStackTrace()
                    }
                }
            },
            placeholder = { Text("Enter destination") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(1f),
            shape = RoundedCornerShape(20.dp),
            trailingIcon = {
                Button(
                    onClick = {
                        isLoading = true
                        fetchCoordinates(destinationText, context) { location ->
                            if (location != null) {
                                destinationLocation = location
                                if (userLocation != null) {
                                    fetchRouteORS(userLocation!!, location) { newRoutePoints, distance, duration, instructions ->
                                        routePoints = newRoutePoints
                                        tripDistance = distance
                                        tripDuration = duration
                                        tripInstructions = instructions
                                        showTripDetails = true
                                        errorMessage = null
                                        isLoading = false

                                        //Update the UI
                                        showDirectionsPanel = true
                                        currentDirectionIndex = 0
                                        //Announce the Result
                                        val announcement = "Found Route to your destination" +
                                                "Distance: $distance, Duration: $duration"
                                        tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)

                                        //Only announce we found a route and read first direction
                                        readRouteAnnouncement(tts!!)
                                        currentDirectionIndex = 0
                                    }
                                }
                            } else {
                                errorMessage = "Location not found"
                                isLoading = false

                            }
                        }
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text("Start Navigation")
                    }
                }
            }
        )
        //Debug text to show suggestion state
        Text(
            text ="Suggestions:${if (showSuggestions) "Visible" else "Hidden"} (${locationSuggestions.size} items)",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Suggestions dropdown
        if (showSuggestions) {
            Log.d("NavigationPage", "Rendering suggestions dropdown with ${locationSuggestions.size} items")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 200.dp)
                    .zIndex(2f),
                shape = RoundedCornerShape(10.dp)
            ){
                if (locationSuggestions.isEmpty()){
                    // Show a message when no suggestions are available
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching locations found")
                    }
                }else{
                    LazyColumn{
                        items(locationSuggestions){suggestion ->
                            SuggestionItem(
                                suggestion = suggestion,
                                onSuggestionSelected = {
                                    destinationText = suggestion.name
                                    showSuggestions = false

                                    // Provide feedback that we're processing the selection
                                    tts?.speak("Finding route to ${suggestion.name}, please wait", TextToSpeech.QUEUE_FLUSH, null, null)
                                    errorMessage = null // Clear any previous error messages

                                    // If we have coordinates for this suggestion, use them directly
                                    if (suggestion.lat != null && suggestion.lon != null) {
                                        val location = GeoPoint(suggestion.lat, suggestion.lon)
                                        destinationLocation = location

                                        Log.d("Navigation page", "Selected location: ${suggestion.name} at ${suggestion.lat}, ${suggestion.lon}")

                                        if (userLocation != null) {
                                            Log.d("Navigation page", "User location is available: $userLocation")

                                            // Show loading indicator or message
                                            errorMessage = "Finding route to ${suggestion.name}, please wait..."

                                            isLoading = true
                                            fetchRouteORS(userLocation!!, location) { newRoutePoints, distance, duration, instructions ->
                                                routePoints = newRoutePoints
                                                tripDistance = distance
                                                tripDuration = duration
                                                tripInstructions = instructions
                                                showTripDetails = true
                                                errorMessage = null
                                                isLoading = false

                                                // Update the UI
                                                showDirectionsPanel = true
                                                currentDirectionIndex = 0

                                                // Announce the result to the user
                                                val announcement = "Found a walking route to your destination. " +
                                                        "Distance is $distance, estimated time is $duration"

                                                tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)

                                                // Only announce that we found a route, then read the first direction
                                                readRouteAnnouncement(tts!!, suggestion.name)
                                                currentDirectionIndex = 0
                                            }
                                        } else {
                                            Log.e("NavigationPage", "User location is not available")
                                            errorMessage = "Your location is not available. Please enable location services."
                                            tts?.speak("Your location is not available. Please enable location services.",
                                                TextToSpeech.QUEUE_FLUSH, null, null)
                                        }
                                    } else {
                                        // Otherwise, geocode the name
                                        fetchCoordinates(suggestion.name, context) { location ->
                                            if (location != null) {
                                                destinationLocation = location
                                                if (userLocation != null) {
                                                    isLoading = true
                                                    fetchRouteORS(userLocation!!, location) { newRoutePoints, distance, duration, instructions ->
                                                        routePoints = newRoutePoints
                                                        tripDistance = distance
                                                        tripDuration = duration
                                                        tripInstructions = instructions
                                                        showTripDetails = true
                                                        errorMessage = null
                                                        isLoading = false

                                                        // Update the UI
                                                        showDirectionsPanel = true
                                                        currentDirectionIndex = 0

                                                        // Announce the result to the user
                                                        val announcement = "Found a walking route to your destination. " +
                                                                "Distance is $distance, estimated time is $duration"

                                                        tts!!.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)

                                                        // Only announce that we found a route, then read the first direction
                                                        readRouteAnnouncement(tts!!, suggestion.name)
                                                        currentDirectionIndex = 0
                                                    }
                                                }
                                            } else {
                                                errorMessage = "Could not find coordinates for ${suggestion.name}"
                                                tts?.speak("Could not find coordinates for ${suggestion.name}. Please try another destination.",
                                                    TextToSpeech.QUEUE_FLUSH, null, null)
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
        if (showDirectionsPanel && tripInstructions.isNotEmpty()) {
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
                        text = "Distance: $tripDistance • Duration: $tripDuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Only show the current direction
                    if (currentDirectionIndex >= 0 && currentDirectionIndex < tripInstructions.size) {
                        val currentDirection = tripInstructions[currentDirectionIndex]

                        // Current step indicator
                        Text(
                            text = "Step ${currentDirectionIndex + 1} of ${tripInstructions.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Current direction
                        DirectionItem(
                            direction = currentDirection,
                            isActive = true,
                            onClick = {
                                //Read current direction
                                tts?.speak(currentDirection, TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        )
                        // Read the direction automatically when it changes
                        LaunchedEffect(currentDirectionIndex) {
                            tts?.speak(currentDirection, TextToSpeech.QUEUE_FLUSH, null, null)
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
                                // Hide directions panel
                                showDirectionsPanel = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text("Hide")
                        }
                    }
                }
            }
        }else if (!showDirectionsPanel && tripInstructions.isNotEmpty()) {
            // Show Instructions button when panel is hidden
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .zIndex(2f),
                contentAlignment = Alignment.TopEnd
            ) {
                Button(
                    onClick = {
                        showDirectionsPanel = true
                    }
                ) {
                    Text("Show Instructions")
                }
            }
        }




        // Map Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = 300.dp, height = 500.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
        ) {
            // Map View
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        Configuration.getInstance()
                            .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                        controller.setZoom(18.0)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        setTilesScaledToDpi(true)
                        setUseDataConnection(false) // Use offline tiles if available
                    }
                },
                update = { mapView ->
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

                        if (routePoints.isNotEmpty()) {
                            val polyline = Polyline().apply {
                                setPoints(routePoints)
                                color = Color.Blue.hashCode()
                                width = 12f
                                isGeodesic = true
                                outlinePaint.strokeWidth = 12f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 30f), 0f)
                            }
                            mapView.overlays.add(polyline)
                        }
                        mapView.invalidate()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showTripDetails) {
                            Modifier.height(300.dp)
                        } else {
                            Modifier.fillMaxHeight()
                        }
                    )
            )

        }

        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)

        if (showSuggestions && locationSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-8).dp)
                    .zIndex(3f),
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .background(Color.White)
                ) {
                    items(locationSuggestions) { suggestion ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        destinationText = suggestion.name
                                        showSuggestions = false

                                        // If we have coordinates for this suggestion, use them directly
                                        if (suggestion.lat != null && suggestion.lon != null) {
                                            val location = GeoPoint(suggestion.lat, suggestion.lon)
                                            destinationLocation = location

                                            if (userLocation != null) {
                                                isLoading = true
                                                fetchRouteORS(userLocation!!, location) { newRoutePoints, distance, duration, instructions ->
                                                    routePoints = newRoutePoints
                                                    tripDistance = distance
                                                    tripDuration = duration
                                                    tripInstructions = instructions
                                                    showTripDetails = true
                                                    errorMessage = null
                                                    isLoading = false

                                                    // Announce route details via TTS
                                                    tts?.speak(
                                                        "Route found to ${suggestion.name}. Distance is $distance, estimated time is $duration",
                                                        TextToSpeech.QUEUE_FLUSH,
                                                        null,
                                                        null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.location),
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (locationSuggestions.last() != suggestion) {
                                Divider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add the alert dialog
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text("Route Alerts") },
                text = {
                    Column {
                        routeAlerts.forEach { alert ->
                            Text(
                                "⚠️ ${alert.description}",
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Divider()
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showAlertDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}


@SuppressLint("MissingPermission")
fun fetchUserLocation(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdated: (GeoPoint) -> Unit
) {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000) // Update every 2 seconds
        .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location: Location? = locationResult.lastLocation
            location?.let {
                onLocationUpdated(GeoPoint(it.latitude, it.longitude))
            }
        }
    }

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }
}


fun fetchCoordinates(destination: String, context: Context, onResult: (GeoPoint?) -> Unit) {
    val geocoder = Geocoder(context)
    val addresses = geocoder.getFromLocationName(destination, 1)
    if (addresses?.isNotEmpty() == true) {
        val address = addresses[0]
        onResult(GeoPoint(address.latitude, address.longitude))
    } else {
        onResult(null)
    }
}

fun getClosestInstruction(userLocation: GeoPoint, routePoints: List<GeoPoint>, instructions: List<String>): String {
    var closestIndex = 0
    var minDistance = Double.MAX_VALUE

    for (i in routePoints.indices) {
        val point = routePoints[i]
        val distance = calculateDistance(userLocation, point)

        if (distance < minDistance) {
            minDistance = distance
            closestIndex = i
        }
    }

    return if (closestIndex < instructions.size) {
        instructions[closestIndex]
    } else {
        "You have arrived at your destination"
    }
}

fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
    val R = 6371e3 // Earth radius in meters
    val lat1 = Math.toRadians(point1.latitude)
    val lon1 = Math.toRadians(point1.longitude)
    val lat2 = Math.toRadians(point2.latitude)
    val lon2 = Math.toRadians(point2.longitude)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return R * c // Distance in meters
}


//ALERTS

fun fetchOverpassData(latitude: Double, longitude: Double, onResult: (List<Alert>) -> Unit) {
    val overpassQuery = """
        [out:json];
        (
            way["highway"="construction"](around:5000,$latitude,$longitude);
            way["highway"="crossing"](around:5000,$latitude,$longitude);
            way["highway"="motorway"](around:5000,$latitude,$longitude);
            way["highway"="primary"](around:5000,$latitude,$longitude);
            way["highway"="roundabout"](around:5000,$latitude,$longitude);
            way["highway"="secondary"](around:5000,$latitude,$longitude);
            way["highway"="tertiary"](around:5000,$latitude,$longitude);
            way["highway"="residential"](around:5000,$latitude,$longitude);
            way["highway"="trunk"](around:5000,$latitude,$longitude);
        );
        out body;
    """.trimIndent()

    val overpassUrl = "https://overpass-api.de/api/interpreter?data=$overpassQuery"

    try {
        val url = URL(overpassUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = InputStreamReader(connection.inputStream)
            val response = reader.readText()
            val jsonResponse = JSONObject(response)
            val newAlerts = parseOverpassResponse(jsonResponse, latitude, longitude)
            onResult(newAlerts)
        } else {
            // Handle HTTP error
        }
    } catch (e: Exception) {
        // Handle network errors
    }
}


fun parseOverpassResponse(response: JSONObject, userLatitude: Double, userLongitude: Double): List<Alert> {
    val elements = response.getJSONArray("elements")
    val newAlerts = mutableListOf<Alert>()

    for (i in 0 until elements.length()) {
        val element = elements.getJSONObject(i)
        val tags = element.optJSONObject("tags") ?: continue

        if (element.has("lat") && element.has("lon")) {
            val latitude = element.getDouble("lat")
            val longitude = element.getDouble("lon")

            // Check if the element has a "highway" tag
            if (tags.has("highway")) {
                val type = tags.getString("highway")

                val validTypes = listOf(
                    "construction", "crossing", "motorway", "primary",
                    "roundabout", "secondary", "tertiary", "residential", "trunk"
                )

                if (type in validTypes) {
                    val distance = calculateDistances(userLatitude, userLongitude, latitude, longitude)

                    val alert = Alert(
                        id = element.optString("id", ""),
                        type = type.replaceFirstChar { it.uppercase() },  // Capitalize first letter
                        latitude = latitude,
                        longitude = longitude,
                        distance = distance
                    )

                    newAlerts.add(alert)
                }
            }
        }
    }

    return newAlerts.sortedBy { it.distance }
}

// Calculate the distance between the user's location and the alert location
fun calculateDistances(userLat: Double, userLon: Double, alertLat: Double, alertLon: Double): Float {
    val userLocation = Location("user").apply {
        latitude = userLat
        longitude = userLon
    }

    val alertLocation = Location("alert").apply {
        latitude = alertLat
        longitude = alertLon
    }

    return userLocation.distanceTo(alertLocation)
}

// Add new data class for ORS alerts
data class ORSAlert(
    val type: String,
    val location: GeoPoint,
    val distance: Double,
    val description: String
)

// Function to check for alerts along the route
fun checkRouteAlerts(
    routePoints: List<GeoPoint>,
    onAlertsFound: (List<ORSAlert>) -> Unit
) {
    val alerts = mutableListOf<ORSAlert>()

    // Check for steep inclines
    for (i in 0 until routePoints.size - 1) {
        val point1 = routePoints[i]
        val point2 = routePoints[i + 1]

        // Calculate distance and elevation change
        val distance = calculateDistance(point1, point2)
        if (distance > 50) { // Alert for segments longer than 50m
            alerts.add(ORSAlert(
                type = "Long Segment",
                location = point1,
                distance = distance,
                description = "Long walking segment ahead (${String.format("%.0f", distance)}m)"
            ))
        }
    }

    // Add crossing points alerts
    routePoints.forEachIndexed { index, point ->
        if (index > 0 && index < routePoints.size - 1) {
            val prevPoint = routePoints[index - 1]
            val nextPoint = routePoints[index + 1]

            // Check for sharp turns (potential crossings)
            val angle = calculateAngle(prevPoint, point, nextPoint)
            if (angle > 60) {
                alerts.add(ORSAlert(
                    type = "Crossing",
                    location = point,
                    distance = 0.0,
                    description = "Potential crossing point ahead"
                ))
            }
        }
    }

    onAlertsFound(alerts)
}

// Helper function to calculate angle between three points
fun calculateAngle(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint): Double {
    val angle1 = Math.atan2(p1.latitude - p2.latitude, p1.longitude - p2.longitude)
    val angle2 = Math.atan2(p3.latitude - p2.latitude, p3.longitude - p2.longitude)
    var angle = Math.toDegrees(Math.abs(angle1 - angle2))
    if (angle > 180) angle = 360 - angle
    return angle
}

fun formatDistance(distanceInMeters: Double): String {
    return when {
        distanceInMeters < 1000 -> "${distanceInMeters.roundToInt()} m"
        else -> String.format("%.2f km", distanceInMeters / 1000)
    }
}

fun formatDuration(durationInMinutes: Double): String {
    return when {
        durationInMinutes < 60 -> "${durationInMinutes.roundToInt()} minutes"
        else -> {
            val hours = (durationInMinutes / 60).toInt()
            val minutes = (durationInMinutes % 60).roundToInt()
            if (minutes > 0) {
                "$hours hours $minutes minutes"
            } else {
                "$hours hours"
            }
        }
    }
}

fun calculateDistanceToNextStep(userLocation: GeoPoint, stepPoint: GeoPoint): Double {
    return calculateDistance(userLocation, stepPoint)
}
