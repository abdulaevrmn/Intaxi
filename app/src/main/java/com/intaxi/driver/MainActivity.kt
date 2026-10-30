package com.intaxi.driver

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
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
    private lateinit var onlineButton: LinearLayout
    private lateinit var onlineStatusText: TextView
    private lateinit var onlineActionText: TextView
    private lateinit var loadingSpinner: TextView
    private lateinit var navBar: LinearLayout
    private var spinnerAnimator: ObjectAnimator? = null

    private var isOnline = false
    private var selectedTab = "შეკვეთები"

    private val backgroundColor = Color.rgb(17, 17, 19)
    private val panelColor = Color.rgb(32, 32, 36)
    private val burgundyColor = Color.rgb(100, 29, 45)
    private val mutedColor = Color.rgb(170, 170, 175)
    private val greenColor = Color.rgb(28, 145, 91)

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
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }

        val brand = TextView(this).apply {
            text = "İNTAXİ\nმძღოლის აპლიკაცია"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(55), 1f))
        root.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68))
        )

        contentContainer = FrameLayout(this)
        root.addView(
            contentContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, dp(36))
            setBackgroundColor(panelColor)
        }
        root.addView(
            navBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88))
        )

        setContentView(root)
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
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }

        (mapView.parent as? ViewGroup)?.removeView(mapView)
        page.addView(
            mapView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(panelColor, 18)
        }

        // Daily income and completed orders are displayed side by side.
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statsRow.addView(
            createStatCard("დღის შემოსავალი", "0.00 ₾"),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        val statGap = View(this)
        statsRow.addView(statGap, LinearLayout.LayoutParams(dp(8), 1))

        statsRow.addView(
            createStatCard("შესრულებული შეკვეთები", "0"),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        controlPanel.addView(
            statsRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
        )

        onlineButton = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleOnline() }
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        loadingSpinner = TextView(this).apply {
            text = "⟳"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }

        onlineStatusText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        searchRow.addView(
            loadingSpinner,
            LinearLayout.LayoutParams(dp(30), dp(30))
        )
        searchRow.addView(
            onlineStatusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30)
            )
        )

        onlineActionText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        onlineButton.addView(
            searchRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(30)
            )
        )
        onlineButton.addView(
            onlineActionText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(27)
            )
        )

        controlPanel.addView(
            onlineButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(68)
            ).apply { topMargin = dp(9) }
        )

        page.addView(
            controlPanel,
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
        updateOnlineButton()
    }

    private fun createStatCard(title: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(6), dp(4))
            background = roundedBackground(Color.rgb(42, 42, 47), 12)

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 11f
                setTextColor(mutedColor)
                maxLines = 1
            })

            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 19f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            })
        }
    }

    private fun toggleOnline() {
        isOnline = !isOnline
        updateOnlineButton()
    }

    private fun updateOnlineButton() {
        if (!::onlineButton.isInitialized) return

        if (isOnline) {
            onlineButton.background = roundedBackground(greenColor, 16)
            onlineStatusText.text = "შეკვეთებს ვეძებთ..."
            onlineActionText.text = "ოფლაინზე გადასვლა"
            loadingSpinner.visibility = View.VISIBLE

            spinnerAnimator?.cancel()
            spinnerAnimator = ObjectAnimator.ofFloat(
                loadingSpinner, View.ROTATION, 0f, 360f
            ).apply {
                duration = 900
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        } else {
            spinnerAnimator?.cancel()
            loadingSpinner.rotation = 0f
            loadingSpinner.visibility = View.GONE
            onlineButton.background = roundedBackground(burgundyColor, 16)
            onlineStatusText.text = "ოფლაინ"
            onlineActionText.text = "ონლაინში გადასვლა"
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
                setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener { showTab(tab) }
            }

            item.addView(TextView(this).apply {
                text = symbol
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(if (selected) Color.WHITE else mutedColor)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(27)
            ))

            item.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (selected) Color.WHITE else mutedColor)
                if (selected) setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
        spinnerAnimator?.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }
}
