package com.intaxi.passenger

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var root: FrameLayout
    private lateinit var pickup: EditText
    private lateinit var destination: EditText
    private lateinit var standard: Button
    private lateinit var comfort: Button
    private var selectedTariff = "სტანდარტი"

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
        mapView.getMapAsync { map ->
            map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(41.7151, 44.8271))
                    .zoom(12.0)
                    .build()
            }
        }
        buildPassengerScreen()
        setContentView(root)
        requestLocationPermission()
    }

    private fun buildPassengerScreen() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = rounded(bgColor, 20)
            elevation = dp(9).toFloat()
        }
        column.addView(TextView(this).apply {
            text = "İNTAXİ"
            textSize = 23f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(10))
        })
        pickup = addressField("საიდან?  აირჩიეთ ჩასხდომის ადგილი")
        destination = addressField("სად მიდიხართ?")
        column.addView(pickup, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        column.addView(destination, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })

        column.addView(TextView(this).apply {
            text = "აირჩიეთ ტარიფი"
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(2), 0, 0, dp(7))
        })
        val tariffs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        standard = tariffButton("სტანდარტი", true)
        comfort = tariffButton("კომფორტი", false)
        tariffs.addView(standard, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        tariffs.addView(comfort, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        column.addView(tariffs)

        val call = Button(this).apply {
            text = "ტაქსის გამოძახება"
            isAllCaps = false
            textSize = 16f
            setTextColor(white)
            background = rounded(burgundy, 13)
            setOnClickListener { requestTaxi() }
        }
        column.addView(call, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })

        root.addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(38), dp(12), 0)
        })

        val locate = TextView(this).apply {
            text = "⌖"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(panel, 28)
            elevation = dp(7).toFloat()
            setOnClickListener {
                mapView.getMapAsync { it.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(41.7151, 44.8271), 15.0)
                ) }
            }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(12)
        })
        root.addView(TextView(this).apply {
            text = "მგზავრობის შეკვეთა"
            textSize = 11f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Color.argb(210, 24, 24, 28), 9)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(18)
        })
    }

    private fun addressField(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setTextColor(white)
        setHintTextColor(muted)
        singleLine = true
        setPadding(dp(13), 0, dp(13), 0)
        background = rounded(Color.rgb(45, 45, 51), 11)
    }

    private fun tariffButton(label: String, selected: Boolean) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(white)
        background = rounded(if (selected) burgundy else Color.rgb(48, 48, 54), 11)
        setOnClickListener {
            selectedTariff = label
            standard.background = rounded(if (label == "სტანდარტი") burgundy else Color.rgb(48, 48, 54), 11)
            comfort.background = rounded(if (label == "კომფორტი") burgundy else Color.rgb(48, 48, 54), 11)
        }
    }

    private fun requestTaxi() {
        if (pickup.text.toString().isBlank() || destination.text.toString().isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("მიუთითეთ ჩასხდომისა და დანიშნულების ადგილი.")
                .setPositiveButton("კარგი", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("İNTAXİ")
            .setMessage("შეკვეთის ფორმა მზად არის. მძღოლთან შეკვეთის გაგზავნა ჯერ არ მუშაობს — სერვერი ჯერ არ არის დაკავშირებული.")
            .setPositiveButton("კარგი", null)
            .show()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 101)
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}