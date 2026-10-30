package com.intaxi.driver

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    private lateinit var onlineButton: Button
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
            map.setStyle(
                "https://tiles.openfreemap.org/styles/liberty"
            )

            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(41.7151, 44.8271))
                .zoom(12.0)
                .build()
        }

        buildMainScreen()
        showTab("შეკვეთები")
    }

    private fun buildMainScreen() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(backgroundColor)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(10), dp(18), dp(10))

        val brand = TextView(this)
        brand.text = "İNTAXİ\nმძღოლის აპლიკაცია"
        brand.setTextColor(Color.WHITE)
        brand.textSize = 17f
        brand.setTypeface(null, Typeface.BOLD)

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        statusText = TextView(this)
        statusText.textSize = 14f
        statusText.setTypeface(null, Typeface.BOLD)
        header.addView(statusText)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(75)
            )
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

        navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.gravity = Gravity.CENTER
        navBar.setBackgroundColor(panelColor)

        root.addView(
            navBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(65)
            )
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
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(10), dp(5), dp(10), dp(8))

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

        val infoPanel = LinearLayout(this)
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.setPadding(dp(16), dp(12), dp(16), dp(12))
        infoPanel.background = roundedBackground(panelColor, 18)

        val incomeTitle = TextView(this)
        incomeTitle.text = "დღევანდელი შემოსავალი"
        incomeTitle.textSize = 13f
        incomeTitle.setTextColor(mutedColor)

        val incomeValue = TextView(this)
        incomeValue.text = "0.00 ₾"
        incomeValue.textSize = 25f
        incomeValue.setTextColor(Color.WHITE)
        incomeValue.setTypeface(null, Typeface.BOLD)

        infoPanel.addView(incomeTitle)
        infoPanel.addView(incomeValue)

        onlineButton = Button(this)
        onlineButton.textSize = 15f
        onlineButton.setTextColor(Color.WHITE)
        onlineButton.setTypeface(null, Typeface.BOLD)
        onlineButton.backgroundTintList =
            ColorStateList.valueOf(burgundyColor)

        onlineButton.setOnClickListener {
            isOnline = !isOnline
            updateStatus()
        }

        infoPanel.addView(
            onlineButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        page.addView(
            infoPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val mapLayoutParams = page.layoutParams
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
            onlineButton.text = if (isOnline) {
                "ოფლაინზე გადასვლა"
            } else {
                "ონლაინში ჩართვა"
            }

            onlineButton.backgroundTintList =
                ColorStateList.valueOf(
                    if (isOnline) {
                        Color.rgb(53, 53, 59)
                    } else {
                        burgundyColor
                    }
                )
        }
    }

    private fun buildNavigation() {
        navBar.removeAllViews()

        val tabs = listOf(
            "შეკვეთები",
            "ჩატები",
            "ფინანსები",
            "პროფილი"
        )

        for (tab in tabs) {
            val item = TextView(this)
            item.text = tab
            item.gravity = Gravity.CENTER
            item.textSize = 12f
            item.setTextColor(
                if (selectedTab == tab) {
                    Color.WHITE
                } else {
                    mutedColor
                }
            )

            if (selectedTab == tab) {
                item.setTypeface(null, Typeface.BOLD)
            }

            item.setOnClickListener {
                showTab(tab)
            }

            navBar.addView(
                item,
                LinearLayout.LayoutParams(0, dp(65), 1f)
            )
        }
    }

    private fun showPlaceholder(tab: String) {
        val message = TextView(this)
        message.text = "$tab\n\nეს განყოფილება მზადების პროცესშია"
        message.gravity = Gravity.CENTER
        message.textSize = 18f
        message.setTextColor(Color.WHITE)

        contentContainer.addView(
            message,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
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
