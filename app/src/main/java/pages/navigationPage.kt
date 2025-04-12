package pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.newapp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val context = LocalContext.current
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

    // Voice Input Launcher
    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spokenText.isNullOrEmpty()) {
            destinationText = spokenText
            tts!!.speak("You entered $spokenText", TextToSpeech.QUEUE_FLUSH, null, null)
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

    // Start tracking user location in real time
    LaunchedEffect(Unit) {
        fetchUserLocation(context, fusedLocationClient) { location ->
            userLocation = location

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
//
//    val gestureDetector = Modifier.pointerInput(Unit) {
//        detectHorizontalDragGestures { _, dragAmount ->
//            if (dragAmount > 100) { // Swipe Right: Move to the next tab
//                selectedTab = when (selectedTab) {
//                    0 -> 1 // Maps → History
//                    1 -> 2 // History → Saved Sites
//                    else -> 2 // Stay on Saved Sites (no further right navigation)
//                }
//            } else if (dragAmount < -100) { // Swipe Left: Move to the previous tab
//                selectedTab = when (selectedTab) {
//                    2 -> 1 // Saved Sites → History
//                    1 -> 0 // History → Maps
//                    else -> 0 // Stay on Maps (no further left navigation)
//                }
//            }
//
//            // Announce the switched tab
//            val tabName = when (selectedTab) {
//                0 -> "Maps"
//                1 -> "History"
//                else -> "Saved Sites"
//            }
//            tts!!.speak("Switched to $tabName", TextToSpeech.QUEUE_FLUSH, null, null)
//        }
//    }


    fun fetchRoute(
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



//    fun fetchDrivingRoute(
//        start: GeoPoint,
//        end: GeoPoint,
//        onRouteReceived: (List<GeoPoint>, String, String, List<String>) -> Unit
//    ) {
//        val apiKey = "456b9753-702c-48ca-91d4-1c21e1b015a9" // Get from https://graphhopper.com
//        val url = "https://graphhopper.com/api/1/route?point=${start.latitude},${start.longitude}&point=${end.latitude},${end.longitude}&profile=foot&points_encoded=false&key=$apiKey"
//        val client = OkHttpClient()
//        val request = Request.Builder().url(url).build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e("ROUTE_ERROR", "Failed to get route: ${e.message}")
//                errorMessage = "Error fetching route. Please try again."
//                tts!!.speak("Error fetching route. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null)
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.body?.let { responseBody ->
//                    val json = JSONObject(responseBody.string())
//                    if (!json.has("routes") || json.getJSONArray("routes").length() == 0) {
//                        errorMessage = "No route found."
//                        tts!!.speak("No route found for that destination. Please reenter your destination", TextToSpeech.QUEUE_FLUSH, null, null)
//                        Log.d("LOC_ERROR", errorMessage!!)
//                        return
//                    }
//
//                    val route = json.getJSONArray("routes").getJSONObject(0)
//                    val distanceMeters = calculateDistance(start,end)
//                    val distanceKm = (distanceMeters / 1000)
//                    val durationMin = (distanceMeters / 1000) / 5 * 60
//
//                    val formattedDistance = String.format("%.2f Km", distanceKm)
//                    val formattedDuration = String.format("%.2f Minutes", durationMin)
//
//                    // Extract route points
//                    val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
//                    val routePoints = mutableListOf<GeoPoint>()
//                    for (i in 0 until coordinates.length()) {
//                        val point = coordinates.getJSONArray(i)
//                        routePoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
//                    }
//
//                    // Extract step-by-step instructions
//                    val stepsArray = route.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
//                    val instructions = mutableListOf<String>()
//                    for (i in 0 until stepsArray.length()) {
//                        val step = stepsArray.getJSONObject(i)
//                        val modifier = step.optString("modifier", "straight")
//                        val roadName = step.optString("name", "unknown road")
//                        val maneuverType = step.getJSONObject("maneuver").optString("type", "continue")
//
//                        // Format the instruction
//                        val formattedInstruction = when (maneuverType) {
//                            "depart" -> "Start on $roadName"
//                            "turn" -> "Turn $modifier onto $roadName"
//                            "end of road" -> "At the end of the road, turn $modifier onto $roadName"
//                            "continue" -> "Continue $modifier on $roadName"
//                            "off ramp" -> "Take the exit ramp onto $roadName"
//                            "roundabout" -> "Enter the roundabout and take the exit towards $roadName"
//                            "destination" -> "You have arrived at your destination"
//                            else -> "Proceed on $roadName"
//                        }
//
//                        instructions.add(formattedInstruction)
//                    }
//
//                    onRouteReceived(routePoints, formattedDistance,
//                        formattedDuration, instructions)
//                }
//            }
//        })
//    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
           // .then(gestureDetector)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
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
//        Row(
//            verticalAlignment = Alignment.CenterVertically,
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp),
//            horizontalArrangement = Arrangement.SpaceBetween
//        ) {
//            Icon(
//                painter = painterResource(id = R.drawable.arrow_back_icon),
//                contentDescription = "Back",
//                tint = Color.Black,
//                modifier = Modifier
//                    .size(32.dp)
//                    .clickable { navController.popBackStack() }
//            )
//
//            Text(
//                text = "Navigate",
//                fontSize = 22.sp,
//                color = Color.Black
//            )
//
//            Text(
//                text = "Cancel",
//                fontSize = 18.sp,
//                color = Color.Black,
//                modifier = Modifier.clickable { navController.popBackStack() }
//            )
//        }
//
//        Spacer(modifier = Modifier.height(12.dp))
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
        OutlinedTextField(
            value = destinationText,
            onValueChange = { destinationText = it },
            placeholder = { Text("Enter destination") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(1f)
            ,
            shape = RoundedCornerShape(20.dp),
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        }
                        voiceInputLauncher.launch(intent)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.mic_icon),
                            contentDescription = "Voice Input"
                        )
                    }
                }

                Button(
                    onClick = {
                        isLoading = true
                        fetchCoordinates(destinationText, context) { location ->
                            if (location != null) {
                                destinationLocation = location
                                if (userLocation != null) {
                                    fetchRoute(userLocation!!, location) { newRoutePoints, distance, duration, instructions ->
                                        routePoints = newRoutePoints
                                        tripDistance = distance
                                        tripDuration = duration
                                        showTripDetails = true
                                        errorMessage = ""
                                        // Set up a listener for when TTS finishes speaking
                                        val onUtteranceCompletedListener = TextToSpeech.OnUtteranceCompletedListener { utteranceId ->
                                            // After TTS finishes, load the instructions
                                            tripInstructions = instructions
                                        }

                                        // Set the listener to TTS
                                        val params = HashMap<String, String>()
                                        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "intro_utterance"
                                        tts!!.setOnUtteranceCompletedListener(onUtteranceCompletedListener)

                                        // Speak the destination details
                                        tts!!.speak("Destination set to $destinationText, $distance away, Estimated Time is $duration. Follow these instructions.....", TextToSpeech.QUEUE_FLUSH, params)
//                                        tts!!.speak("Destination set to $destinationText, $distance away, Estimated Time is $duration. Follow these instructions.....", TextToSpeech.QUEUE_FLUSH, null, null)
//                                        tripInstructions = instructions

                                    }
                                }
                            }else{
                                errorMessage = "Invalid destination. Please enter a valid location."
                                tts!!.speak("Invalid destination. Please enter a valid location.", TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    }else{
                        Text("Go")
                    }

                }
                if (showTripDetails) {
                    AlertDialog(
                        onDismissRequest = { showTripDetails = false },
                        text = {
                            Column(
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "Distance: $tripDistance",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Blue
                                )

                                Text(
                                    text = "ETA: $tripDuration",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Blue
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "DIRECTIONS",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = currentInstruction,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.DarkGray
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showTripDetails = false }) {
                               Text("Close")
                            }
                        }
                    )
                }
            }
        )

        // Pop-up alert dialog
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Nearby Alerts") },
                text = {
                    Column {
                        alerts.forEach { alert ->
                            Text("⚠️ ${alert.type} - ${alert.distance.roundToInt()} meters away")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            )
        }



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

                    if (routePoints.isNotEmpty()) {
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