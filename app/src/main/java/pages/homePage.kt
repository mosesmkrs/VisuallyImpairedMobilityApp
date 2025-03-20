package pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

import android.content.Context
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import components.Footer
import APIs.GoogleAuthClient
import com.example.newapp.R
import com.example.newapp.Routes

@Composable
fun HomeScreen(googleAuthClient: GoogleAuthClient,
               lifecycleOwner: LifecycleOwner,
               navController: NavController) {

    val userPhoto by remember { mutableStateOf(googleAuthClient.getUserPhotoUrl()) }
    val context = LocalContext.current
    var isGpsEnabled by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isGpsEnabled = checkGpsStatus(context)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            isGpsEnabled = checkGpsStatus(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            //.verticalScroll(scrollState)

    ) {
        // Top Bar with Home Title and Profile Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Home",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.Center
            )
            if (userPhoto != null) {
                AsyncImage(
                    model = userPhoto,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { navController.navigate(Routes.profilePage) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.person_icon),
                    contentDescription = "Default Profile Picture",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { navController.navigate(Routes.profilePage) },
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SOS Emergency Button
        Button(
            onClick = { navController.navigate(Routes.ContactFormScreen) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.emergency_icon),
                contentDescription = "Alert Icon",
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "SOS EMERGENCY", color = Color.White, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Options
        NavigationOptionsGrid(navController)

        Spacer(modifier = Modifier.height(16.dp))

        // Current Status and Alerts
        StatusAndAlertsUI(isGpsEnabled)

        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)
    }
}

fun checkGpsStatus(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}

@Composable
fun NavigationOptionsGrid(navController: NavController) {
    val options = listOf(
        Triple("Start Navigation", R.drawable.navigate_icon, Routes.navigationPage),
//        Triple("Public Transport", R.drawable.bus_icon, Routes.profilePage),
//        Triple("Object Recognition", R.drawable.recog_icon, Routes.navigationPage),
        Triple("Offline Maps", R.drawable.map_icon, Routes.offlineMapPage)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        items(options) { (label, iconRes, path) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable { navController.navigate(path) }
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
@Composable
fun StatusAndAlertsUI(isGpsEnabled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .padding(horizontal = 20.dp)
    ) {
        // Current Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)) // Changed to requested color
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Status",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.wifi_icon),
                        contentDescription = "GPS Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGpsEnabled) "Connected to GPS" else "GPS is OFF",
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.audio_icon),
                        contentDescription = "Audio Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Guidance Active", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.smartphone_icon),
                        contentDescription = "Haptic Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Haptic Feedback On", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nearby Alerts Section
        Text(
            text = "Nearby Alerts",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Alert Cards
        val alerts = listOf(
            Pair("Crosswalk ahead - 20m", R.drawable.cross_icon),
            Pair("Construction work - 50m", R.drawable.construction_icon)
        )

        alerts.forEach { (text, iconRes) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
