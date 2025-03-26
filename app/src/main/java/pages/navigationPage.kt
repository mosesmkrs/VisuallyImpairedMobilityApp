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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.newapp.R
import com.google.android.gms.location.*
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


    tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.speak("Welcome to Navigation Page. " +
                        "Double tap on the top of the screen to enter your destination." +
                        " Swipe right to move to the next tab. " +
                        "Swipe left to move back", TextToSpeech.QUEUE_FLUSH, null, null)
            }
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

    // Swipe Gesture for Going Back
    val interactionSource = remember { MutableInteractionSource() }
    val gestureDetector = Modifier.pointerInput(Unit) {
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
    var errorMessage by remember { mutableStateOf<String?>(null) }





    fun fetchRoute(start: GeoPoint, end: GeoPoint, onRouteReceived: (List<GeoPoint>, List<String>) -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
            .then(gestureDetector)
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
                text = "Navigate",
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
                        fetchCoordinates(destinationText, context) { location ->
                            if (location != null) {
                                destinationLocation = location
                                if (userLocation != null) {
                                    fetchRoute(userLocation!!, location) { newRoutePoints, instructions ->
                                        routePoints = newRoutePoints
                                    }
                                }
                                tts!!.speak("Destination set to $destinationText", TextToSpeech.QUEUE_FLUSH, null, null)

                            }
                        }
                    },

                    modifier = Modifier.padding(4.dp)
                        .zIndex(1f)
                ) {
                    Text("Go")
                }
            }
        )



        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
fun fetchUserLocation(context: Context, fusedLocationClient: FusedLocationProviderClient, onLocationReceived: (GeoPoint) -> Unit) {
    //val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                onLocationReceived(GeoPoint(it.latitude, it.longitude))
            }
        }
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