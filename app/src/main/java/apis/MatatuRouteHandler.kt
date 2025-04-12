package apis

import android.content.Context
import android.util.Log
import org.osmdroid.util.GeoPoint
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream

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
        Log.d("MatatuRouteHandler", "Initializing MatatuRouteHandler")
        
        // Load stops and routes from GTFS data
        try {
            // Ensure GTFS data is initialized
            gtfsDataHandler.initialize()
            
            // Load stops from assets
            allStops = loadStops()
            
            // Load routes from assets
            allRoutes = loadRoutes()
            
            Log.d("MatatuRouteHandler", "Loaded ${allStops.size} stops and ${allRoutes.size} routes")
            
            // Debug: Print first few stops and routes
            if (allStops.isNotEmpty()) {
                Log.d("MatatuRouteHandler", "First stop: ${allStops[0].id} - ${allStops[0].name}")
            }
            if (allRoutes.isNotEmpty()) {
                val firstRoute = allRoutes[0]
                Log.d("MatatuRouteHandler", "First route: ${firstRoute.id} - ${firstRoute.name} with ${firstRoute.stops.size} stops")
                if (firstRoute.stops.isNotEmpty()) {
                    Log.d("MatatuRouteHandler", "First stop on route: ${firstRoute.stops[0]}")
                }
            }
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
            Log.e("MatatuRouteHandler", "No stops available")
            return null
        }
        
        // Find the nearest stop
        val nearestStop = allStops.minByOrNull { stop ->
            calculateDistance(
                location.latitude, location.longitude,
                stop.latitude, stop.longitude
            )
        }
        
        if (nearestStop != null) {
            val distance = calculateDistance(
                location.latitude, location.longitude,
                nearestStop.latitude, nearestStop.longitude
            )
            Log.d("MatatuRouteHandler", "Found nearest stop: ${nearestStop.name} at distance $distance km")
        } else {
            Log.e("MatatuRouteHandler", "Could not find nearest stop")
        }
        
        return nearestStop
    }
    
    /**
     * Find the nearest matatu stop to the user that serves the destination
     */
    fun findNearestStopToDestination(
        userLocation: GeoPoint,
        destinationLocation: GeoPoint,
        maxDistance: Double = 3.0 // Maximum distance in kilometers
    ): NearestStopResult? {
        Log.d("MatatuRouteHandler", "Finding nearest stop to destination")
        
        // Find the nearest stop to the destination
        val destinationStop = findNearestStop(destinationLocation) ?: return null
        Log.d("MatatuRouteHandler", "Nearest stop to destination: ${destinationStop.name}")
        
        // Find routes that serve this destination stop
        val routesServingDestination = findRoutesForStop(destinationStop.id)
        Log.d("MatatuRouteHandler", "Found ${routesServingDestination.size} routes serving destination stop")
        
        // If no routes serve the destination stop, try a fallback approach
        if (routesServingDestination.isEmpty()) {
            Log.d("MatatuRouteHandler", "No routes serve the destination stop, trying fallback approach")
            
            // As a fallback, find the nearest route to the destination
            // This is useful when the GTFS data doesn't properly connect stops to routes
            val nearestRoute = findNearestRouteToLocation(destinationLocation)
            if (nearestRoute != null) {
                Log.d("MatatuRouteHandler", "Found nearest route ${nearestRoute.name} to destination")
                
                // Find the nearest stop on this route to the destination
                val nearestStopOnRoute = findNearestStopOnRoute(nearestRoute, destinationLocation)
                if (nearestStopOnRoute != null) {
                    Log.d("MatatuRouteHandler", "Found nearest stop ${nearestStopOnRoute.name} on route ${nearestRoute.name} to destination")
                    
                    // Find the nearest stop on this route to the user
                    val nearestStopToUser = findNearestStopOnRoute(nearestRoute, userLocation)
                    if (nearestStopToUser != null) {
                        Log.d("MatatuRouteHandler", "Found nearest stop ${nearestStopToUser.name} on route ${nearestRoute.name} to user")
                        
                        // Calculate distance to this stop
                        val distanceToStop = calculateDistance(
                            userLocation.latitude, userLocation.longitude,
                            nearestStopToUser.latitude, nearestStopToUser.longitude
                        )
                        
                        if (distanceToStop <= maxDistance) {
                            Log.d("MatatuRouteHandler", "Using fallback approach: route ${nearestRoute.name} from ${nearestStopToUser.name} to ${nearestStopOnRoute.name}")
                            
                            return NearestStopResult(
                                nearbyStop = nearestStopToUser,
                                destinationStop = nearestStopOnRoute,
                                route = nearestRoute,
                                distanceToStop = distanceToStop
                            )
                        }
                    }
                }
            }
            
            Log.d("MatatuRouteHandler", "Fallback approach failed, returning null")
            return null
        }
        
        // For each route, find the nearest stop to the user
        val routeResults = mutableListOf<NearestStopResult>()
        
        for (route in routesServingDestination) {
            Log.d("MatatuRouteHandler", "Checking route: ${route.name} with ${route.stops.size} stops")
            
            // Get all stops on this route
            val stopsOnRoute = route.stops.mapNotNull { stopId ->
                allStops.find { it.id == stopId }
            }
            
            // Skip routes with no stops
            if (stopsOnRoute.isEmpty()) {
                Log.d("MatatuRouteHandler", "Route ${route.name} has no stops, skipping")
                continue
            }
            
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
            
            Log.d("MatatuRouteHandler", "Nearest stop on route ${route.name} is ${nearestStopOnRoute.name} at distance ${distanceToStop} km")
            
            // Check if this stop is within the maximum distance
            if (distanceToStop <= maxDistance) {
                // Check if this route goes from the nearest stop to the destination stop
                val routeStopIds = route.stops
                val nearestStopIndex = routeStopIds.indexOf(nearestStopOnRoute.id)
                val destinationStopIndex = routeStopIds.indexOf(destinationStop.id)
                
                Log.d("MatatuRouteHandler", "Route ${route.name}: nearest stop index = $nearestStopIndex, destination stop index = $destinationStopIndex")
                
                // Consider this route valid if both stops are on the route, regardless of order
                // This allows for routes in both directions
                if (nearestStopIndex != -1 && destinationStopIndex != -1) {
                    Log.d("MatatuRouteHandler", "Found valid route: ${route.name} from ${nearestStopOnRoute.name} to ${destinationStop.name}, distance: $distanceToStop km")
                    
                    routeResults.add(
                        NearestStopResult(
                            nearbyStop = nearestStopOnRoute,
                            destinationStop = destinationStop,
                            route = route,
                            distanceToStop = distanceToStop
                        )
                    )
                } else {
                    Log.d("MatatuRouteHandler", "Route ${route.name} doesn't connect the stops properly")
                }
            } else {
                Log.d("MatatuRouteHandler", "Stop ${nearestStopOnRoute.name} is too far (${distanceToStop} km)")
                
                // FALLBACK: If the stop is just slightly beyond our threshold, still consider it
                // This helps in cases where the nearest stop is just a bit farther than our threshold
                if (distanceToStop <= maxDistance * 1.5) { // Allow up to 50% more distance as fallback
                    Log.d("MatatuRouteHandler", "Stop is within extended range, adding as fallback option")
                    
                    val routeStopIds = route.stops
                    val nearestStopIndex = routeStopIds.indexOf(nearestStopOnRoute.id)
                    val destinationStopIndex = routeStopIds.indexOf(destinationStop.id)
                    
                    if (nearestStopIndex != -1 && destinationStopIndex != -1) {
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
        val result = routeResults.minByOrNull { it.distanceToStop }
        Log.d("MatatuRouteHandler", "Final result: ${result?.route?.name ?: "No route found"}")
        return result
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
    fun findRoutesForStop(stopId: String): List<MatatuRoute> {
        Log.d("MatatuRouteHandler", "Finding routes for stop ID: $stopId")
        
        // Filter routes that contain this stop ID
        val matchingRoutes = allRoutes.filter { route ->
            route.stops.contains(stopId)
        }
        
        // If we found no routes, try a more flexible approach
        if (matchingRoutes.isEmpty()) {
            Log.d("MatatuRouteHandler", "No exact matches found, trying fuzzy matching")
            
            // Find the stop by ID
            val stop = allStops.find { it.id == stopId }
            if (stop != null) {
                // Find routes that have stops close to this one
                val nearbyRoutes = allRoutes.filter { route ->
                    // Get all stops on this route
                    val stopsOnRoute = route.stops.mapNotNull { routeStopId ->
                        allStops.find { it.id == routeStopId }
                    }
                    
                    // Check if any stop on the route is close to our target stop
                    stopsOnRoute.any { routeStop ->
                        val distance = calculateDistance(
                            stop.latitude, stop.longitude,
                            routeStop.latitude, routeStop.longitude
                        )
                        // Consider stops within 0.5 km as "the same stop"
                        distance <= 0.5
                    }
                }
                
                if (nearbyRoutes.isNotEmpty()) {
                    Log.d("MatatuRouteHandler", "Found ${nearbyRoutes.size} routes with stops near $stopId")
                    return nearbyRoutes
                }
            }
        }
        
        Log.d("MatatuRouteHandler", "Found ${matchingRoutes.size} routes for stop $stopId")
        
        // Debug: print the first few matching routes
        matchingRoutes.take(3).forEach { route ->
            Log.d("MatatuRouteHandler", "Route ${route.id} - ${route.name} serves stop $stopId")
        }
        
        return matchingRoutes
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
        val stops = mutableListOf<MatatuStop>()
        
        try {
            // Open the stops.txt file from assets
            val stopsFile = context.assets.open("stops.txt")
            val reader = BufferedReader(InputStreamReader(stopsFile))
            
            // Parse header to find column indices
            val header = reader.readLine()?.split(",") ?: return emptyList()
            val idIndex = header.indexOf("stop_id")
            val nameIndex = header.indexOf("stop_name")
            val latIndex = header.indexOf("stop_lat")
            val lonIndex = header.indexOf("stop_lon")
            
            if (idIndex == -1 || nameIndex == -1 || latIndex == -1 || lonIndex == -1) {
                Log.e("MatatuRouteHandler", "Invalid stops.txt format")
                return getSampleStops() // Fallback to sample data
            }
            
            // Parse stops
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val values = line?.split(",") ?: continue
                if (values.size <= maxOf(idIndex, nameIndex, latIndex, lonIndex)) continue
                
                try {
                    val id = values[idIndex]
                    val name = values[nameIndex]
                    val lat = values[latIndex].toDouble()
                    val lon = values[lonIndex].toDouble()
                    
                    stops.add(MatatuStop(id, name, lat, lon))
                } catch (e: Exception) {
                    Log.e("MatatuRouteHandler", "Error parsing stop: ${e.message}")
                }
            }
            
            Log.d("MatatuRouteHandler", "Loaded ${stops.size} stops from GTFS data")
            return stops
            
        } catch (e: Exception) {
            Log.e("MatatuRouteHandler", "Error loading stops: ${e.message}")
            return getSampleStops() // Fallback to sample data
        }
    }
    
    /**
     * Load routes from GTFS data
     */
    private fun loadRoutes(): List<MatatuRoute> {
        val routes = mutableListOf<MatatuRoute>()
        val routeStops = mutableMapOf<String, MutableList<String>>()
        
        try {
            // First, load the routes from routes.txt
            val routesFile = context.assets.open("routes.txt")
            val routesReader = BufferedReader(InputStreamReader(routesFile))
            
            // Parse header
            val header = routesReader.readLine()?.split(",") ?: return emptyList()
            val idIndex = header.indexOf("route_id")
            val nameIndex = header.indexOf("route_short_name")
            val descIndex = header.indexOf("route_long_name")
            
            if (idIndex == -1 || nameIndex == -1) {
                Log.e("MatatuRouteHandler", "Invalid routes.txt format")
                return getSampleRoutes() // Fallback to sample data
            }
            
            // Parse routes
            var line: String?
            while (routesReader.readLine().also { line = it } != null) {
                val values = line?.split(",") ?: continue
                if (values.size <= maxOf(idIndex, nameIndex)) continue
                
                try {
                    val id = values[idIndex]
                    val name = values[nameIndex]
                    val description = if (descIndex != -1 && values.size > descIndex) values[descIndex] else ""
                    
                    // Initialize empty list for this route's stops
                    routeStops[id] = mutableListOf()
                    
                    // Create route without stops for now
                    routes.add(MatatuRoute(id, name, description, emptyList(), emptyList()))
                } catch (e: Exception) {
                    Log.e("MatatuRouteHandler", "Error parsing route: ${e.message}")
                }
            }
            
            Log.d("MatatuRouteHandler", "Loaded ${routes.size} routes from routes.txt")
            
            // Now try to load stop_times.txt to get the stops for each route
            try {
                val stopTimesFile = context.assets.open("stop_times.txt")
                val stopTimesReader = BufferedReader(InputStreamReader(stopTimesFile))
                
                // Parse header
                val stHeader = stopTimesReader.readLine()?.split(",") ?: emptyList()
                val tripIdIndex = stHeader.indexOf("trip_id")
                val stopIdIndex = stHeader.indexOf("stop_id")
                val stopSeqIndex = stHeader.indexOf("stop_sequence")
                
                if (tripIdIndex != -1 && stopIdIndex != -1 && stopSeqIndex != -1) {
                    // Also load trips.txt to map trip_id to route_id
                    val tripToRoute = mutableMapOf<String, String>()
                    val tripsFile = context.assets.open("trips.txt")
                    val tripsReader = BufferedReader(InputStreamReader(tripsFile))
                    
                    // Parse header
                    val trHeader = tripsReader.readLine()?.split(",") ?: emptyList()
                    val trRouteIdIndex = trHeader.indexOf("route_id")
                    val trTripIdIndex = trHeader.indexOf("trip_id")
                    
                    if (trRouteIdIndex != -1 && trTripIdIndex != -1) {
                        // Parse trips
                        while (tripsReader.readLine().also { line = it } != null) {
                            val values = line?.split(",") ?: continue
                            if (values.size <= maxOf(trRouteIdIndex, trTripIdIndex)) continue
                            
                            val routeId = values[trRouteIdIndex]
                            val tripId = values[trTripIdIndex]
                            tripToRoute[tripId] = routeId
                        }
                        
                        Log.d("MatatuRouteHandler", "Loaded ${tripToRoute.size} trips from trips.txt")
                        
                        // Now parse stop_times
                        val stopTimesByTrip = mutableMapOf<String, MutableList<Pair<String, Int>>>()
                        
                        while (stopTimesReader.readLine().also { line = it } != null) {
                            val values = line?.split(",") ?: continue
                            if (values.size <= maxOf(tripIdIndex, stopIdIndex, stopSeqIndex)) continue
                            
                            val tripId = values[tripIdIndex]
                            val stopId = values[stopIdIndex]
                            val sequence = values[stopSeqIndex].toIntOrNull() ?: continue
                            
                            if (!stopTimesByTrip.containsKey(tripId)) {
                                stopTimesByTrip[tripId] = mutableListOf()
                            }
                            stopTimesByTrip[tripId]?.add(Pair(stopId, sequence))
                        }
                        
                        Log.d("MatatuRouteHandler", "Loaded stop times for ${stopTimesByTrip.size} trips")
                        
                        // Associate stops with routes
                        for ((tripId, stops) in stopTimesByTrip) {
                            val routeId = tripToRoute[tripId] ?: continue
                            
                            // Sort stops by sequence
                            val sortedStops = stops.sortedBy { it.second }.map { it.first }
                            
                            // Add to route's stops
                            routeStops[routeId]?.addAll(sortedStops)
                        }
                    } else {
                        Log.e("MatatuRouteHandler", "Invalid trips.txt format: route_id or trip_id column not found")
                    }
                } else {
                    Log.e("MatatuRouteHandler", "Invalid stop_times.txt format: trip_id, stop_id, or stop_sequence column not found")
                }
            } catch (e: Exception) {
                Log.e("MatatuRouteHandler", "Error loading stop_times: ${e.message}")
                // Continue with routes that don't have stops
            }
            
            // Update routes with their stops
            var routesWithStops = 0
            var totalStops = 0
            
            for (i in routes.indices) {
                val route = routes[i]
                val stops = routeStops[route.id] ?: emptyList()
                
                // Remove duplicates while preserving order
                val uniqueStops = stops.toSet().toList()
                totalStops += uniqueStops.size
                
                if (uniqueStops.isNotEmpty()) {
                    routesWithStops++
                }
                
                routes[i] = MatatuRoute(route.id, route.name, route.description, uniqueStops, emptyList())
            }
            
            Log.d("MatatuRouteHandler", "Updated routes with stops: $routesWithStops routes have stops, $totalStops total stops")
            
            // If we have no routes with stops, use sample data as a fallback
            if (routesWithStops == 0) {
                Log.w("MatatuRouteHandler", "No routes have stops! Using sample data as fallback")
                return getSampleRoutes()
            }
            
            // Debug: print a few routes with their stops
            routes.filter { it.stops.isNotEmpty() }.take(3).forEach { route ->
                Log.d("MatatuRouteHandler", "Route ${route.id} - ${route.name} has ${route.stops.size} stops: ${route.stops.take(5)}")
            }
            
            return routes
            
        } catch (e: Exception) {
            Log.e("MatatuRouteHandler", "Error loading routes: ${e.message}")
            return getSampleRoutes() // Fallback to sample data
        }
    }
    
    /**
     * Create a route path from a list of stop IDs
     */
    private fun createRoutePath(stopIds: List<String>, stops: Map<String, MatatuStop>): List<GeoPoint> {
        val path = mutableListOf<GeoPoint>()
        
        // First, try to use shape data if available
        val routeShapes = getShapeForRoute(stopIds.first())
        if (routeShapes.isNotEmpty()) {
            Log.d("MatatuRouteHandler", "Using shape data for route path with ${routeShapes.size} points")
            return routeShapes
        }
        
        // Fallback: Create a path by connecting stops
        for (stopId in stopIds) {
            val stop = stops[stopId] ?: continue
            path.add(GeoPoint(stop.latitude, stop.longitude))
        }
        
        return path
    }
    
    /**
     * Get shape data for a route from GTFS shapes.txt
     */
    private fun getShapeForRoute(routeId: String): List<GeoPoint> {
        try {
            // Try to find shape data in shapes.txt
            val shapesFile = context.assets.open("shapes.txt")
            val reader = BufferedReader(InputStreamReader(shapesFile))
            
            // Parse header to find column indices
            val header = reader.readLine()?.split(",") ?: return emptyList()
            val shapeIdIndex = header.indexOf("shape_id")
            val latIndex = header.indexOf("shape_pt_lat")
            val lonIndex = header.indexOf("shape_pt_lon")
            val sequenceIndex = header.indexOf("shape_pt_sequence")
            
            if (shapeIdIndex == -1 || latIndex == -1 || lonIndex == -1 || sequenceIndex == -1) {
                Log.e("MatatuRouteHandler", "Invalid shapes.txt format")
                return emptyList()
            }
            
            // Find the shape ID for this route (in trips.txt)
            val shapeId = getShapeIdForRoute(routeId)
            if (shapeId.isEmpty()) {
                Log.d("MatatuRouteHandler", "No shape ID found for route $routeId")
                return emptyList()
            }
            
            // Parse shape points
            val shapePoints = mutableListOf<Pair<GeoPoint, Int>>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val values = line?.split(",") ?: continue
                if (values.size <= maxOf(shapeIdIndex, latIndex, lonIndex, sequenceIndex)) continue
                
                if (values[shapeIdIndex] == shapeId) {
                    try {
                        val lat = values[latIndex].toDouble()
                        val lon = values[lonIndex].toDouble()
                        val sequence = values[sequenceIndex].toInt()
                        shapePoints.add(Pair(GeoPoint(lat, lon), sequence))
                    } catch (e: Exception) {
                        Log.e("MatatuRouteHandler", "Error parsing shape point: ${e.message}")
                    }
                }
            }
            
            // Sort by sequence and return points
            return shapePoints.sortedBy { it.second }.map { it.first }
            
        } catch (e: Exception) {
            Log.e("MatatuRouteHandler", "Error loading shape data: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Get shape ID for a route from trips.txt
     */
    private fun getShapeIdForRoute(routeId: String): String {
        try {
            val tripsFile = context.assets.open("trips.txt")
            val reader = BufferedReader(InputStreamReader(tripsFile))
            
            // Parse header
            val header = reader.readLine()?.split(",") ?: return ""
            val routeIdIndex = header.indexOf("route_id")
            val shapeIdIndex = header.indexOf("shape_id")
            
            if (routeIdIndex == -1 || shapeIdIndex == -1) {
                Log.e("MatatuRouteHandler", "Invalid trips.txt format")
                return ""
            }
            
            // Find first trip for this route
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val values = line?.split(",") ?: continue
                if (values.size <= maxOf(routeIdIndex, shapeIdIndex)) continue
                
                if (values[routeIdIndex] == routeId) {
                    return values[shapeIdIndex]
                }
            }
            
            return ""
        } catch (e: Exception) {
            Log.e("MatatuRouteHandler", "Error finding shape ID: ${e.message}")
            return ""
        }
    }
    
    /**
     * Get sample stops for testing
     */
    private fun getSampleStops(): List<MatatuStop> {
        return listOf(
            MatatuStop(
                id = "stop1",
                name = "Nairobi Central Station",
                latitude = -1.2921,
                longitude = 36.8219
            ),
            MatatuStop(
                id = "stop2",
                name = "Westlands Terminal",
                latitude = -1.2673,
                longitude = 36.8123
            ),
            MatatuStop(
                id = "stop3",
                name = "Ngong Road Station",
                latitude = -1.3001,
                longitude = 36.7792
            ),
            MatatuStop(
                id = "stop4",
                name = "Kawangware Terminal",
                latitude = -1.2878,
                longitude = 36.7428
            ),
            MatatuStop(
                id = "stop5",
                name = "Kibera Station",
                latitude = -1.3131,
                longitude = 36.7807
            ),
            MatatuStop(
                id = "stop6",
                name = "Eastleigh Terminal",
                latitude = -1.2697,
                longitude = 36.8503
            )
        )
    }
    
    /**
     * Get sample routes for testing
     */
    private fun getSampleRoutes(): List<MatatuRoute> {
        return listOf(
            MatatuRoute(
                id = "route1",
                name = "Route 1",
                description = "Nairobi Central - Westlands",
                stops = listOf("stop1", "stop2"),
                routePath = listOf(
                    GeoPoint(-1.2921, 36.8219), // Central
                    GeoPoint(-1.2800, 36.8150), // Intermediate point
                    GeoPoint(-1.2673, 36.8123)  // Westlands
                )
            ),
            MatatuRoute(
                id = "route2",
                name = "Route 2",
                description = "Nairobi Central - Ngong Road - Kawangware",
                stops = listOf("stop1", "stop3", "stop4"),
                routePath = listOf(
                    GeoPoint(-1.2921, 36.8219), // Central
                    GeoPoint(-1.3001, 36.7792), // Ngong Road
                    GeoPoint(-1.2878, 36.7428)  // Kawangware
                )
            ),
            MatatuRoute(
                id = "route3",
                name = "Route 3",
                description = "Nairobi Central - Kibera",
                stops = listOf("stop1", "stop5"),
                routePath = listOf(
                    GeoPoint(-1.2921, 36.8219), // Central
                    GeoPoint(-1.3131, 36.7807)  // Kibera
                )
            ),
            MatatuRoute(
                id = "route4",
                name = "Route 4",
                description = "Nairobi Central - Eastleigh",
                stops = listOf("stop1", "stop6"),
                routePath = listOf(
                    GeoPoint(-1.2921, 36.8219), // Central
                    GeoPoint(-1.2697, 36.8503)  // Eastleigh
                )
            )
        )
    }
    
    /**
     * Find the nearest route to a location
     */
    private fun findNearestRouteToLocation(location: GeoPoint): MatatuRoute? {
        // Calculate the distance from the location to each route
        var nearestRoute: MatatuRoute? = null
        var minDistance = Double.MAX_VALUE
        
        for (route in allRoutes) {
            // Skip routes with no stops
            if (route.stops.isEmpty()) continue
            
            // Get all stops on this route
            val stopsOnRoute = route.stops.mapNotNull { stopId ->
                allStops.find { it.id == stopId }
            }
            
            if (stopsOnRoute.isEmpty()) continue
            
            // Find the nearest stop on this route to the location
            val nearestStop = stopsOnRoute.minByOrNull { stop ->
                calculateDistance(
                    location.latitude, location.longitude,
                    stop.latitude, stop.longitude
                )
            } ?: continue
            
            // Calculate distance to this stop
            val distance = calculateDistance(
                location.latitude, location.longitude,
                nearestStop.latitude, nearestStop.longitude
            )
            
            if (distance < minDistance) {
                minDistance = distance
                nearestRoute = route
            }
        }
        
        return nearestRoute
    }
    
    /**
     * Find the nearest stop on a route to a location
     */
    private fun findNearestStopOnRoute(route: MatatuRoute, location: GeoPoint): MatatuStop? {
        // Get all stops on this route
        val stopsOnRoute = route.stops.mapNotNull { stopId ->
            allStops.find { it.id == stopId }
        }
        
        if (stopsOnRoute.isEmpty()) return null
        
        // Find the nearest stop on this route to the location
        return stopsOnRoute.minByOrNull { stop ->
            calculateDistance(
                location.latitude, location.longitude,
                stop.latitude, stop.longitude
            )
        }
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
    val description: String,
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
