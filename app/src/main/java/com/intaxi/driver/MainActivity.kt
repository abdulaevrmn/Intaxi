package com.intaxi.driver

import android.Manifest
import android.app.AlertDialog
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var content: FrameLayout
    private lateinit var navBar: LinearLayout
    private lateinit var radar: LinearLayout
    private lateinit var onlinePanel: LinearLayout
    private lateinit var onlineStatus: TextView
    private lateinit var onlineAction: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var gpsPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var map: MapLibreMap? = null
    private var mapReady = false
    private var online = false
    private var navRevealed = false
    private var selectedTab = "შეკვეთები"
    private var period = "დღე"
    private var touchY = 0f

    private val bg = Color.rgb(17, 17, 19)
    private val panel = Color.rgb(32, 32, 36)
    private val card = Color.rgb(42, 42, 47)
    private val red = Color.rgb(100, 29, 45)
    private val green = Color.rgb(28, 145, 91)
    private val muted = Color.rgb(170, 170, 175)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("intaxi_settings", MODE_PRIVATE)
        gpsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) enableGps()
            else notice("მდებარეობის ნებართვა", "რეალური GPS-ის გამოსაყენებლად İNTAXİ-ს მდებარეობის ნებართვა სჭირდება.")
        }
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                mapReady = true
                enableGps()
                applyMapSettings()
            }
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(41.7151, 44.8271)).zoom(12.0).build()
        }
        buildShell()
        showTab("შეკვეთები")
        if (!hasGpsPermission()) {
            gpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "İNTAXİ\nმძღოლის აპლიკაცია"
            textSize = 17f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(60)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, dp(20))
            setBackgroundColor(panel)
        }
        root.addView(navBar, LinearLayout.LayoutParams(-1, dp(78)))
        navBar.translationY = -dp(39).toFloat()
        setContentView(root)
        buildNav()
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        content.removeAllViews()
        when (tab) {
            "შეკვეთები" -> ordersScreen()
            "ჩატები" -> chatsScreen()
            "ფინანსები" -> financeScreen()
            "პროფილი" -> profileScreen()
        }
        buildNav()
        navBar.visibility = if (online && !navRevealed) View.GONE else View.VISIBLE
    }

    private fun ordersScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(3), dp(7), dp(5))
        }
        val mapFrame = FrameLayout(this)
        (mapView.parent as? ViewGroup)?.removeView(mapView)
        mapFrame.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        radar = makeRadar()
        mapFrame.addView(radar, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(48); marginEnd = dp(12)
        })
        radar.visibility = if (online) View.VISIBLE else View.GONE
        radar.findViewWithTag<TextView>("radar_spinner")?.let { spinner ->
            ObjectAnimator.ofFloat(spinner, View.ROTATION, 0f, 360f).apply {
                duration = 1900L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
        val gpsButton = makeGpsButton()
        mapFrame.addView(gpsButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL or Gravity.END).apply {
            marginEnd = dp(13)
        })
        mapFrame.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) touchY = e.y
            if (e.action == MotionEvent.ACTION_UP && online) {
                val d = e.y - touchY
                if (d < -dp(55)) {
                    navRevealed = true
                    navBar.visibility = View.VISIBLE
                } else if (d > dp(55)) {
                    navRevealed = false
                    navBar.visibility = View.GONE
                }
            }
            false
        }
        page.addView(mapFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        onlinePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = rounded(panel, 17)
            visibility = if (online) View.GONE else View.VISIBLE
        }
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(stat("დღის შემოსავალი", "0.00 ₾"), LinearLayout.LayoutParams(0, dp(53), 1f))
        stats.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        stats.addView(stat("შესრულებული შეკვეთები", "0"), LinearLayout.LayoutParams(0, dp(53), 1f))
        onlinePanel.addView(stats)
        onlineStatus = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        onlineAction = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        val toggle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { toggleOnline() }
        }
        toggle.background = outlinedButton(panel, red, 16)
        toggle.addView(onlineStatus, LinearLayout.LayoutParams(-1, dp(22)))
        toggle.addView(onlineAction, LinearLayout.LayoutParams(-1, dp(24)))
        onlinePanel.addView(toggle, LinearLayout.LayoutParams(-1, dp(53)).apply { topMargin = dp(7) })
        page.addView(onlinePanel)
        content.addView(page, FrameLayout.LayoutParams(-1, -1))
        refreshOnlineUi()
    }

    private fun makeRadar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(4), dp(5), dp(4))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(green)
            setStroke(dp(3), Color.argb(170, 255, 255, 255))
        }
        elevation = dp(7).toFloat()
        isClickable = true
        setOnClickListener { toggleOnline() }
        addView(TextView(this@MainActivity).apply {
            tag = "radar_spinner"
            text = "◉"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(white)
        }, LinearLayout.LayoutParams(-1, dp(35)))
        addView(TextView(this@MainActivity).apply {
            text = "შეკვეთებს\nვეძებთ"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(30)))
        addView(TextView(this@MainActivity).apply {
            text = "ოფლაინ"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(white)
        }, LinearLayout.LayoutParams(-1, dp(16)))
    }

    private fun toggleOnline() {
        online = !online
        navRevealed = false
        if (::radar.isInitialized) radar.visibility = if (online) View.VISIBLE else View.GONE
        if (::onlinePanel.isInitialized) onlinePanel.visibility = if (online) View.GONE else View.VISIBLE
        navBar.visibility = if (online) View.GONE else View.VISIBLE
        refreshOnlineUi()
    }

    private fun refreshOnlineUi() {
        if (!::onlineStatus.isInitialized) return
        onlineStatus.text = if (online) "ონლაინ" else "ოფლაინ"
        onlineAction.text = if (online) "ოფლაინზე გადასვლა" else "ონლაინში გადასვლა"
        onlinePanel.background = rounded(panel, 16)
    }

    private fun makeGpsButton(): View = TextView(this).apply {
        text = "◎"
        textSize = 29f
        gravity = Gravity.CENTER
        setTextColor(white)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(35, 35, 40))
            setStroke(dp(2), Color.rgb(220, 220, 225))
        }
        elevation = dp(7).toFloat()
        contentDescription = "ჩემი მდებარეობა"
        setOnClickListener {
            if (!hasGpsPermission()) {
                gpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else {
                val m = map
                if (m != null && mapReady) {
                    try {
                        if (!m.locationComponent.isLocationComponentEnabled) enableGps()
                        m.locationComponent.cameraMode = CameraMode.TRACKING
                    } catch (_: Exception) {
                        notice("GPS", "მდებარეობა ჯერ ვერ განისაზღვრა. შეამოწმეთ GPS ტელეფონში.")
                    }
                }
            }
        }
    }

    private fun hasGpsPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableGps() {
        val m = map ?: return
        if (!mapReady || !hasGpsPermission()) return
        try {
            m.locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, m.style!!).build()
            )
            m.locationComponent.isLocationComponentEnabled = true
            m.locationComponent.cameraMode = if (prefs.getBoolean("map_rotate", true)) CameraMode.TRACKING_GPS else CameraMode.TRACKING
        } catch (_: Exception) {
            notice("GPS", "GPS ვერ ჩაირთო. შეამოწმეთ ტელეფონის მდებარეობის პარამეტრები.")
        }
    }

    private fun applyMapSettings() {
        val m = map ?: return
        val old = m.cameraPosition
        m.cameraPosition = CameraPosition.Builder(old)
            .tilt(if (prefs.getBoolean("map_3d", false)) 45.0 else 0.0)
            .build()
    }

    private fun chatsScreen() {
        val p = page()
        p.addView(title("ჩატები"))
        p.addView(info("მხარდაჭერის ჩატი", "მხარდაჭერისა და მგზავრებთან მიმოწერა ჩაირთვება სერვერთან და მგზავრის აპლიკაციასთან დაკავშირების შემდეგ."))
        p.addView(note("რეალური შეტყობინებები და შეკვეთის ჩატი საჭიროებს სერვერს."))
        scroll(p)
    }

    private fun financeScreen() {
        val p = page()
        p.addView(title("ფინანსები"))
        p.addView(info("ხელმისაწვდომი ბალანსი", "— ₾", "რეალური ბალანსი გამოჩნდება ანგარიშის სისტემასთან დაკავშირების შემდეგ."))
        val periods = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("დღე", "კვირა", "თვე").forEach { v ->
            periods.addView(button(v, period == v) { period = v; showTab("ფინანსები") },
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        }
        p.addView(periods)
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 15)
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        summary.addView(valueRow("შემოსავალი", "— ₾"))
        summary.addView(valueRow("შესრულებული შეკვეთები", "—"))
        summary.addView(valueRow("ბონუსი და ჩაი", "— ₾"))
        summary.addView(valueRow("საკომისიო", "— ₾"))
        summary.addView(valueRow("ხელზე დარჩენილი", "— ₾", true))
        p.addView(summary)
        p.addView(title("ბოლო ოპერაციები"))
        p.addView(info("ოპერაციების ისტორია", "რეალური ტრანზაქციები გამოჩნდება შეკვეთებისა და ფინანსური სისტემის ჩართვის შემდეგ."))
        scroll(p)
    }

    private fun profileScreen() {
        val p = page()
        p.addView(title("პროფილი"))
        p.addView(info("მძღოლის ანგარიში", "სახელი და გვარი\nარ არის მითითებული\n\nტელეფონი\nარ არის მითითებული"))
        p.addView(title("ტარიფები"))
        listOf("სტანდარტი", "კომფორტი").forEach {
            switchRow(p, it, "ტარიფის ჩართვა / გამორთვა", "tariff_" + it, false, false)
        }
        p.addView(title("პარამეტრები"))
        p.addView(setting("აპლიკაციის პარამეტრები", "ენა, ნავიგაცია, რუკა, ხმები და სხვა") { settingsScreen() })
        p.addView(setting("ავტომობილი", "ავტომობილის მონაცემები") {
            notice("ავტომობილი", "მონაცემების მართვა დაემატება მძღოლის ანგარიშის მოდულთან ერთად.")
        })
        p.addView(setting("აპლიკაციის შესახებ", "İNTAXİ Driver · 0.3.0") {
            notice("İNTAXİ Driver", "მძღოლის აპლიკაცია İNTAXİ. ვერსია 0.3.0")
        })
        p.addView(button("გასვლა", false) { notice("გასვლა", "შესვლის სისტემა ჯერ არ არის დამატებული.") }, fullButton())
        scroll(p)
    }

    private fun settingsScreen() {
        val p = page()
        p.addView(title("პარამეტრები"))
        p.addView(title("ენა"))
        p.addView(setting("აპლიკაციის ენა", prefs.getString("language", "ქართული") ?: "ქართული") {
            choose("ენა", listOf("ქართული", "Azərbaycan dili", "English"), "language")
        })
        p.addView(title("ნავიგაცია"))
        p.addView(setting("ნავიგაციის აპლიკაცია", prefs.getString("navigation", "İNTAXİ რუკა") ?: "İNTAXİ რუკა") {
            choose("ნავიგაცია", listOf("İNTAXİ რუკა", "Google Maps", "Waze"), "navigation")
        })
        p.addView(title("რუკა"))
        switchRow(p, "3D რუკა", "რუკის დახრილი 3D ხედვა", "map_3d", false, true)
        switchRow(p, "რუკის ავტომატური ბრუნვა", "მოძრაობის მიმართულების მიხედვით", "map_rotate", true, true)
        switchRow(p, "სიჩქარის გაფრთხილება", "სიჩქარის ზღვრის გადაჭარბებისას", "speed_warning", true, true)
        p.addView(setting("სიჩქარის ზღვარი", prefs.getInt("speed_limit", 60).toString() + " კმ/სთ") {
            choose("სიჩქარის ზღვარი", listOf("40", "50", "60", "70", "80", "90", "100"), "speed_limit", true)
        })
        p.addView(title("ხმები"))
        switchRow(p, "შეკვეთის ხმა", "ახალი შეკვეთის ხმოვანი სიგნალი", "sound_order", true, true)
        switchRow(p, "ხმოვანი გაფრთხილებები", "სიჩქარისა და სისტემის გაფრთხილებები", "sound_alerts", true, true)
        p.addView(title("სხვა"))
        switchRow(p, "შეტყობინებები", "აპლიკაციის შეტყობინებები", "notifications", true, true)
        p.addView(setting("GPS ნებართვა", "ტელეფონის მდებარეობის პარამეტრები") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        })
        p.addView(note("პარამეტრები ამ ეტაპზე ინახება ამ ტელეფონში. ანგარიშთან სინქრონიზაცია დაემატება სერვერის ჩართვის შემდეგ."))
        p.addView(button("პროფილზე დაბრუნება", false) { profileScreen() }, fullButton())
        scroll(p)
    }

    private fun choose(title: String, options: List<String>, key: String, numeric: Boolean = false) {
        val current = if (numeric) prefs.getInt(key, 60).toString()
            else prefs.getString(key, options[0]) ?: options[0]
        AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), options.indexOf(current).coerceAtLeast(0)) { d, i ->
                if (numeric) prefs.edit().putInt(key, options[i].toInt()).apply()
                else prefs.edit().putString(key, options[i]).apply()
                d.dismiss()
                settingsScreen()
            }.setNegativeButton("გაუქმება", null).show()
    }

    private fun switchRow(parent: LinearLayout, name: String, sub: String, key: String, default: Boolean, settings: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(panel, 13)
            isClickable = true
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = name; textSize = 14f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            text = sub; textSize = 11f; setTextColor(muted)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val state = TextView(this).apply {
            text = if (prefs.getBoolean(key, default)) "ჩართულია" else "გამორთულია"
            textSize = 11f
            setTextColor(if (prefs.getBoolean(key, default)) Color.rgb(95, 220, 145) else muted)
        }
        row.addView(state)
        row.setOnClickListener {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, default)).apply()
            if (key == "map_3d") applyMapSettings()
            if (settings) settingsScreen() else profileScreen()
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }

    private fun setting(name: String, sub: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(panel, 13)
            isClickable = true
            setOnClickListener { action() }
            addView(TextView(this@MainActivity).apply {
                text = name; textSize = 14f; setTextColor(white); setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub; textSize = 11f; setTextColor(muted)
            })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
        }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(10), dp(15), dp(18))
    }

    private fun scroll(p: LinearLayout) {
        val s = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg); addView(p) }
        content.addView(s, FrameLayout.LayoutParams(-1, -1))
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 20f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(10))
    }

    private fun info(t: String, body: String, foot: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = rounded(panel, 14)
        addView(TextView(this@MainActivity).apply {
            text = t; textSize = 15f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        })
        addView(TextView(this@MainActivity).apply {
            text = body; textSize = 14f; setTextColor(if (body.contains("—")) muted else white)
            setPadding(0, dp(8), 0, 0)
        })
        if (foot != null) addView(TextView(this@MainActivity).apply {
            text = foot; textSize = 11f; setTextColor(muted); setPadding(0, dp(5), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 12f; setTextColor(muted); setPadding(dp(3), dp(10), dp(3), dp(14))
    }

    private fun stat(t: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9), dp(4), dp(5), dp(4))
        background = rounded(card, 11)
        addView(TextView(this@MainActivity).apply {
            text = t; textSize = 10f; setTextColor(muted); maxLines = 1
        })
        addView(TextView(this@MainActivity).apply {
            text = v; textSize = 18f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        })
    }

    private fun valueRow(t: String, v: String, strong: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(11), 0, dp(11))
        addView(TextView(this@MainActivity).apply {
            text = t; textSize = 14f; setTextColor(if (strong) white else muted)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = v; textSize = 15f
            setTextColor(if (strong) Color.rgb(95, 220, 145) else white)
            setTypeface(null, Typeface.BOLD)
        })
    }

    private fun button(t: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 13f; setTextColor(white)
        background = rounded(if (primary) red else card, 11)
        setOnClickListener { action() }
    }

    private fun fullButton() = LinearLayout.LayoutParams(-1, dp(47)).apply {
        topMargin = dp(7); bottomMargin = dp(5)
    }

    private fun notice(t: String, msg: String) {
        AlertDialog.Builder(this).setTitle(t).setMessage(msg).setPositiveButton("კარგი", null).show()
    }

    private fun buildNav() {
        if (!::navBar.isInitialized) return
        navBar.removeAllViews()
        listOf(Triple("შეკვეთები", "⌖", "შეკვეთები"),
            Triple("ჩატები", "▤", "ჩატები"),
            Triple("ფინანსები", "₾", "ფინანსები"),
            Triple("პროფილი", "●", "პროფილი")).forEach { (tab, symbol, label) ->
            val chosen = selectedTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener {
                    if (online) { online = false; navRevealed = false }
                    showTab(tab)
                }
            }
            item.addView(TextView(this).apply {
                text = symbol; textSize = 21f; gravity = Gravity.CENTER
                setTextColor(if (chosen) white else muted)
            }, LinearLayout.LayoutParams(-1, dp(26)))
            item.addView(TextView(this).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(if (chosen) white else muted)
                if (chosen) setTypeface(null, Typeface.BOLD)
            })
            navBar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun outlinedButton(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(2), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
