package com.intaxi.driver

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var contentContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var onlineButton: LinearLayout
    private lateinit var onlineStatusText: TextView
    private lateinit var onlineActionText: TextView
    private lateinit var navBar: LinearLayout

    private var isOnline = false
    private var selectedTab = "შეკვეთები"

    private val backgroundColor = Color.rgb(17, 17, 19)
    private val panelColor = Color.rgb(32, 32, 36)
    private val burgundyColor = Color.rgb(100, 29, 45)
    private val mutedColor = Color.rgb(170, 170, 175)
    private val greenColor = Color.rgb(57, 184, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->
            map.setStyle("https://tiles.openfreemap.org/styles/liberty")
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(41.7151, 44.8271))
                .zoom(12.0)
                .build()
        }

        buildMainScreen()
        showTab("შეკვეთები")
    }

    private fun buildMainScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }

        val brand = TextView(this).apply {
            text = "İNTAXİ\nმძღოლის აპლიკაცია"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }

        header.addView(brand, LinearLayout.LayoutParams(0, dp(55), 1f))

        statusText = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        header.addView(statusText)

        root.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(75))
        )

        contentContainer = FrameLayout(this)
        root.addView(
            contentContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
            setBackgroundColor(panelColor)
        }

        root.addView(
            navBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76))
        )

        setContentView(root)
        updateStatus()
        buildNavigation()
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        contentContainer.removeAllViews()

        if (tab == "შეკვეთები") {
            showOrdersScreen()
        } else {
            showPlaceholder(tab)
        }

        buildNavigation()
    }

    private fun showOrdersScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(8))
        }

        val oldParent = mapView.parent
        if (oldParent is ViewGroup) {
            oldParent.removeView(mapView)
        }

        page.addView(
            mapView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBackground(panelColor, 18)
        }

        val incomeTitle = TextView(this).apply {
            text = "დღევანდელი შემოსავალი"
            textSize = 13f
            setTextColor(mutedColor)
        }

        val incomeValue = TextView(this).apply {
            text = "0.00 ₾"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        infoPanel.addView(incomeTitle)
        infoPanel.addView(incomeValue)

        onlineButton = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBackground(burgundyColor, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                isOnline = !isOnline
                updateStatus()
            }
        }

        onlineStatusText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
        }

        onlineActionText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        onlineButton.addView(
            onlineStatusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        onlineButton.addView(
            onlineActionText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        infoPanel.addView(
            onlineButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
            ).apply {
                topMargin = dp(10)
            }
        )

        page.addView(
            infoPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        contentContainer.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        updateStatus()
    }

    private fun updateStatus() {
        if (!::statusText.isInitialized) return

        if (isOnline) {
            statusText.text = "● ონლაინ"
            statusText.setTextColor(greenColor)
        } else {
            statusText.text = "● ოფლაინ"
            statusText.setTextColor(mutedColor)
        }

        if (::onlineButton.isInitialized) {
            onlineStatusText.text = if (isOnline) "ონლაინ" else "ოფლაინ"
            onlineActionText.text = if (isOnline) {
                "ოფლაინზე გადასვლა"
            } else {
                "ონლაინში გადასვლა"
            }

            onlineButton.background = roundedBackground(
                if (isOnline) Color.rgb(53, 53, 59) else burgundyColor,
                16
            )
        }
    }

    private fun buildNavigation() {
        navBar.removeAllViews()

        val tabs = listOf(
            Triple("შეკვეთები", "⌖", "შეკვეთები"),
            Triple("ჩატები", "▤", "ჩატები"),
            Triple("ფინანსები", "₾", "ფინანსები"),
            Triple("პროფილი", "●", "პროფილი")
        )

        for ((tab, symbol, label) in tabs) {
            val selected = selectedTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(2), dp(2), dp(2), dp(2))
                setOnClickListener { showTab(tab) }
            }

            val icon = TextView(this).apply {
                text = symbol
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(if (selected) Color.WHITE else mutedColor)
            }

            val title = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (selected) Color.WHITE else mutedColor)
                if (selected) setTypeface(null, Typeface.BOLD)
            }

            item.addView(
                icon,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(27)
                )
            )
            item.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            navBar.addView(
                item,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
    }

    private fun showPlaceholder(tab: String) {
        val message = TextView(this).apply {
            text = "$tab\n\nეს განყოფილება მზადების პროცესშია"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
        }

        contentContainer.addView(
            message,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
}
