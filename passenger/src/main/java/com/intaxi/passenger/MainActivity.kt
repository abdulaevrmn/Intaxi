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
    private var selectedTariff = "სტანდარტი"
    private var pickupPoint: LatLng? = null
    private var destinationPoint: LatLng? = null
    private var pinMode: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

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
                        pinHint.text = if (mode == "A") "A nöqtəsi seçildi" else "B nöqtəsi seçildi"
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
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(bgColor, 20)
            elevation = dp(9).toFloat()
        }
        column.addView(TextView(this).apply {
            text = "İNTAXİ"; textSize = 22f; setTextColor(white)
            setTypeface(null, Typeface.BOLD); setPadding(dp(2), 0, 0, dp(8))
        })
        val aRow = addressRow("საიდან?  ჩასხდომის მისამართი", true)
        val bRow = addressRow("სად მიდიხართ?  დანიშნულება", false)
        column.addView(aRow, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })
        column.addView(bRow, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        column.addView(TextView(this).apply {
            text = "აირჩიეთ ტარიფი"; textSize = 12f; setTextColor(muted); setPadding(dp(2), 0, 0, dp(5))
        })
        val tariffs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        standard = tariffButton("სტანდარტი", true)
        comfort = tariffButton("კომფორტი", false)
        tariffs.addView(standard, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        tariffs.addView(comfort, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        column.addView(tariffs)
        estimate = TextView(this).apply {
            text = "მანძილი და სავარაუდო ფასი გამოჩნდება A და B წერტილების არჩევის შემდეგ"
            textSize = 12f; setTextColor(white); setPadding(dp(3), dp(8), dp(3), 0)
        }
        eta = TextView(this).apply { textSize = 11f; setTextColor(muted); setPadding(dp(3), dp(4), dp(3), 0) }
        column.addView(estimate)
        column.addView(eta)
        val call = Button(this).apply {
            text = "ტაქსის გამოძახება"; isAllCaps = false; textSize = 15f; setTextColor(white)
            background = rounded(burgundy, 12); setOnClickListener { requestTaxi() }
        }
        column.addView(call, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        root.addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(10), dp(30), dp(10), 0)
        })

        val locate = TextView(this).apply {
            text = "⌖"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(white)
            background = rounded(panel, 28); elevation = dp(7).toFloat()
            setOnClickListener { currentLocation() }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(12)
        })
        pinHint = TextView(this).apply {
            text = "რუკაზე წერტილის ასარჩევად დააჭირეთ „რუკაზე“"
            textSize = 12f; setTextColor(white); gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(Color.argb(220, 25, 25, 30), 10)
            visibility = View.GONE
        }
        root.addView(pinHint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(78))
        })
        pinConfirm = Button(this).apply {
            text = "წერტილის დადასტურება"; isAllCaps = false; setTextColor(white)
            background = rounded(burgundy, 12); visibility = View.GONE
            setOnClickListener { pinMode = null; pinHint.visibility = View.GONE; visibility = View.GONE; updateRoute() }
        }
        root.addView(pinConfirm, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(18))
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
                if (s.isNullOrBlank()) return
                if (isPickup) pickupPoint = null else destinationPoint = null
                searchRunnable?.let { handler.removeCallbacks(it) }
                val query = s.toString()
                val task = Runnable { searchAddress(query, isPickup) }
                searchRunnable = task
                handler.postDelayed(task, 850)
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
                val geocoder = Geocoder(this, Locale("ka", "GE"))
                val results = geocoder.getFromLocationName(query, 5)
                if (!results.isNullOrEmpty()) runOnUiThread {
                    val names = results.map { a ->
                        listOfNotNull(a.thoroughfare, a.subThoroughfare, a.locality, a.subAdminArea).distinct().joinToString(", ").ifBlank { a.getAddressLine(0) ?: query }
                    }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("აირჩიეთ მისამართი")
                        .setItems(names) { _, which ->
                            val a = results[which]
                            val p = LatLng(a.latitude, a.longitude)
                            if (isPickup) { pickupPoint = p; pickup.setText(names[which]); pickup.setSelection(pickup.text.length) }
                            else { destinationPoint = p; destination.setText(names[which]); destination.setSelection(destination.text.length) }
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 15.0))
                            updateRoute()
                        }.show()
                }
            } catch (_: Exception) { }
        }
    }

    private fun reverseGeocode(point: LatLng, mode: String) {
        thread {
            try {
                val results = Geocoder(this, Locale("ka", "GE")).getFromLocation(point.latitude, point.longitude, 1)
                val address = results?.firstOrNull()?.let {
                    listOfNotNull(it.thoroughfare, it.subThoroughfare, it.locality, it.subAdminArea).distinct().joinToString(", ")
                }?.ifBlank { null } ?: String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
                runOnUiThread {
                    if (mode == "A") { pickup.setText(address); pickup.setSelection(pickup.text.length) }
                    else { destination.setText(address); destination.setSelection(destination.text.length) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun updateRoute() {
        val a = pickupPoint ?: return
        val b = destinationPoint ?: return
        thread {
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/${a.longitude},${a.latitude};${b.longitude},${b.latitude}?overview=false")
                val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 9000; readTimeout = 12000 }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
                val route = json.getJSONArray("routes").getJSONObject(0)
                val km = route.getDouble("distance") / 1000.0
                val minutes = route.getDouble("duration") / 60.0
                val price = calculateFare(km, minutes, selectedTariff)
                runOnUiThread {
                    estimate.text = String.format(Locale.US, "A–B: %.1f km  •  %.0f dəqiqə  •  Təxmini qiymət: %.2f ₾", km, minutes, price)
                    eta.text = "A nöqtəsinə sürücünün çatma vaxtı: sürücü GPS-i və canlı sifariş sistemi qoşulanda hesablanacaq."
                }
            } catch (_: Exception) {
                runOnUiThread { estimate.text = "Marşrut hesablanmadı. Ünvanları və internet bağlantısını yoxlayın." }
            }
        }
    }

    private fun calculateFare(km: Double, minutes: Double, tariff: String): Double {
        val base = if (tariff == "სტანდარტი") 3.0 else 5.0
        val perKm = if (tariff == "სტანდარტი") 0.50 else 0.70
        val perMinute = if (tariff == "სტანდარტი") 0.20 else 0.25
        return base + max(0.0, km - 1.0) * perKm + max(0.0, minutes - 4.0) * perMinute
    }

    private fun currentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return
        }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc: Location? = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (loc != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0))
        } catch (_: SecurityException) { }
    }

    private fun requestTaxi() {
        if (pickupPoint == null || destinationPoint == null) {
            AlertDialog.Builder(this).setMessage("A და B მისამართlarını seçin və ya xəritədə pin qoyun.")
                .setPositiveButton("კარგი", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("İNTAXİ")
            .setMessage("A və B nöqtələri seçilib. Sifarişin sürücüyə göndərilməsi üçün server hələ qoşulmayıb.")
            .setPositiveButton("კარგი", null).show()
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