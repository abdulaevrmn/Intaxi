package com.intaxi.driver

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private var financePeriod = "დღე"
    private var supportDraft = ""

    private val backgroundColor = Color.rgb(17, 17, 19)
    private val panelColor = Color.rgb(32, 32, 36)
    private val cardColor = Color.rgb(42, 42, 47)
    private val burgundyColor = Color.rgb(100, 29, 45)
    private val mutedColor = Color.rgb(170, 170, 175)
    private val greenColor = Color.rgb(28, 145, 91)
    private val white = Color.WHITE

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
            setTextColor(white)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(55), 1f))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(68)))

        contentContainer = FrameLayout(this)
        root.addView(contentContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        // Bottom menu content is raised, with extra space below the icons and labels.
        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(3), 0, dp(36))
            setBackgroundColor(panelColor)
        }
        root.addView(navBar, LinearLayout.LayoutParams(-1, dp(88)))
        setContentView(root)
        buildNavigation()
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        contentContainer.removeAllViews()
        when (tab) {
            "შეკვეთები" -> showOrdersScreen()
            "ჩატები" -> showChatsScreen()
            "ფინანსები" -> showFinanceScreen()
            "პროფილი" -> showProfileScreen()
        }
        buildNavigation()
    }

    private fun showOrdersScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }
        (mapView.parent as? ViewGroup)?.removeView(mapView)
        page.addView(mapView, LinearLayout.LayoutParams(-1, 0, 1f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(panelColor, 18)
        }
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stats.addView(createStatCard("დღის შემოსავალი", "0.00 ₾"),
            LinearLayout.LayoutParams(0, dp(58), 1f))
        stats.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        stats.addView(createStatCard("შესრულებული შეკვეთები", "0"),
            LinearLayout.LayoutParams(0, dp(58), 1f))
        panel.addView(stats, LinearLayout.LayoutParams(-1, dp(58)))

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
            setTextColor(white)
            visibility = View.GONE
        }
        onlineStatusText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        searchRow.addView(loadingSpinner, LinearLayout.LayoutParams(dp(30), dp(30)))
        searchRow.addView(onlineStatusText, LinearLayout.LayoutParams(-2, dp(30)))
        onlineActionText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        onlineButton.addView(searchRow, LinearLayout.LayoutParams(-1, dp(30)))
        onlineButton.addView(onlineActionText, LinearLayout.LayoutParams(-1, dp(27)))
        panel.addView(onlineButton, LinearLayout.LayoutParams(-1, dp(68)).apply {
            topMargin = dp(9)
        })
        page.addView(panel, LinearLayout.LayoutParams(-1, -2))
        contentContainer.addView(page, FrameLayout.LayoutParams(-1, -1))
        updateOnlineButton()
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
            spinnerAnimator = ObjectAnimator.ofFloat(loadingSpinner, View.ROTATION, 0f, 360f).apply {
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

    private fun showChatsScreen() {
        val page = verticalPage()
        page.addView(pageTitle("ჩატები"))
        page.addView(infoCard(
            "მხარდაჭერის ჩატი",
            "აქ შეძლებთ İNTAXİ-ის მხარდაჭერის გუნდთან დაკავშირებას, შეკვეთის პრობლემის ან ანგარიშის საკითხის გაგზავნას."
        ))
        val empty = TextView(this).apply {
            text = "ჯერ შეტყობინებები არ არის"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(mutedColor)
            setPadding(dp(12), dp(35), dp(12), dp(35))
        }
        page.addView(empty, LinearLayout.LayoutParams(-1, -2))
        val input = EditText(this).apply {
            hint = "შეტყობინების ტექსტი"
            setHintTextColor(mutedColor)
            setTextColor(white)
            textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(cardColor, 12)
            isEnabled = false
        }
        page.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        val send = actionButton("მხარდაჭერის ჩატი მალე ჩაირთვება", false) {
            showNotice("ჩატი", "მხარდაჭერის ჩატი ჩაირთვება серверთან დაკავშირების შემდეგ.")
        }
        page.addView(send, fullWidthButtonParams())
        page.addView(sectionNote("შეტყობინებების გაგზავნა და მიღება ამ ვერსიაში ჯერ არ არის დაკავშირებული."))
        showScrollable(page)
    }

    private fun showFinanceScreen() {
        val page = verticalPage()
        page.addView(pageTitle("ფინანსები"))
        page.addView(infoCard("ხელმისაწვდომი ბალანსი", "— ₾", "ბალანსი გამოჩნდება ანგარიშის სისტემასთან დაკავშირების შემდეგ."))

        val periodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        listOf("დღე", "კვირა", "თვე").forEach { period ->
            val selected = financePeriod == period
            val b = actionButton(period, selected) { financePeriod = period; showTab("ფინანსები") }
            periodRow.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(5)
            })
        }
        page.addView(periodRow)

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(panelColor, 16)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        summary.addView(rowLabelValue("შემოსავალი", "— ₾"))
        summary.addView(rowLabelValue("შესრულებული შეკვეთები", "—"))
        summary.addView(rowLabelValue("ბონუსი და ჩაი", "— ₾"))
        summary.addView(rowLabelValue("საკომისიო", "— ₾"))
        summary.addView(rowLabelValue("ხელზე დარჩენილი", "— ₾", true))
        page.addView(summary, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        page.addView(pageTitle("ბოლო ოპერაციები"))
        page.addView(infoCard("ოპერაციების ისტორია", "ჯერ ტრანზაქციები არ არის. შეკვეთებისა და გადახდების ისტორია გამოჩნდება სისტემის ჩართვის შემდეგ."))
        page.addView(actionButton("თანხის გამოტანა", false) {
            showNotice("თანხის გამოტანა", "თანხის გადარიცხვა ჩაირთვება საბანკო და გადახდის სისტემის ინტეგრაციის შემდეგ.")
        }, fullWidthButtonParams())
        page.addView(sectionNote("ფინანსური მაჩვენებლები ჯერ რეალურ ანგარიშთან არ არის დაკავშირებული. აქ ნაჩვენები ცარიელი მნიშვნელობები გამოგონილი მონაცემები არ არის."))
        showScrollable(page)
    }

    private fun showProfileScreen() {
        val page = verticalPage()
        page.addView(pageTitle("პროფილი"))
        page.addView(infoCard("მძღოლის ანგარიში", "სახელი და გვარი\nარ არის მითითებული\n\nტელეფონი\nარ არის მითითებული"))
        page.addView(pageTitle("ავტომობილი"))
        page.addView(infoCard("ავტომობილის მონაცემები", "მარკა და მოდელი: —\nსახელმწიფო ნომერი: —\nფერი: —"))
        page.addView(pageTitle("პარამეტრები"))
        page.addView(settingRow("შეტყობინებები", "შეკვეთებისა და ანგარიშის შეტყობინებები"))
        page.addView(settingRow("ხმა", "შეკვეთის ხმოვანი სიგნალი"))
        page.addView(actionButton("პირადი məlumatları redaktə et", false) {
            showNotice("პროფილის რედაქტირება", "პირადი და ავტომობილის მონაცემების რედაქტირება ჩაირთვება რეგისტრაციისა და ანგარიშის მოდულის დამატების შემდეგ.")
        }, fullWidthButtonParams())
        page.addView(actionButton("აპლიკაციის შესახებ", true) {
            showNotice("İNTAXİ Driver", "მძღოლის აპლიკაცია İNTAXİ. ვერსია 0.2.0")
        }, fullWidthButtonParams())
        page.addView(actionButton("გასვლა", false) {
            showNotice("გასვლა", "შესვლის სისტემა ჯერ არ არის დამატებული.")
        }, fullWidthButtonParams())
        showScrollable(page)
    }

    private fun settingRow(title: String, subtitle: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(panelColor, 13)
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(mutedColor)
        })
        return row
    }

    private fun rowLabelValue(label: String, value: String, emphasize: Boolean = false): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(if (emphasize) white else mutedColor)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(if (emphasize) Color.rgb(95, 220, 145) else white)
            setTypeface(null, Typeface.BOLD)
        })
        return row
    }

    private fun createStatCard(title: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(6), dp(4))
            background = roundedBackground(cardColor, 12)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 11f
                setTextColor(mutedColor)
                maxLines = 1
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 19f
                setTextColor(white)
                setTypeface(null, Typeface.BOLD)
            })
        }
    }

    private fun verticalPage(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(20))
    }

    private fun showScrollable(page: LinearLayout) {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
            setBackgroundColor(backgroundColor)
        }
        contentContainer.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun pageTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 22f
        setTextColor(white)
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(13))
    }

    private fun infoCard(title: String, body: String, footnote: String? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = roundedBackground(panelColor, 15)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 15f
                setTextColor(white)
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = body
                textSize = 14f
                setTextColor(if (body.contains("—")) mutedColor else white)
                setPadding(0, dp(9), 0, 0)
            })
            if (footnote != null) addView(TextView(this@MainActivity).apply {
                text = footnote
                textSize = 11f
                setTextColor(mutedColor)
                setPadding(0, dp(6), 0, 0)
            })
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun sectionNote(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(mutedColor)
        setPadding(dp(3), dp(12), dp(3), dp(16))
    }

    private fun actionButton(text: String, primary: Boolean, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(white)
            background = roundedBackground(if (primary) burgundyColor else cardColor, 12)
            setOnClickListener { action() }
        }
    }

    private fun fullWidthButtonParams() = LinearLayout.LayoutParams(-1, dp(48)).apply {
        topMargin = dp(7)
        bottomMargin = dp(5)
    }

    private fun showNotice(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("კარგი", null)
            .show()
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
                setTextColor(if (selected) white else mutedColor)
            }, LinearLayout.LayoutParams(-1, dp(27)))
            item.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (selected) white else mutedColor)
                if (selected) setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, -2))
            navBar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
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
