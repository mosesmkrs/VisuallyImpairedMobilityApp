package apis

import android.content.Context
import android.util.Log
import org.osmdroid.util.GeoPoint
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handler for finding matatu routes and nearest stops
 */
class MatatuRouteHandler(
    private val context: Context,
    private val gtfsDataHandler: GtfsDataHandler
) {
    // Cache of stops and routes
    private var allStops = listOf<MatatuStop>()
    private var allRoutes = listOf<MatatuRoute>()
    
    /**
     * Initialize the matatu route handler
     */
    suspend fun initialize() {
        // Load stops and routes from GTFS data
        try {
            // Ensure GTFS data is initialized
            gtfsDataHandler.initialize()
            
            // Load stops from assets
            allStops = loadStops()
            
            // Load routes from assets
            allRoutes = loadRoutes()
            
            Log.d("MatatuRouteHandler", "Initialized with ${allStops.size} stops and ${allRoutes.size} routes")
        } catch (e: Exception) {
            Log.e("MatatuRouteHandler", "Error initializing matatu route handler: ${e.message}")
            e.printStackTrace()
            
            // Use sample data if loading fails
            allStops = getSampleStops()
            allRoutes = getSampleRoutes()
        }
    }
    
    /**
     * Find the nearest matatu stop to a given location
     */
    fun findNearestStop(location: GeoPoint): MatatuStop? {
        if (allStops.isEmpty()) {
            Log.w("MatatuRouteHandler", "No stops available to find nearest stop")
            return null
        }
        
        return allStops.minByOrNull { stop ->
            calculateDistance(
                location.latitude, location.longitude,
                stop.latitude, stop.longitude
            )
        }
    }
    
    /**
     * Find the nearest matatu stop that serves a specific destination
     */
    fun findNearestStopToDestination(
        userLocation: GeoPoint,
        destinationLocation: GeoPoint,
        maxDistance: Double = 2.0 // in kilometers
    ): NearestStopResult? {
        if (allStops.isEmpty() || allRoutes.isEmpty()) {
            Log.w("MatatuRouteHandler", "No stops or routes available")
            return null
        }
        
        // Find the destination stop (closest to the destination)
        val destinationStop = allStops.minByOrNull { stop ->
            calculateDistance(
                destinationLocation.latitude, destinationLocation.longitude,
                stop.latitude, stop.longitude
            )
        } ?: return null
        
        Log.d("MatatuRouteHandler", "Found destination stop: ${destinationStop.name}")
        
        // Find routes that serve the destination stop
        val routesToDestination = findRoutesForStop(destinationStop.id)
        if (routesToDestination.isEmpty()) {
            Log.d("MatatuRouteHandler", "No routes serve the destination stop")
            return null
        }
        
        // For each route that serves the destination, find the nearest point on the route
        val routeResults = mutableListOf<NearestStopResult>()
        
        for (route in routesToDestination) {
            // Get all stops on this route
            val stopsOnRoute = route.stops.mapNotNull { stopId ->
                allStops.find { it.id == stopId }
            }
            
            // Skip routes with no stops
            if (stopsOnRoute.isEmpty()) continue
            
            // Find the nearest stop on this route to the user
            val nearestStopOnRoute = stopsOnRoute.minByOrNull { stop ->
                calculateDistance(
                    userLocation.latitude, userLocation.longitude,
                    stop.latitude, stop.longitude
                )
            } ?: continue
            
            // Calculate distance to this stop
            val distanceToStop = calculateDistance(
                userLocation.latitude, userLocation.longitude,
                nearestStopOnRoute.latitude, nearestStopOnRoute.longitude
            )
            
            // Check if this stop is within the maximum distance
            if (distanceToStop <= maxDistance) {
                // Check if this route goes from the nearest stop to the destination stop
                val routeStopIds = route.stops
                val nearestStopIndex = routeStopIds.indexOf(nearestStopOnRoute.id)
                val destinationStopIndex = routeStopIds.indexOf(destinationStop.id)
                
                // Only consider this route if the nearest stop comes before the destination stop in the route
                if (nearestStopIndex != -1 && destinationStopIndex != -1 && 
                    ((nearestStopIndex < destinationStopIndex) || route.routePath.isNotEmpty())) {
                    
                    Log.d("MatatuRouteHandler", "Found valid route: ${route.name} from ${nearestStopOnRoute.name} to ${destinationStop.name}, distance: $distanceToStop km")
                    
                    routeResults.add(
                        NearestStopResult(
                            nearbyStop = nearestStopOnRoute,
                            destinationStop = destinationStop,
                            route = route,
                            distanceToStop = distanceToStop
                        )
                    )
                }
            }
            
            // If the route has a defined path, find the nearest point on the path
            if (route.routePath.isNotEmpty()) {
                val (nearestPoint, distanceToRoute) = findNearestPointOnRoute(userLocation, route.routePath)
                
                // If the nearest point on the route is closer than the nearest stop
                if (distanceToRoute <= maxDistance && (routeResults.isEmpty() || distanceToRoute < routeResults.minOf { it.distanceToStop })) {
                    // Find the nearest stop to this point on the route
                    val nearestStopToPoint = stopsOnRoute.minByOrNull { stop ->
                        calculateDistance(
                            nearestPoint.latitude, nearestPoint.longitude,
                            stop.latitude, stop.longitude
                        )
                    } ?: continue
                    
                    Log.d("MatatuRouteHandler", "Found closer point on route: ${route.name}, distance: $distanceToRoute km to route, nearest stop: ${nearestStopToPoint.name}")
                    
                    routeResults.add(
                        NearestStopResult(
                            nearbyStop = nearestStopToPoint,
                            destinationStop = destinationStop,
                            route = route,
                            distanceToStop = distanceToRoute // Using distance to route instead of stop
                        )
                    )
                }
            }
        }
        
        // Return the result with the shortest distance
        return routeResults.minByOrNull { it.distanceToStop }
    }
    
    /**
     * Find the nearest point on a route to the user's location
     */
    private fun findNearestPointOnRoute(userLocation: GeoPoint, routePath: List<GeoPoint>): Pair<GeoPoint, Double> {
        var nearestPoint = routePath.first()
        var minDistance = Double.MAX_VALUE
        
        // Find the nearest point on the route
        for (point in routePath) {
            val distance = calculateDistance(
                userLocation.latitude, userLocation.longitude,
                point.latitude, point.longitude
            )
            
            if (distance < minDistance) {
                minDistance = distance
                nearestPoint = point
            }
        }
        
        return Pair(nearestPoint, minDistance)
    }
    
    /**
     * Find routes that serve a specific stop
     */
    private fun findRoutesForStop(stopId: String): List<MatatuRoute> {
        // In a real implementation, this would use the GTFS data to find routes
        // For now, we'll use a simple implementation based on our sample data
        return allRoutes.filter { route ->
            route.stops.contains(stopId)
        }
    }
    
    /**
     * Check if a route serves a specific stop
     */
    private fun doesRouteServeStop(routeId: String, stopId: String): Boolean {
        val route = allRoutes.find { it.id == routeId } ?: return false
        return route.stops.contains(stopId)
    }
    
    /**
     * Get a stop by its ID
     */
    fun getStopById(stopId: String): MatatuStop? {
        return allStops.find { it.id == stopId }
    }
    
    /**
     * Calculate the distance between two points in kilometers
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val theta = lon1 - lon2
        var dist = sin(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) + 
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(theta))
        dist = acos(dist)
        dist = Math.toDegrees(dist)
        dist = dist * 60 * 1.1515 * 1.609344 // Convert to kilometers
        return dist
    }
    
    /**
     * Load stops from GTFS data
     */
    private fun loadStops(): List<MatatuStop> {
        // In a real implementation, this would parse the GTFS stops.txt file
        // For now, we'll return sample data
        return getSampleStops()
    }
    
    /**
     * Load routes from GTFS data
     */
    private fun loadRoutes(): List<MatatuRoute> {
        // In a real implementation, this would parse the GTFS routes.txt file
        // For now, we'll return sample data
        return getSampleRoutes()
    }
    
    /**
     * Get sample stops for testing
     */
    private fun getSampleStops(): List<MatatuStop> {
        return listOf(
            MatatuStop("stop1", "Nairobi Central Station", -1.2921, 36.8219),
            MatatuStop("stop2", "Westlands Terminal", -1.2673, 36.8123),
            MatatuStop("stop3", "Mombasa Road Bus Stop", -1.3182, 36.8286),
            MatatuStop("stop4", "Karen Bus Terminal", -1.3139, 36.7062),
            MatatuStop("stop5", "Thika Road Mall", -1.2192, 36.8880),
            MatatuStop("stop6", "CBD Matatu Stage", -1.2864, 36.8172),
            MatatuStop("stop7", "Ngong Road Stop", -1.3009, 36.7809),
            MatatuStop("stop8", "Eastleigh Terminal", -1.2728, 36.8502),
            MatatuStop("stop9", "Kangemi Stage", -1.2566, 36.7472),
            MatatuStop("stop10", "Rongai Terminal", -1.3967, 36.7544)
        )
    }
    
    /**
     * Get sample routes for testing
     */
    private fun getSampleRoutes(): List<MatatuRoute> {
        // Get the stops for reference to create route paths
        val stops = getSampleStops().associateBy { it.id }
        
        return listOf(
            MatatuRoute(
                id = "route1",
                name = "Route 34 - City Center to Kibera",
                stops = listOf("stop1", "stop6", "stop7", "stop4"),
                routePath = createRoutePath(listOf("stop1", "stop6", "stop7", "stop4"), stops)
            ),
            MatatuRoute(
                id = "route2",
                name = "Route 58 - Westlands to CBD",
                stops = listOf("stop2", "stop6", "stop1"),
                routePath = createRoutePath(listOf("stop2", "stop6", "stop1"), stops)
            ),
            MatatuRoute(
                id = "route3",
                name = "Route 23 - Nairobi to Mombasa Road",
                stops = listOf("stop1", "stop6", "stop3"),
                routePath = createRoutePath(listOf("stop1", "stop6", "stop3"), stops)
            ),
            MatatuRoute(
                id = "route4",
                name = "Route 45 - Thika Road to CBD",
                stops = listOf("stop5", "stop8", "stop6", "stop1"),
                routePath = createRoutePath(listOf("stop5", "stop8", "stop6", "stop1"), stops)
            ),
            MatatuRoute(
                id = "route5",
                name = "Route 111 - Rongai to CBD",
                stops = listOf("stop10", "stop4", "stop7", "stop6", "stop1"),
                routePath = createRoutePath(listOf("stop10", "stop4", "stop7", "stop6", "stop1"), stops)
            ),
            MatatuRoute(
                id = "route6",
                name = "Route 237 - Kangemi to Eastleigh",
                stops = listOf("stop9", "stop2", "stop6", "stop8"),
                routePath = createRoutePath(listOf("stop9", "stop2", "stop6", "stop8"), stops)
            )
        )
    }
    
    /**
     * Create a route path from a list of stop IDs
     */
    private fun createRoutePath(stopIds: List<String>, stopsMap: Map<String, MatatuStop>): List<GeoPoint> {
        val path = mutableListOf<GeoPoint>()
        
        // Add the stops to the path
        for (stopId in stopIds) {
            val stop = stopsMap[stopId] ?: continue
            path.add(GeoPoint(stop.latitude, stop.longitude))
            
            // If this isn't the last stop, add some intermediate points between this stop and the next
            if (stopId != stopIds.last()) {
                val nextStopId = stopIds[stopIds.indexOf(stopId) + 1]
                val nextStop = stopsMap[nextStopId] ?: continue
                
                // Add 2-3 intermediate points between stops
                val numPoints = (2..3).random()
                for (i in 1..numPoints) {
                    val ratio = i.toDouble() / (numPoints + 1)
                    val lat = stop.latitude + (nextStop.latitude - stop.latitude) * ratio
                    val lon = stop.longitude + (nextStop.longitude - stop.longitude) * ratio
                    path.add(GeoPoint(lat, lon))
                }
            }
        }
        
        return path
    }
}

/**
 * Matatu stop data class
 */
data class MatatuStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Matatu route data class
 */
data class MatatuRoute(
    val id: String,
    val name: String,
    val stops: List<String>, // List of stop IDs served by this route
    val routePath: List<GeoPoint> = emptyList() // List of points that define the route path
)

/**
 * Result class for nearest stop search
 */
data class NearestStopResult(
    val nearbyStop: MatatuStop,
    val destinationStop: MatatuStop,
    val route: MatatuRoute,
    val distanceToStop: Double // Distance in kilometers
)
