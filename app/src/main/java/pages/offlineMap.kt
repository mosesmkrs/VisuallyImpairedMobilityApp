@file:OptIn(ExperimentalMaterial3Api::class)

package pages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.toolkit.geoviewcompose.MapView



fun createMap(): ArcGISMap {

    return ArcGISMap(BasemapStyle.ArcGISTopographic).apply {

        initialViewpoint = Viewpoint(
            latitude = -1.286389,
            longitude = 36.817223,
            scale = 5000.0
        )


    }

}

@Composable
fun OfflineMap(navController: NavController) {

    val map = remember {
        createMap()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "Nairobi city offline map") }) }
    ) {

        MapView(
            modifier = Modifier.fillMaxSize().padding(it),
            arcGISMap = map
        )

    }

}

