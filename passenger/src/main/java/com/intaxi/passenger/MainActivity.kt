package com.intaxi.passenger

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var root: FrameLayout
    private lateinit var pickup: EditText
    private lateinit var destination: EditText
    private lateinit var standard: Button
    private lateinit var comfort: Button
    private lateinit var estimate: TextView
    private lateinit var eta: TextView
    private lateinit var pinHint: TextView
    private lateinit var pinConfirm: Button
    private var map: MapLibreMap? = null
    private var routeLine: Polyline? = null
    private var routeRequestId = 0
    private var selectedTariff = "სტანდარტი"
    private var pickupPoint: LatLng? = null
    private var destinationPoint: LatLng? = null
    private var pinMode: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pickupSearchRunnable: Runnable? = null
    private var destinationSearchRunnable: Runnable? = null
    private var suppressTextSearch = false

    private val bgColor = Color.rgb(18, 18, 21)
    private val panel = Color.rgb(31, 31, 36)
    private val burgundy = Color.rgb(100, 29, 45)
    private val white = Color.WHITE
    private val muted = Color.rgb(175, 175, 182)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        mapView.getMapAsync { m ->
            map = m
            m.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                m.cameraPosition = CameraPosition.Builder().target(LatLng(41.7151, 44.8271)).zoom(12.0).build()
                m.addOnMapClickListener { point ->
                    if (pinMode != null) {
                        val mode = pinMode!!
                        if (mode == "A") pickupPoint = point else destinationPoint = point
                        clearRoute("Yeni nöqtə seçildi. Təsdiqlədikdən sonra marşrut yenilənəcək.")
                        pinHint.text = if (mode == "A") "A nöqtəsi seçildi — götürülmə yeri" else "B nöqtəsi seçildi — gediləcək yer"
                        reverseGeocode(point, mode)
                        true
                    } else false
                }
            }
        }
        buildPassengerScreen()
        setContentView(root)
        requestLocationPermission()
    }

    private fun buildPassengerScreen() {
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(12))
            background = rounded(bgColor, 20)
            elevation = dp(10).toFloat()
        }
        topPanel.addView(TextView(this).apply {
            text = "İNTAXİ"; textSize = 22f; setTextColor(white)
            setTypeface(null, Typeface.BOLD); setPadding(dp(2), 0, 0, dp(9))
        })
        topPanel.addView(addressRow("საიდან?  ჩასხდომის ადგილი", true),
            LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })
        topPanel.addView(addressRow("სად მიდიხართ?  დანიშნულება", false),
            LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(10), dp(28), dp(10), 0)
        })

        val locate = TextView(this).apply {
            text = "⌖"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(white)
            background = rounded(panel, 28); elevation = dp(7).toFloat()
            setOnClickListener { currentLocation() }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.END or Gravity.TOP).apply {
            setMargins(0, dp(205), dp(12), 0)
        })

        pinHint = TextView(this).apply {
            text = "რუკაზე მონიშნეთ სასურველი წერტილი"; textSize = 12f
            setTextColor(white); gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.argb(235, 25, 25, 30), 10); visibility = View.GONE
        }
        root.addView(pinHint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(225))
        })
        pinConfirm = Button(this).apply {
            text = "წერტილის დადასტურება"; isAllCaps = false; setTextColor(white)
            background = rounded(burgundy, 12); visibility = View.GONE
            setOnClickListener {
                pinMode = null; pinHint.visibility = View.GONE; visibility = View.GONE
                updateRoute()
            }
        }
        root.addView(pinConfirm, FrameLayout.LayoutParams(-1, dp(44), Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(174))
        })

        val orderPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(bgColor, 20); elevation = dp(12).toFloat()
        }
        orderPanel.addView(TextView(this).apply {
            text = "აირჩიეთ ტარიფი"; textSize = 12f; setTextColor(muted)
            setPadding(dp(2), 0, 0, dp(6))
        })
        val tariffs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        standard = tariffButton("სტანდარტი", true)
        comfort = tariffButton("კომფორტი", false)
        tariffs.addView(standard, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        tariffs.addView(comfort, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        orderPanel.addView(tariffs)
        estimate = TextView(this).apply {
            text = "მიუთითეთ ჩასხდომისა და დანიშნულების მისამართები"
            textSize = 13f; setTextColor(white); setPadding(dp(3), dp(10), dp(3), 0)
        }
        eta = TextView(this).apply {
            text = "მარშრუტისა და ფასის გამოთვლა ავტომატურად მოხდება"
            textSize = 11f; setTextColor(muted); setPadding(dp(3), dp(4), dp(3), dp(8))
        }
        orderPanel.addView(estimate); orderPanel.addView(eta)
        val callTaxi = Button(this).apply {
            text = "შეკვეთის მიცემა"; isAllCaps = false; textSize = 16f
            setTypeface(null, Typeface.BOLD); setTextColor(white)
            background = rounded(burgundy, 13); setOnClickListener { requestTaxi() }
        }
        orderPanel.addView(callTaxi, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(orderPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(10))
        })
    }

    private fun addressRow(hintText: String, isPickup: Boolean): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val field = addressField(hintText)
        if (isPickup) pickup = field else destination = field
        row.addView(field, LinearLayout.LayoutParams(0, -1, 1f))
        val button = Button(this).apply {
            text = "რუკაზე"; isAllCaps = false; textSize = 11f; setTextColor(white)
            background = rounded(Color.rgb(65, 65, 73), 10)
            setPadding(dp(5), 0, dp(5), 0)
            setOnClickListener {
                pinMode = if (isPickup) "A" else "B"
                pinHint.text = if (isPickup) "რუკაზე მონიშნეთ A — ჩასხდომის ადგილი" else "რუკაზე მონიშნეთ B — დანიშნულების ადგილი"
                pinHint.visibility = View.VISIBLE
                pinConfirm.visibility = View.VISIBLE
                map?.animateCamera(CameraUpdateFactory.zoomTo(14.0))
            }
        }
        row.addView(button, LinearLayout.LayoutParams(dp(76), -1).apply { marginStart = dp(6) })
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressTextSearch) return
                val query = s?.toString()?.trim().orEmpty()
                if (isPickup) pickupPoint = null else destinationPoint = null
                clearRoute("A და B მისამართები შეიცვალა. აირჩიეთ ორივე წერტილი თავიდან.")
                val previous = if (isPickup) pickupSearchRunnable else destinationSearchRunnable
                previous?.let { handler.removeCallbacks(it) }
                if (query.length < 3) return
                val task = Runnable {
                    if (field.text.toString().trim() == query) searchAddress(query, isPickup)
                }
                if (isPickup) pickupSearchRunnable = task else destinationSearchRunnable = task
                handler.postDelayed(task, 1100)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        return row
    }

    private fun addressField(hintText: String) = EditText(this).apply {
        hint = hintText; textSize = 12f; setTextColor(white); setHintTextColor(muted)
        isSingleLine = true; setPadding(dp(11), 0, dp(8), 0)
        background = rounded(Color.rgb(45, 45, 51), 10)
    }

    private fun tariffButton(label: String, selected: Boolean) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f; setTextColor(white)
        background = rounded(if (selected) burgundy else Color.rgb(48, 48, 54), 10)
        setOnClickListener {
            selectedTariff = label
            standard.background = rounded(if (label == "სტანდარტი") burgundy else Color.rgb(48, 48, 54), 10)
            comfort.background = rounded(if (label == "კომფორტი") burgundy else Color.rgb(48, 48, 54), 10)
            updateRoute()
        }
    }

    private fun searchAddress(query: String, isPickup: Boolean) {
        thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&addressdetails=1&countrycodes=ge&limit=10&viewbox=40.0,43.8,46.8,41.0&bounded=1")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 12000
                    setRequestProperty("User-Agent", "INTAXI-Android/1.0 (Georgia taxi app)")
                    setRequestProperty("Accept-Language", "ka,en")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = org.json.JSONArray(response)
                val here = lastKnownLocation()
                val found = mutableListOf<Pair<String, LatLng>>()
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite() || lat !in 41.0..43.8 || lon !in 40.0..46.8) continue
                    val address = item.optJSONObject("address")
                    val street = listOfNotNull(
                        address?.optString("road")?.takeIf { it.isNotBlank() },
                        address?.optString("house_number")?.takeIf { it.isNotBlank() }
                    ).joinToString(" ")
                    val town = listOf("city", "town", "village", "municipality", "suburb")
                        .mapNotNull { key -> address?.optString(key)?.takeIf { it.isNotBlank() } }
                        .distinct().firstOrNull().orEmpty()
                    val label = listOf(street, town).filter { it.isNotBlank() }.joinToString(", ")
                        .ifBlank { item.optString("display_name").split(",").take(3).joinToString(", ") }
                    found.add(label to LatLng(lat, lon))
                }
                val sorted = found.distinctBy { "%.5f,%.5f".format(Locale.US, it.second.latitude, it.second.longitude) }
                    .sortedBy { pair ->
                        if (here == null) 0f else Location("search").apply {
                            latitude = pair.second.latitude; longitude = pair.second.longitude
                        }.let { here.distanceTo(it) }
                    }.take(7)
                if (sorted.isNotEmpty()) runOnUiThread {
                    val currentText = if (isPickup) pickup.text.toString() else destination.text.toString()
                    if (currentText.trim() != query) return@runOnUiThread
                    val labels = sorted.map { it.first }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("აირჩიეთ მისამართი საქართველოში")
                        .setItems(labels) { _, which ->
                            val point = sorted[which].second
                            suppressTextSearch = true
                            if (isPickup) {
                                pickupPoint = point; pickup.setText(labels[which]); pickup.setSelection(pickup.text.length)
                            } else {
                                destinationPoint = point; destination.setText(labels[which]); destination.setSelection(destination.text.length)
                            }
                            suppressTextSearch = false
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
                            updateRoute()
                        }.setNegativeButton("გაუქმება", null).show()
                } else runOnUiThread {
                    val currentText = if (isPickup) pickup.text.toString() else destination.text.toString()
                    if (currentText.trim() == query) AlertDialog.Builder(this)
                        .setMessage("საქართველოში შესაბამისი მისამართი ვერ მოიძებნა. სცადეთ ქუჩის სახელი და ქალაქი.")
                        .setPositiveButton("კარგი", null).show()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    val currentText = if (isPickup) pickup.text.toString() else destination.text.toString()
                    if (currentText.trim() == query) AlertDialog.Builder(this)
                        .setMessage("მისამართის ძებნა ვერ მოხერხდა. შეამოწმეთ ინტერნეტი და სცადეთ თავიდან.")
                        .setPositiveButton("კარგი", null).show()
                }
            }
        }
    }

    private fun lastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (_: SecurityException) { null }
    }

    private fun reverseGeocode(point: LatLng, mode: String) {
        thread {
            try {
                val results = Geocoder(this, Locale("ka", "GE")).getFromLocation(point.latitude, point.longitude, 3)
                val address = results.orEmpty().firstOrNull { it.countryCode.equals("GE", true) }?.let {
                    listOfNotNull(it.thoroughfare, it.subThoroughfare, it.locality, it.subAdminArea).distinct().joinToString(", ")
                }?.ifBlank { null } ?: String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
                runOnUiThread { suppressTextSearch = true
                    if (mode == "A") { pickupPoint = point; pickup.setText(address); pickup.setSelection(pickup.text.length) }
                    else { destinationPoint = point; destination.setText(address); destination.setSelection(destination.text.length) }
                    suppressTextSearch = false
                }
            } catch (_: Exception) { }
        }
    }
    private fun clearRoute(message: String? = null) {
        routeRequestId++
        routeLine?.let { try { map?.removePolyline(it) } catch (_: Exception) { } }
        routeLine = null
        if (message != null) {
            estimate.text = message
            eta.text = "A və B nöqtələrini seçdikdən sonra marşrut hesablanacaq."
        }
    }

    private fun updateRoute() {
        val a = pickupPoint ?: return
        val b = destinationPoint ?: return
        val requestId = ++routeRequestId
        estimate.text = "Marşrut hesablanır..."
        eta.text = "Yol məlumatları yoxlanılır"
        thread {
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/${a.longitude},${a.latitude};${b.longitude},${b.latitude}?overview=full&geometries=geojson&steps=true")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 15000
                    setRequestProperty("User-Agent", "INTAXI-Android/1.0")
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
                if (json.optString("code") != "Ok" || json.optJSONArray("routes")?.length() == 0) {
                    throw IllegalStateException("No road route")
                }
                val route = json.getJSONArray("routes").getJSONObject(0)
                val km = route.getDouble("distance") / 1000.0
                val minutes = route.getDouble("duration") / 60.0
                val routePoints = mutableListOf<LatLng>()
                val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                for (i in 0 until coordinates.length()) {
                    val pair = coordinates.getJSONArray(i)
                    routePoints.add(LatLng(pair.getDouble(1), pair.getDouble(0)))
                }
                if (routePoints.size < 2) throw IllegalStateException("Invalid route geometry")
                val price = calculateRouteFare(routePoints, km, minutes, selectedTariff)
                runOnUiThread {
                    if (requestId != routeRequestId) return@runOnUiThread
                    routeLine?.let { try { map?.removePolyline(it) } catch (_: Exception) { } }
                    routeLine = map?.addPolyline(
                        PolylineOptions().addAll(routePoints).color(Color.rgb(190, 40, 65)).width(6f)
                    )
                    val middle = routePoints[routePoints.size / 2]
                    val zoom = when {
                        km > 45 -> 8.5
                        km > 20 -> 9.5
                        km > 8 -> 10.8
                        km > 3 -> 12.0
                        else -> 14.0
                    }
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(middle, zoom))
                    estimate.text = String.format(Locale.US, "Marşrut: %.1f km  •  %.0f dəq", km, minutes)
                    eta.text = String.format(Locale.US, "%s tarif · Təxmini qiymət: %.2f ₾", selectedTariff, price)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (requestId == routeRequestId) clearRoute("Marşrut tapılmadı. Ünvanları və interneti yoxlayın.")
                }
            }
        }
    }


    // დროებითი ქალაქის საზღვრის საგზაო ნიშნულები — მომხმარებლის მოწოდებული კოორდინატები.
    private data class FareBoundaryPoint(val latitude: Double, val longitude: Double)
    private data class FareInterval(val startMeters: Double, val endMeters: Double, val insideCity: Boolean)

    private val fareBoundaryPoints = listOf(
        // თბილისი
        FareBoundaryPoint(41.6893188, 45.0120082),
        FareBoundaryPoint(41.6300674, 44.9204118),
        FareBoundaryPoint(41.6851710, 44.7949280),
        FareBoundaryPoint(41.7133126, 44.7095433),
        FareBoundaryPoint(41.8210010, 44.7399870),
        FareBoundaryPoint(41.8273399, 44.7440589),
        FareBoundaryPoint(41.8270288, 44.8404129),
        // მარნეული — მოწოდებული ზუსტი საგზაო ნიშნულები
        FareBoundaryPoint(41.4571066, 44.8109462),
        FareBoundaryPoint(41.4954837, 44.7833104),
        FareBoundaryPoint(41.4640589, 44.8316569),
        FareBoundaryPoint(41.5098849, 44.7930796)
    )

    private fun fareDistanceMeters(a: LatLng, b: LatLng): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun isInsideKnownCity(point: LatLng): Boolean {
        val tbilisi = LatLng(41.7151, 44.8271)
        val marneuli = LatLng(41.475842, 44.812081)
        return fareDistanceMeters(point, tbilisi) <= 11000.0 ||
            fareDistanceMeters(point, marneuli) <= 4500.0
    }

    private fun calculateRouteFare(
        routePoints: List<LatLng>,
        totalKm: Double,
        minutes: Double,
        tariff: String
    ): Double {
        val comfort = tariff == "კომფორტი"
        val base = if (comfort) 5.0 else 3.0
        val cityRate = if (comfort) 0.70 else 0.50
        val outsideRate = if (comfort) 0.90 else 0.80
        val perMinute = if (comfort) 0.25 else 0.20
        if (routePoints.size < 2 || totalKm <= 0.0) {
            val rate = if (routePoints.firstOrNull()?.let { isInsideKnownCity(it) } == true) cityRate else outsideRate
            return base + kotlin.math.max(0.0, totalKm - 1.0) * rate +
                kotlin.math.max(0.0, minutes - 4.0) * perMinute
        }

        val cumulative = DoubleArray(routePoints.size)
        for (i in 1 until routePoints.size) {
            cumulative[i] = cumulative[i - 1] + fareDistanceMeters(routePoints[i - 1], routePoints[i])
        }
        val routeMeters = cumulative.last()
        if (routeMeters <= 0.0) return base + kotlin.math.max(0.0, totalKm - 1.0) * cityRate +
            kotlin.math.max(0.0, minutes - 4.0) * perMinute

        val crossings = mutableListOf<Double>()
        for (boundary in fareBoundaryPoints) {
            val point = LatLng(boundary.latitude, boundary.longitude)
            var bestDistance = Double.MAX_VALUE
            var bestAlongRoute = 0.0
            for (i in routePoints.indices) {
                val distance = fareDistanceMeters(routePoints[i], point)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestAlongRoute = cumulative[i]
                }
            }
            // Route geometry must pass close to a supplied road marker.
            val beforeTarget = (bestAlongRoute - 500.0).coerceAtLeast(0.0)
            val afterTarget = (bestAlongRoute + 500.0).coerceAtMost(routeMeters)
            val beforeIndex = cumulative.indices.lastOrNull { cumulative[it] <= beforeTarget } ?: 0
            val afterIndex = cumulative.indices.firstOrNull { cumulative[it] >= afterTarget } ?: routePoints.lastIndex
            val changesArea = isInsideKnownCity(routePoints[beforeIndex]) != isInsideKnownCity(routePoints[afterIndex])
            if (bestDistance <= 350.0 && changesArea && crossings.none { kotlin.math.abs(it - bestAlongRoute) < 700.0 }) {
                crossings.add(bestAlongRoute.coerceIn(0.0, routeMeters))
            }
        }
        crossings.sort()

        val intervals = mutableListOf<FareInterval>()
        var inside = isInsideKnownCity(routePoints.first())
        var cursor = 0.0
        for (crossing in crossings) {
            if (crossing > cursor) intervals.add(FareInterval(cursor, crossing, inside))
            inside = !inside
            cursor = crossing
        }
        if (cursor < routeMeters) intervals.add(FareInterval(cursor, routeMeters, inside))

        // The base fare includes the first kilometre once; subsequent distance is split by tariff area.
        val distanceScale = (totalKm * 1000.0) / routeMeters
        var freeMetersLeft = 1000.0
        var cityChargeKm = 0.0
        var outsideChargeKm = 0.0
        for (interval in intervals) {
            var length = (interval.endMeters - interval.startMeters) * distanceScale
            val freePart = kotlin.math.min(length, freeMetersLeft)
            length -= freePart
            freeMetersLeft -= freePart
            if (length > 0.0) {
                if (interval.insideCity) cityChargeKm += length / 1000.0
                else outsideChargeKm += length / 1000.0
            }
        }
        val distanceCharge = cityChargeKm * cityRate + outsideChargeKm * outsideRate
        return base + distanceCharge + kotlin.math.max(0.0, minutes - 4.0) * perMinute
    }


    private fun currentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return
        }
        try {
            val loc: Location? = lastKnownLocation()
            if (loc != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0))
        } catch (_: SecurityException) { }
    }

    private fun requestTaxi() {
        if (pickupPoint == null || destinationPoint == null) {
            AlertDialog.Builder(this).setTitle("İNTAXİ")
                .setMessage("Əvvəlcə A — götürülmə və B — gediləcək ünvanı seçin.")
                .setPositiveButton("კარგი", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Sifariş vermək")
            .setMessage("Marşrut və təxmini qiymət hesablanıb. Canlı sürücü sifarişlərini göndərmək üçün server hələ qoşulmayıb.")
            .setPositiveButton("Bağla", null).show()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}