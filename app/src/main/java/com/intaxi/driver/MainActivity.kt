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
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
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
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
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
    private lateinit var onlineSpin: TextView
    private var onlineSpinnerAnimator: ObjectAnimator? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var gpsPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var map: MapLibreMap? = null
    private var mapReady = false
    private var online = false
    private var navRevealed = false
    private var selectedTab = "შეკვეთები"
    private var period = "დღე"
    private var touchY = 0f
    private var balanceVisible = false
    private var demoOrderActive = false
    private var demoTripStarted = false
    private var routeLine: Polyline? = null
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var navigationActive = false
    private var routeRequestActive = false
    private var navigationTargetLat = 0.0
    private var navigationTargetLon = 0.0
    private var navigationTargetLabel = ""
    private var routePoints = ArrayList<LatLng>()
    private var routeManeuvers = ArrayList<RouteManeuver>()
    private var maneuverIndex = 0
    private val announcedThresholds = mutableSetOf<Int>()
    private var deviationCount = 0
    private var lastRerouteAt = 0L
    private data class RouteManeuver(val lat: Double, val lon: Double, val instruction: String)
    private val navigationLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) { handleNavigationLocation(location) }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

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
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ka", "GE") }
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


    private fun tr(value: String): String {
        val language = prefs.getString("language", "ქართული") ?: "ქართული"
        if (language == "ქართული") return value
        val az = mapOf(
            "დღეს გამოიმუშავეთ" to "Bu gün qazandınız",
            "შეკვეთები" to "Sifarişlər", "ჩატები" to "Söhbətlər", "ფინანსები" to "Balans", "პროფილი" to "Profil",
            "ონლაინ" to "Onlayn", "ოფლაინ" to "Oflayn", "ხაზზე გასვლა" to "Xəttə çıx", "ხაზიდან გადასვლა" to "Xətdən çıx",
            "რომელი ტარიფებით იმუშაოთ?" to "Hansı tariflərlə işləyirsiniz?",
            "სტანდარტი" to "Standart", "კომფორტი" to "Komfort",
            "გაუქმება" to "Ləğv et", "შენახვა" to "Yadda saxla", "კარგი" to "Oldu",
            "ტარიფები" to "Tariflər", "მდებარეობის ნებართვა" to "Məkan icazəsi",
            "რეალური GPS-ის გამოსაყენებლად İNTAXİ-ს მდებარეობის ნებართვა სჭირდება." to "Real GPS-dən istifadə üçün İNTAXİ-yə məkan icazəsi lazımdır.",
            "ჩემი მდებარეობა" to "Mənim yerim", "მდებარეობა ჯერ ვერ განისაზღვრა. შეამოწმეთ GPS ტელეფონში." to "Məkan hələ müəyyən edilməyib. Telefonda GPS-i yoxlayın.",
            "მინიმუმ ერთი ტარიფი ჩართული უნდა იყოს." to "Ən azı bir tarif aktiv olmalıdır.",
            "GPS ვერ ჩაირთო. შეამოწმეთ ტელეფონის მდებარეობის პარამეტრები." to "GPS açıla bilmədi. Telefonun məkan ayarlarını yoxlayın.",
            "ბალანსი" to "Balans", "ხელმისაწვდომი ბალანსი" to "Mövcud balans",
            "რეალური ბალანსი ანგარიშის სისტემასთან დაკავშირების შემდეგ გამოჩნდება." to "Real balans hesab sistemi qoşulduqdan sonra görünəcək.",
            "მხარდაჭერის ჩატი" to "Dəstək söhbəti",
            "მხარდაჭერისა და მგზავრებთან მიმოწერა ჩაირთვება სერვერთან და მგზავრის აპლიკაციასთან დაკავშირების შემდეგ." to "Dəstək və sərnişinlərlə yazışma server və sərnişin tətbiqi qoşulduqdan sonra aktiv olacaq.",
            "რეალური შეტყობინებები და შეკვეთის ჩატი საჭიროებს სერვერს." to "Real mesajlar və sifariş söhbəti üçün server lazımdır.",
            "მძღოლის ანგარიში" to "Sürücü hesabı", "სახელი და გვარი\nარ არის მითითებული\n\nტელეფონი\nარ არის მითითებული" to "Ad və soyad\nGöstərilməyib\n\nTelefon\nGöstərilməyib",
            "ტარიფები" to "Tariflər", "ტარიფის ჩართვა / გამორთვა" to "Tarifi aktiv / deaktiv et",
            "პარამეტრები" to "Ayarlar", "აპლიკაციის პარამეტრები" to "Tətbiq ayarları", "ენა, ნავიგაცია, რუკა, ხმები და სხვა" to "Dil, naviqasiya, xəritə, səslər və digərləri",
            "ავტომობილი" to "Avtomobil", "ავტომობილის მონაცემები" to "Avtomobil məlumatları",
            "მონაცემების მართვა დაემატება მძღოლის ანგარიშის მოდულთან ერთად." to "Məlumatların idarəsi sürücü hesabı modulu ilə birlikdə əlavə ediləcək.",
            "აპლიკაციის შესახებ" to "Tətbiq haqqında", "მძღოლის აპლიკაცია İNTAXİ. ვერსია 0.3.0" to "İNTAXİ sürücü tətbiqi. Versiya 0.3.0",
            "გასვლა" to "Çıxış", "შესვლის სისტემა ჯერ არ არის დამატებული." to "Giriş sistemi hələ əlavə edilməyib.",
            "აპლიკაციის ენა" to "Tətbiqin dili", "ენა" to "Dil",
            "ნავიგაცია" to "Naviqasiya", "ნავიგაციის აპლიკაცია" to "Naviqasiya tətbiqi", "რუკა" to "Xəritə",
            "3D რუკა" to "3D xəritə", "რუკის დახრილი 3D ხედვა" to "Xəritənin maili 3D görünüşü",
            "რუკის ავტომატური ბრუნვა" to "Xəritənin avtomatik fırlanması", "მოძრაობის მიმართულების მიხედვით" to "Hərəkət istiqamətinə uyğun",
            "სიჩქარის გაფრთხილება" to "Sürət xəbərdarlığı", "სიჩქარის ზღვრის გადაჭერებისას" to "Sürət həddi keçildikdə",
            "სიჩქარის ზღვარი" to "Sürət həddi", "ხმები" to "Səslər", "შეკვეთის ხმა" to "Sifariş səsi",
            "ახალი შეკვეთის ხმოვანი სიგნალი" to "Yeni sifariş üçün səs siqnalı", "ხმოვანი გაფრთხილებები" to "Səsli xəbərdarlıqlar",
            "სიჩქარისა და სისტემის გაფრთხილებები" to "Sürət və sistem xəbərdarlıqları", "სხვა" to "Digər",
            "შეტყობინებები" to "Bildirişlər", "აპლიკაციის შეტყობინებები" to "Tətbiq bildirişləri",
            "GPS ნებართვა" to "GPS icazəsi", "ტელეფონის მდებარეობის პარამეტრები" to "Telefonun məkan ayarları",
            "პარამეტრები ამ ეტაპზე ინახება ამ ტელეფონში. ანგარიშთან სინქრონიზაცია დაემატება სერვერის ჩართვის შემდეგ." to "Ayarlar hazırda bu telefonda saxlanılır. Hesabla sinxronizasiya server qoşulduqdan sonra əlavə ediləcək.",
            "პროფილზე დაბრუნება" to "Profilə qayıt", "ჩართულია" to "Aktivdir", "გამორთულია" to "Deaktivdir"
        )
        val ru = mapOf(
            "დღეს გამოიმუშავეთ" to "Заработано сегодня",
            "შეკვეთები" to "Заказы", "ჩატები" to "Чаты", "ფინანსები" to "Баланс", "პროფილი" to "Профиль",
            "ონლაინ" to "Онлайн", "ოფლაინ" to "Офлайн", "ხაზზე გასვლა" to "Выйти на линию", "ხაზიდან გადასვლა" to "Завершить смену",
            "რომელი ტარიფებით იმუშაოთ?" to "С какими тарифами работать?",
            "სტანდარტი" to "Стандарт", "კომფორტი" to "Комфорт",
            "გაუქმება" to "Отмена", "შენახვა" to "Сохранить", "კარგი" to "Хорошо",
            "ტარიფები" to "Тарифы", "მდებარეობის ნებართვა" to "Разрешение на геолокацию",
            "რეალური GPS-ის გამოსაყენებლად İNTAXİ-ს მდებარეობის ნებართვა სჭირდება." to "Для работы GPS приложению İNTAXİ нужен доступ к местоположению.",
            "ჩემი მდებარეობა" to "Моё местоположение", "მდებარეობა ჯერ ვერ განისაზღვრა. შეამოწმეთ GPS ტელეფონში." to "Местоположение пока не определено. Проверьте GPS на телефоне.",
            "მინიმუმ ერთი ტარიფი ჩართული უნდა იყოს." to "Должен быть включён хотя бы один тариф.",
            "GPS ვერ ჩაირთო. შეამოწმეთ ტელეფონის მდებარეობის პარამეტრები." to "Не удалось включить GPS. Проверьте настройки геолокации телефона.",
            "ბალანსი" to "Баланс", "ხელმისაწვდომი ბალანსი" to "Доступный баланс",
            "რეალური ბალანსი ანგარიშის სისტემასთან დაკავშირების შემდეგ გამოჩნდება." to "Реальный баланс появится после подключения системы аккаунтов.",
            "მხარდაჭერის ჩატი" to "Чат поддержки",
            "მხარდაჭერისა და მგზავრებთან მიმოწერა ჩაირთვება სერვერთან და მგზავრის აპლიკაციასთან დაკავშირების შემდეგ." to "Поддержка и переписка с пассажирами заработают после подключения сервера и приложения пассажира.",
            "რეალური შეტყობინებები და შეკვეთის ჩატი საჭიროებს სერვერს." to "Для сообщений и чата заказа необходим сервер.",
            "მძღოლის ანგარიში" to "Аккаунт водителя", "სახელი და გვარი\nარ არის მითითებული\n\nტელეფონი\nარ არის მითითებული" to "Имя и фамилия\nНе указаны\n\nТелефон\nНе указан",
            "ტარიფის ჩართვა / გამორთვა" to "Включить / выключить тариф",
            "პარამეტრები" to "Настройки", "აპლიკაციის პარამეტრები" to "Настройки приложения", "ენა, ნავიგაცია, რუკა, ხმები და სხვა" to "Язык, навигация, карта, звуки и другое",
            "ავტომობილი" to "Автомобиль", "ავტომობილის მონაცემები" to "Данные автомобиля",
            "მონაცემების მართვა დაემატება მძღოლის ანგარიშის მოდულთან ერთად." to "Управление данными появится вместе с модулем аккаунта водителя.",
            "აპლიკაციის შესახებ" to "О приложении", "მძღოლის აპლიკაცია İNTAXİ. ვერსია 0.3.0" to "Приложение водителя İNTAXİ. Версия 0.3.0",
            "გასვლა" to "Выйти", "შესვლის სისტემა ჯერ არ არის დამატებული." to "Система входа ещё не добавлена.",
            "აპლიკაციის ენა" to "Язык приложения", "ენა" to "Язык",
            "ნავიგაცია" to "Навигация", "ნავიგაციის აპლიკაცია" to "Навигационное приложение", "რუკა" to "Карта",
            "3D რუკა" to "3D-карта", "რუკის დახრილი 3D ხედვა" to "Наклонный 3D-вид карты",
            "რუკის ავტომატური ბრუნვა" to "Автоматический поворот карты", "მოძრაობის მიმართულების მიხედვით" to "По направлению движения",
            "სიჩქარის გაფრთხილება" to "Предупреждение о скорости", "სიჩქარის ზღვარი" to "Ограничение скорости",
            "ხმები" to "Звуки", "შეკვეთის ხმა" to "Звук заказа", "ახალი შეკვეთის ხმოვანი სიგნალი" to "Звуковой сигнал нового заказа",
            "ხმოვანი გაფრთხილებები" to "Голосовые предупреждения", "სიჩქარისა და სისტემის გაფრთხილებები" to "Предупреждения о скорости и системе",
            "სხვა" to "Другое", "შეტყობინებები" to "Уведомления", "აპლიკაციის შეტყობინებები" to "Уведомления приложения",
            "GPS ნებართვა" to "Разрешение GPS", "ტელეფონის მდებარეობის პარამეტრები" to "Настройки геолокации телефона",
            "პარამეტრები ამ ეტაპზე ინახება ამ ტელეფონში. ანგარიშთან სინქრონიზაცია დაემატება სერვერის ჩართვის შემდეგ." to "Сейчас настройки сохраняются на этом телефоне. Синхронизация с аккаунтом появится после запуска сервера.",
            "პროფილზე დაბრუნება" to "Вернуться в профиль", "ჩართულია" to "Включено", "გამორთულია" to "Выключено"
        )
        val en = mapOf(
            "დღეს გამოიმუშავეთ" to "Earned today",
            "შეკვეთები" to "Orders", "ჩატები" to "Chats", "ფინანსები" to "Balance", "პროფილი" to "Profile",
            "ონლაინ" to "Online", "ოფლაინ" to "Offline", "ხაზზე გასვლა" to "Go online", "ხაზიდან გადასვლა" to "Go offline",
            "რომელი ტარიფებით იმუშაოთ?" to "Which tariffs do you want to use?",
            "სტანდარტი" to "Standard", "კომფორტი" to "Comfort",
            "გაუქმება" to "Cancel", "შენახვა" to "Save", "კარგი" to "OK",
            "ტარიფები" to "Tariffs", "მდებარეობის ნებართვა" to "Location permission",
            "რეალური GPS-ის გამოსაყენებლად İNTAXİ-ს მდებარეობის ნებართვა სჭირდება." to "İNTAXİ needs location permission to use real GPS.",
            "ჩემი მდებარეობა" to "My location", "მდებარეობა ჯერ ვერ განისაზღვრა. შეამოწმეთ GPS ტელეფონში." to "Location is not available yet. Check your phone's GPS.",
            "მინიმუმ ერთი ტარიფი ჩართული უნდა იყოს." to "At least one tariff must be enabled.",
            "GPS ვერ ჩაირთო. შეამოწმეთ ტელეფონის მდებარეობის პარამეტრები." to "GPS could not be enabled. Check your phone's location settings.",
            "ბალანსი" to "Balance", "ხელმისაწვდომი ბალანსი" to "Available balance",
            "რეალური ბალანსი ანგარიშის სისტემასთან დაკავშირების შემდეგ გამოჩნდება." to "The actual balance will appear after the account system is connected.",
            "მხარდაჭერის ჩატი" to "Support chat",
            "მხარდაჭერისა და მგზავრებთან მიმოწერა ჩაირთვება სერვერთან და მგზავრის აპლიკაციასთან დაკავშირების შემდეგ." to "Support and passenger messaging will be enabled after the server and passenger app are connected.",
            "რეალური შეტყობინებები და შეკვეთის ჩატი საჭიროებს სერვერს." to "Live messages and order chat require a server.",
            "მძღოლის ანგარიში" to "Driver account", "სახელი და გვარი\nარ არის მითითებული\n\nტელეფონი\nარ არის მითითებული" to "Name and surname\nNot provided\n\nPhone\nNot provided",
            "ტარიფის ჩართვა / გამორთვა" to "Enable / disable tariff",
            "პარამეტრები" to "Settings", "აპლიკაციის პარამეტრები" to "App settings", "ენა, ნავიგაცია, რუკა, ხმები და სხვა" to "Language, navigation, map, sounds and more",
            "ავტომობილი" to "Vehicle", "ავტომობილის მონაცემები" to "Vehicle details",
            "მონაცემების მართვა დაემატება მძღოლის ანგარიშის მოდულთან ერთად." to "Vehicle management will be added with the driver account module.",
            "აპლიკაციის შესახებ" to "About app", "მძღოლის აპლიკაცია İNTAXİ. ვერსია 0.3.0" to "İNTAXİ Driver app. Version 0.3.0",
            "გასვლა" to "Log out", "შესვლის სისტემა ჯერ არ არის დამატებული." to "Login has not been implemented yet.",
            "აპლიკაციის ენა" to "App language", "ენა" to "Language",
            "ნავიგაცია" to "Navigation", "ნავიგაციის აპლიკაცია" to "Navigation app", "რუკა" to "Map",
            "3D რუკა" to "3D map", "რუკის დახრილი 3D ხედვა" to "Tilted 3D map view",
            "რუკის ავტომატური ბრუნვა" to "Automatic map rotation", "მოძრაობის მიმართულების მიხედვით" to "Based on direction of travel",
            "სიჩქარის გაფრთხილება" to "Speed warning", "სიჩქარის ზღვარი" to "Speed limit",
            "ხმები" to "Sounds", "შეკვეთის ხმა" to "Order sound", "ახალი შეკვეთის ხმოვანი სიგნალი" to "New order sound alert",
            "ხმოვანი გაფრთხილებები" to "Voice alerts", "სიჩქარისა და სისტემის გაფრთხილებები" to "Speed and system alerts",
            "სხვა" to "Other", "შეტყობინებები" to "Notifications", "აპლიკაციის შეტყობინებები" to "App notifications",
            "GPS ნებართვა" to "GPS permission", "ტელეფონის მდებარეობის პარამეტრები" to "Phone location settings",
            "პარამეტრები ამ ეტაპზე ინახება ამ ტელეფონში. ანგარიშთან სინქრონიზაცია დაემატება სერვერის ჩართვის შემდეგ." to "Settings are currently saved on this phone. Account sync will be added when the server is connected.",
            "პროფილზე დაბრუნება" to "Back to profile", "ჩართულია" to "Enabled", "გამორთულია" to "Disabled"
        )
        return when (language) {
            "Azərbaycan dili" -> az[value] ?: value
            "Русский" -> ru[value] ?: value
            "English" -> en[value] ?: value
            else -> value
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val balanceHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(0), dp(12), dp(5))
            setBackgroundColor(Color.argb(77, 32, 32, 36))
        }
        val balanceInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { showTab("ფინანსები") }
        }
        balanceInfo.addView(TextView(this).apply {
            text = tr("დღეს გამოიმუშავეთ")
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })
        val balanceAmount = TextView(this).apply {
            text = if (balanceVisible) "— ₾" else "••••"
            textSize = 21f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val amountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(balanceAmount)
            addView(TextView(this@MainActivity).apply {
                text = if (balanceVisible) "◉" else "⊙"
                textSize = 20f
                setTextColor(white)
                gravity = Gravity.CENTER
                setPadding(dp(9), 0, dp(9), 0)
                isClickable = true
                setOnClickListener {
                    balanceVisible = !balanceVisible
                    balanceAmount.text = if (balanceVisible) "— ₾" else "••••"
                    text = if (balanceVisible) "◉" else "⊙"
                }
            })
        }
        balanceInfo.addView(amountRow)
        balanceHeader.addView(balanceInfo, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(balanceHeader, LinearLayout.LayoutParams(-1, dp(66)).apply { topMargin = dp(38) })
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, dp(20))
            setBackgroundColor(Color.BLACK)
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
        navBar.visibility = View.VISIBLE
    }

    private fun ordersScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(3), dp(7), dp(5))
        }
        val mapFrame = FrameLayout(this)
        (mapView.parent as? ViewGroup)?.removeView(mapView)
        mapFrame.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val mapTools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(5))
        }
        mapTools.addView(makeGpsButton(), LinearLayout.LayoutParams(dp(52), dp(52)))
        val tariffQuick = TextView(this).apply {
            text = "☷"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(29, 30, 35))
                setStroke(dp(2), red)
            }
            elevation = dp(7).toFloat()
            contentDescription = tr("ტარიფები")
            setOnClickListener { showQuickTariffs() }
        }
        mapTools.addView(tariffQuick, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            topMargin = dp(9)
        })
        mapFrame.addView(mapTools, FrameLayout.LayoutParams(dp(66), -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(105)
            marginEnd = dp(3)
        })

        onlinePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            elevation = dp(6).toFloat()
            isClickable = true
            setOnClickListener {
                toggleOnline()
                if (online) {
                    visibility = View.GONE
                    showDemoOrderCard(mapFrame)
                }
            }
        }
        val mainStateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        onlineSpin = TextView(this).apply {
            text = "⟳"; gravity = Gravity.CENTER; textSize = 20f; setTextColor(white)
        }
        onlineStatus = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 19f; setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        onlineAction = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 12f; setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }
        mainStateRow.addView(onlineStatus, LinearLayout.LayoutParams(-2, dp(29)))
        mainStateRow.addView(onlineSpin, LinearLayout.LayoutParams(dp(27), dp(29)))
        onlinePanel.addView(mainStateRow, LinearLayout.LayoutParams(-1, dp(29)))
        onlinePanel.addView(onlineAction, LinearLayout.LayoutParams(-1, dp(20)))
        mapFrame.addView(onlinePanel, FrameLayout.LayoutParams(
            dp(260), dp(62), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(18) })

        page.addView(mapFrame, LinearLayout.LayoutParams(-1, -1))
        content.addView(page, FrameLayout.LayoutParams(-1, -1))
        refreshOnlineUi()
    }

    private fun showDemoOrderCard(mapFrame: FrameLayout) {
        if (demoOrderActive || demoTripStarted) return
        demoOrderActive = true

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(11), dp(15), dp(13))
            background = rounded(Color.rgb(24, 24, 28), 18)
            elevation = dp(14).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "ახალი შეკვეთა"
            textSize = 16f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "სატესტო"
            textSize = 11f
            setTextColor(Color.rgb(255, 205, 120))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rounded(card, 8)
        })
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 21f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener {
                mapFrame.removeView(cardView)
                demoOrderActive = false
                onlinePanel.visibility = View.VISIBLE
                refreshOnlineUi()
            }
        })
        cardView.addView(header)

        val fareRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(9))
        }
        fareRow.addView(TextView(this).apply {
            text = "— ₾"
            textSize = 23f
            setTextColor(Color.rgb(95, 220, 145))
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        fareRow.addView(TextView(this).apply {
            text = "👤  გიორგი  ★ 4.9"
            textSize = 13f
            setTextColor(white)
            gravity = Gravity.END
        })
        cardView.addView(fareRow)

        val routeInfo = TextView(this).apply {
            text = "●  მარნეულის ცენტრალური მოედანი\n   მარშრუტი ითვლება…\n\n◎  თბილისი, ორთაჭალა"
            textSize = 14f
            setTextColor(white)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(3), 0, dp(12))
            tag = "intaxi_demo_route_info"
        }
        cardView.addView(routeInfo)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(Button(this).apply {
            text = "გამოტოვება"
            isAllCaps = false
            textSize = 13f
            setTextColor(white)
            background = rounded(card, 10)
            setOnClickListener {
                mapFrame.removeView(cardView)
                demoOrderActive = false
                onlinePanel.visibility = View.VISIBLE
                refreshOnlineUi()
            }
        }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { marginEnd = dp(6) })
        actions.addView(Button(this).apply {
            text = "მიღება  ·  სტანდარტი"
            isAllCaps = false
            textSize = 13f
            setTextColor(white)
            background = rounded(red, 10)
            setOnClickListener {
                mapFrame.removeView(cardView)
                demoOrderActive = false
                demoTripStarted = false
                onlinePanel.visibility = View.GONE
                routeToFrom(41.4755, 44.8085, 41.6764, 44.8295, "თბილისი, ორთაჭალა")
                showTripControls(mapFrame, false)
            }
        }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { marginStart = dp(6) })
        cardView.addView(actions)

        mapFrame.addView(cardView, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(8), 0, dp(8), dp(8))
        })

        Thread {
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/44.8085,41.4755;44.8295,41.6764?overview=false")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 12000
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                val route = json.getJSONArray("routes").getJSONObject(0)
                val km = route.getDouble("distance") / 1000.0
                val minutes = (route.getDouble("duration") / 60.0).toInt()
                runOnUiThread {
                    routeInfo.text = "●  მარნეულის ცენტრალური მოედანი\n   %.1f კმ  ·  დაახლოებით %d წთ\n\n◎  თბილისი, ორთაჭალა".format(Locale.US, km, minutes)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    routeInfo.text = "●  მარნეულის ცენტრალური მოედანი\n   მანძილი ვერ გამოითვალა — ინტერნეტი შეამოწმეთ\n\n◎  თბილისი, ორთაჭალა"
                }
            }
        }.start()
    }

    private fun routeToFrom(fromLat: Double, fromLon: Double, destinationLat: Double, destinationLon: Double, label: String) {
        if (routeRequestActive) return
        navigationTargetLat = destinationLat
        navigationTargetLon = destinationLon
        navigationTargetLabel = label
        routeRequestActive = true
        Thread {
            var error = "მარშრუტის გამოთვლა ვერ მოხერხდა."
            val points = ArrayList<LatLng>()
            val maneuvers = ArrayList<RouteManeuver>()
            var summary = ""
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/$fromLon,$fromLat;$destinationLon,$destinationLat?overview=full&geometries=geojson&steps=true")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                if (json.optString("code") != "Ok") throw IllegalStateException("OSRM route error")
                val route = json.getJSONArray("routes").getJSONObject(0)
                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                for (i in 0 until coords.length()) {
                    val pair = coords.getJSONArray(i)
                    points.add(LatLng(pair.getDouble(1), pair.getDouble(0)))
                }
                val steps = route.optJSONArray("legs")?.optJSONObject(0)?.optJSONArray("steps")
                if (steps != null) for (i in 0 until steps.length()) {
                    val step = steps.getJSONObject(i)
                    val maneuver = step.optJSONObject("maneuver") ?: continue
                    val type = maneuver.optString("type")
                    if (type == "depart" || type == "arrive" || type.isBlank()) continue
                    val location = maneuver.optJSONArray("location") ?: continue
                    if (location.length() < 2) continue
                    maneuvers.add(RouteManeuver(location.getDouble(1), location.getDouble(0),
                        maneuverPhrase(type, maneuver.optString("modifier"), step.optString("name").takeIf { it.isNotBlank() })))
                }
                summary = "$label · %.1f km · %.0f წთ".format(Locale.US, route.getDouble("distance") / 1000.0, route.getDouble("duration") / 60.0)
                error = ""
            } catch (_: Exception) {
                error = "მარშრუტის გამოთვლა ვერ მოხერხდა. შეამოწმეთ ინტერნეტი და სცადეთ თავიდან."
            }
            runOnUiThread {
                routeRequestActive = false
                if (points.isNotEmpty()) {
                    routeLine?.let { try { map?.removePolyline(it) } catch (_: Exception) { } }
                    routeLine = map?.addPolyline(PolylineOptions().addAll(points).color(Color.rgb(190, 40, 65)).width(6f))
                    routePoints = points
                    routeManeuvers = maneuvers
                    maneuverIndex = 0
                    announcedThresholds.clear()
                    deviationCount = 0
                    navigationActive = true
                    startNavigationUpdates()
                    map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(fromLat, fromLon), 11.5))
                    speakNavigation(summary)
                } else {
                    notice("ნავიგაცია", error + "\nსატესტო მარშრუტი ინტერნეტს საჭიროებს.")
                }
            }
        }.start()
    }

    private fun showTripControls(mapFrame: FrameLayout, passengerPicked: Boolean) {
        mapFrame.findViewWithTag<View>("intaxi_trip_controls")?.let { mapFrame.removeView(it) }
        val panelView = LinearLayout(this).apply {
            tag = "intaxi_trip_controls"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = rounded(Color.rgb(24, 24, 28), 16)
            elevation = dp(10).toFloat()
        }
        panelView.addView(TextView(this).apply {
            text = if (passengerPicked) "მიმდინარეობს მგზავრობა · 8.5 კმ" else "მგზავრამდე · 1.2 კმ"
            textSize = 15f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        })
        panelView.addView(Button(this).apply {
            text = if (passengerPicked) "სატესტო მგზავრობის დასრულება" else "სატესტო მგზავრი ავიყვანე"
            isAllCaps = false; setTextColor(white); background = rounded(red, 10)
            setOnClickListener {
                if (!passengerPicked) {
                    routeTo(41.7025, 44.8015, "დანიშნულების ადგილი")
                    showTripControls(mapFrame, true)
                } else {
                    mapFrame.findViewWithTag<View>("intaxi_trip_controls")?.let { mapFrame.removeView(it) }
                    showTripCompleted()
                }
            }
        })
        mapFrame.addView(panelView, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(198))
        })
    }

    private fun currentLocation(): Location? {
        if (!hasGpsPermission()) return null
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.getProviders(true).asSequence().mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
        } catch (_: Exception) { null }
    }

    private fun routeTo(destinationLat: Double, destinationLon: Double, label: String) {
        if (routeRequestActive) return
        val origin = currentLocation()
        if (origin == null) {
            notice("GPS", "მარშრუტისთვის ჯერ GPS მდებარეობა ვერ განისაზღვრა. ჩართეთ მდებარეობა და სცადეთ ხელახლა.")
            return
        }
        navigationTargetLat = destinationLat
        navigationTargetLon = destinationLon
        navigationTargetLabel = label
        routeRequestActive = true
        val fromLat = origin.latitude
        val fromLon = origin.longitude
        Thread {
            var error = "მარშრუტის გამოთვლა ვერ მოხერხდა."
            val points = ArrayList<LatLng>()
            val maneuvers = ArrayList<RouteManeuver>()
            var summary = ""
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/$fromLon,$fromLat;$destinationLon,$destinationLat?overview=full&geometries=geojson&steps=true")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                if (json.optString("code") != "Ok") throw IllegalStateException("OSRM route error")
                val route = json.getJSONArray("routes").getJSONObject(0)
                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                for (i in 0 until coords.length()) {
                    val pair = coords.getJSONArray(i)
                    points.add(LatLng(pair.getDouble(1), pair.getDouble(0)))
                }
                val legs = route.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val steps = legs.getJSONObject(0).optJSONArray("steps")
                    if (steps != null) {
                        for (i in 0 until steps.length()) {
                            val step = steps.getJSONObject(i)
                            val maneuver = step.optJSONObject("maneuver") ?: continue
                            val type = maneuver.optString("type")
                            if (type == "depart" || type == "arrive" || type.isBlank()) continue
                            val location = maneuver.optJSONArray("location") ?: continue
                            if (location.length() < 2) continue
                            val modifier = maneuver.optString("modifier")
                            val street = step.optString("name").takeIf { it.isNotBlank() }
                            maneuvers.add(RouteManeuver(
                                location.getDouble(1),
                                location.getDouble(0),
                                maneuverPhrase(type, modifier, street)
                            ))
                        }
                    }
                }
                val km = route.getDouble("distance") / 1000.0
                val mins = route.getDouble("duration") / 60.0
                summary = "$label · %.1f km · %.0f წთ".format(Locale.US, km, mins)
                error = ""
            } catch (_: Exception) {
                error = "მარშრუტის გამოთვლა ვერ მოხერხდა. შეამოწმეთ ინტერნეტი და სცადეთ თავიდან."
            }
            runOnUiThread {
                routeRequestActive = false
                if (points.isNotEmpty()) {
                    routeLine?.let { try { map?.removePolyline(it) } catch (_: Exception) { } }
                    routeLine = map?.addPolyline(PolylineOptions().addAll(points).color(Color.rgb(190, 40, 65)).width(6f))
                    routePoints = points
                    routeManeuvers = maneuvers
                    maneuverIndex = 0
                    announcedThresholds.clear()
                    deviationCount = 0
                    navigationActive = true
                    startNavigationUpdates()
                    map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        LatLng(fromLat, fromLon), 16.0
                    ))
                    speakNavigation(summary)
                } else {
                    notice("ნავიგაცია", error + "\nსატესტო მარშრუტი ინტერნეტს საჭიროებს.")
                }
            }
        }.start()
    }

    private fun maneuverPhrase(type: String, modifier: String, street: String?): String {
        val language = prefs.getString("language", "ქართული") ?: "ქართული"
        val direction = when (modifier) {
            "left", "slight left", "sharp left" -> when (language) {
                "Azərbaycan dili" -> "sola"
                "Русский" -> "налево"
                "English" -> "left"
                else -> "მარცხნივ"
            }
            "right", "slight right", "sharp right" -> when (language) {
                "Azərbaycan dili" -> "sağa"
                "Русский" -> "направо"
                "English" -> "right"
                else -> "მარჯვნივ"
            }
            "uturn" -> when (language) {
                "Azərbaycan dili" -> "geri dönün"
                "Русский" -> "развернитесь"
                "English" -> "make a U-turn"
                else -> "მობრუნდით"
            }
            else -> when (language) {
                "Azərbaycan dili" -> "პირდაპირ გააგრძელეთ"
                "Русский" -> "продолжайте прямо"
                "English" -> "continue straight"
                else -> "გააგრძელეთ პირდაპირ"
            }
        }
        val action = when (type) {
            "roundabout", "rotary" -> when (language) {
                "Azərbaycan dili" -> "dairəvi მოძრაობაში $direction çıxın"
                "Русский" -> "на круговом движении сверните $direction"
                "English" -> "at the roundabout, take the exit $direction"
                else -> "წრიულ მოძრაობაზე გადადით $direction"
            }
            "merge" -> when (language) {
                "Azərbaycan dili" -> "$direction birləşin"
                "Русский" -> "перестройтесь $direction"
                "English" -> "merge $direction"
                else -> "შეუერთდით $direction"
            }
            "fork" -> when (language) {
                "Azərbaycan dili" -> "ayrıcda $direction saxlayın"
                "Русский" -> "на развилке держитесь $direction"
                "English" -> "keep $direction at the fork"
                else -> "განშტოებაზე დაიჭირეთ $direction"
            }
            else -> when (language) {
                "Azərbaycan dili" -> "$direction dönün"
                "Русский" -> "поверните $direction"
                "English" -> "turn $direction"
                else -> "მოუხვიეთ $direction"
            }
        }
        val streetText = street?.let {
            when (language) {
                "Azərbaycan dili" -> " ქუჩაზე $it"
                "Русский" -> " на улицу $it"
                "English" -> " onto $it"
                else -> " ქუჩაზე $it"
            }
        } ?: ""
        return action + streetText
    }

    private fun speakNavigation(message: String) {
        val language = prefs.getString("language", "ქართული") ?: "ქართული"
        val locale = when (language) {
            "Azərbaycan dili" -> Locale("az", "AZ")
            "Русский" -> Locale("ru", "RU")
            "English" -> Locale.US
            else -> Locale("ka", "GE")
        }
        try {
            tts?.language = locale
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "intaxi_navigation")
        } catch (_: Exception) { }
    }

    private fun startNavigationUpdates() {
        if (!hasGpsPermission()) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = manager
        navigationActive = true
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 1000L, 2f, navigationLocationListener, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) {
            notice("GPS", "ნავიგაციისთვის მდებარეობის ნებართვა საჭიროა.")
        } catch (_: Exception) { }
    }

    private fun stopNavigationUpdates() {
        navigationActive = false
        try { locationManager?.removeUpdates(navigationLocationListener) } catch (_: Exception) { }
        locationManager = null
        routeRequestActive = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun handleNavigationLocation(location: Location) {
        if (!navigationActive) return
        val current = LatLng(location.latitude, location.longitude)
        val bearing = if (location.hasBearing()) location.bearing.toDouble() else map?.cameraPosition?.bearing ?: 0.0
        if (prefs.getBoolean("map_rotate", true)) {
            val camera = CameraPosition.Builder()
                .target(current).zoom(17.0).tilt(if (prefs.getBoolean("map_3d", false)) 45.0 else 0.0)
                .bearing(bearing).build()
            map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(camera), 700)
        } else {
            map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(current), 500)
        }

        if (maneuverIndex < routeManeuvers.size) {
            val maneuver = routeManeuvers[maneuverIndex]
            val distance = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, maneuver.lat, maneuver.lon, distance)
            val meters = distance[0].toInt()
            if (meters <= 25) {
                maneuverIndex++
                announcedThresholds.clear()
            } else {
                val thresholds = listOf(500, 150, 40)
                val nextThreshold = thresholds.firstOrNull { meters <= it && !announcedThresholds.contains(it) }
                if (nextThreshold != null) {
                    announcedThresholds.add(nextThreshold)
                    val instruction = when (prefs.getString("language", "ქართული")) {
                        "Azərbaycan dili" -> when (nextThreshold) {
                            500 -> "500 metr sonra "
                            150 -> "150 metr sonra "
                            else -> "İndi "
                        }
                        "Русский" -> when (nextThreshold) {
                            500 -> "Через 500 метров "
                            150 -> "Через 150 метров "
                            else -> "Сейчас "
                        }
                        "English" -> when (nextThreshold) {
                            500 -> "In 500 meters, "
                            150 -> "In 150 meters, "
                            else -> "Now, "
                        }
                        else -> when (nextThreshold) {
                            500 -> "500 მეტრში "
                            150 -> "150 მეტრში "
                            else -> "ახლა "
                        }
                    } + maneuver.instruction
                    speakNavigation(instruction)
                }
            }
        }

        if (routePoints.isNotEmpty() && !routeRequestActive) {
            var nearest = Float.MAX_VALUE
            val sampleStep = (routePoints.size / 250).coerceAtLeast(1)
            for (i in routePoints.indices step sampleStep) {
                val point = routePoints[i]
                val distance = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, distance)
                if (distance[0] < nearest) nearest = distance[0]
            }
            if (nearest > 90f && location.accuracy <= 60f) deviationCount++ else deviationCount = 0
            val now = System.currentTimeMillis()
            if (deviationCount >= 3 && now - lastRerouteAt > 15000L) {
                deviationCount = 0
                lastRerouteAt = now
                speakNavigation(when (prefs.getString("language", "ქართული")) {
                    "Azərbaycan dili" -> "Marşrutdan kənara çıxdınız. Yeni marşrut hesablanır."
                    "Русский" -> "Вы отклонились от маршрута. Перестраиваю маршрут."
                    "English" -> "You are off route. Recalculating."
                    else -> "მარშრუტიდან გადაუხვიეთ. ვითვლი ახალ მარშრუტს."
                })
                routeTo(navigationTargetLat, navigationTargetLon, navigationTargetLabel)
            }
        }
    }

    private fun showTripCompleted() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            background = rounded(Color.rgb(25, 25, 29), 16)
        }
        box.addView(TextView(this).apply {
            text = "მგზავრობა დასრულდა"
            textSize = 19f; setTextColor(white); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = "15.00 ₾"
            textSize = 32f; setTextColor(white); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })
        box.addView(TextView(this).apply {
            text = "თქვენი შემოსავალი: 13.50 ₾\nგადახდა: ნაღდი"
            textSize = 15f; setTextColor(Color.rgb(95, 220, 145)); gravity = Gravity.CENTER
        })
        AlertDialog.Builder(this).setView(box).setPositiveButton("მგზავრის შეფასება") { _, _ ->
            showPassengerRating()
        }.setOnCancelListener { resetDemoTrip() }.show()
    }

    private fun showPassengerRating() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
        }
        layout.addView(TextView(this).apply { text = "შეაფასეთ მგზავრი"; textSize = 18f; setTextColor(white) })
        var rating = 5
        val stars = TextView(this).apply {
            text = "★ ★ ★ ★ ★"; textSize = 28f; setTextColor(Color.rgb(255, 190, 50)); gravity = Gravity.CENTER
            setOnClickListener { rating = if (rating == 5) 4 else rating + 1; text = (1..5).joinToString(" ") { if (it <= rating) "★" else "☆" } }
        }
        layout.addView(stars)
        val comment = android.widget.EditText(this).apply {
            hint = "კომენტარი (არასავალდებულო)"
            setTextColor(white); setHintTextColor(muted); setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        layout.addView(comment)
        AlertDialog.Builder(this).setView(layout).setPositiveButton("შეფასების შენახვა") { _, _ ->
            resetDemoTrip()
        }.setNegativeButton("გამოტოვება") { _, _ -> resetDemoTrip() }.show()
    }

    private fun resetDemoTrip() {
        stopNavigationUpdates()
        routePoints.clear()
        routeManeuvers.clear()
        maneuverIndex = 0
        announcedThresholds.clear()
        routeLine?.let { try { map?.removePolyline(it) } catch (_: Exception) { } }
        routeLine = null
        demoTripStarted = false
        tts?.stop()
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
        if (::onlinePanel.isInitialized) onlinePanel.visibility = View.VISIBLE
        navBar.visibility = View.VISIBLE
        refreshOnlineUi()
    }

    private fun refreshOnlineUi() {
        if (!::onlineStatus.isInitialized) return
        onlineStatus.text = tr(if (online) "ონლაინ" else "ოფლაინ")
        onlineAction.text = tr(if (online) "ხაზიდან გადასვლა" else "ხაზზე გასვლა")
        val actionColor = if (online) Color.rgb(28, 145, 91) else red
        onlineAction.setTextColor(white)
        onlineStatus.setTextColor(white)
        onlineSpin.visibility = if (online) View.VISIBLE else View.GONE
        onlineSpin.setTextColor(white)
        onlinePanel.background = GradientDrawable().apply {
            setColor(actionColor)
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), actionColor)
        }
        onlineSpinnerAnimator?.cancel()
        onlineSpin.rotation = 0f
        if (online) {
            onlineSpinnerAnimator = ObjectAnimator.ofFloat(onlineSpin, "rotation", 0f, 360f).apply {
                duration = 1100
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = LinearInterpolator()
                start()
            }
        }
        if (::navBar.isInitialized) navBar.visibility = View.VISIBLE
    }

    private fun makeGpsButton(): View = TextView(this).apply {
        text = "⌖"
        textSize = 34f
        gravity = Gravity.CENTER
        setTextColor(white)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(29, 30, 35))
            setStroke(dp(2), Color.rgb(100, 29, 45))
        }
        elevation = dp(7).toFloat()
        contentDescription = tr("ჩემი მდებარეობა")
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

    private fun showQuickTariffs() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            setBackgroundColor(bg)
        }
        val dialogTitle = TextView(this).apply {
            text = tr("რომელი ტარიფებით იმუშაოთ?")
            textSize = 15f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        box.addView(dialogTitle)
        val standard = android.widget.CheckBox(this).apply {
            text = tr("სტანდარტი")
            textSize = 15f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(red)
            isChecked = prefs.getBoolean("tariff_სტანდარტი", true)
        }
        val comfort = android.widget.CheckBox(this).apply {
            text = tr("კომფორტი")
            textSize = 15f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(red)
            isChecked = prefs.getBoolean("tariff_კომფორტი", true)
        }
        box.addView(standard)
        box.addView(comfort)
        val dialog = AlertDialog.Builder(this)
            .setView(box)
            .setNegativeButton(tr("გაუქმება"), null)
            .setPositiveButton(tr("შენახვა"), null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(rounded(bg, 18))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(95, 220, 145))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(white)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!standard.isChecked && !comfort.isChecked) {
                    notice("ტარიფები", "მინიმუმ ერთი ტარიფი ჩართული უნდა იყოს.")
                    return@setOnClickListener
                }
                prefs.edit()
                    .putBoolean("tariff_სტანდარტი", standard.isChecked)
                    .putBoolean("tariff_კომფორტი", comfort.isChecked)
                    .apply()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun hasGpsPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableGps() {
        val m = map ?: return
        if (!mapReady || !hasGpsPermission()) return
        try {
            val locationStyle = LocationComponentOptions.builder(this)
                .foregroundDrawable(R.drawable.ic_intaxi_location_arrow)
                .foregroundDrawableStale(R.drawable.ic_intaxi_location_arrow)
                .accuracyAlpha(0f)
                .enableStaleState(false)
                .build()
            m.locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, m.style!!)
                    .locationComponentOptions(locationStyle)
                    .build()
            )
            m.locationComponent.isLocationComponentEnabled = true
            m.locationComponent.renderMode = RenderMode.GPS
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
        p.addView(title("ბალანსი"))
        p.addView(info(
            "ხელმისაწვდომი ბალანსი",
            "— ₾",
            "რეალური ბალანსი ანგარიშის სისტემასთან დაკავშირების შემდეგ გამოჩნდება."
        ))
        scroll(p)
    }

    private fun profileScreen() {
        val p = page()
        p.addView(title("პროფილი"))
        p.addView(info("მძღოლის ანგარიში", "სახელი და გვარი\nარ არის მითითებული\n\nტელეფონი\nარ არის მითითებული"))
        p.addView(title("ტარიფები"))
        listOf("სტანდარტი", "კომფორტი").forEach {
            switchRow(p, it, "ტარიფის ჩართვა / გამორთვა", "tariff_" + it, true, false)
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
            choose("ენა", listOf("ქართული", "Azərbaycan dili", "Русский", "English"), "language")
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
        AlertDialog.Builder(this).setTitle(tr(title))
            .setSingleChoiceItems(options.toTypedArray(), options.indexOf(current).coerceAtLeast(0)) { d, i ->
                if (numeric) prefs.edit().putInt(key, options[i].toInt()).apply()
                else prefs.edit().putString(key, options[i]).apply()
                d.dismiss()
                if (key == "language") recreate() else settingsScreen()
            }.setNegativeButton(tr("გაუქმება"), null).show()
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
            text = tr(name); textSize = 14f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            text = tr(sub); textSize = 11f; setTextColor(muted)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val state = TextView(this).apply {
            text = tr(if (prefs.getBoolean(key, default)) "ჩართულია" else "გამორთულია")
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
        text = tr(t); textSize = 20f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(10))
    }

    private fun info(t: String, body: String, foot: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = rounded(panel, 14)
        addView(TextView(this@MainActivity).apply {
            text = tr(t); textSize = 15f; setTextColor(white); setTypeface(null, Typeface.BOLD)
        })
        addView(TextView(this@MainActivity).apply {
            text = tr(body); textSize = 14f; setTextColor(if (body.contains("—")) muted else white)
            setPadding(0, dp(8), 0, 0)
        })
        if (foot != null) addView(TextView(this@MainActivity).apply {
            text = tr(foot); textSize = 11f; setTextColor(muted); setPadding(0, dp(5), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun note(t: String) = TextView(this).apply {
        text = tr(t); textSize = 12f; setTextColor(muted); setPadding(dp(3), dp(10), dp(3), dp(14))
    }

    private fun stat(t: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9), dp(4), dp(5), dp(4))
        background = rounded(card, 11)
        addView(TextView(this@MainActivity).apply {
            text = tr(t); textSize = 10f; setTextColor(muted); maxLines = 1
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
            text = tr(t); textSize = 14f; setTextColor(if (strong) white else muted)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = v; textSize = 15f
            setTextColor(if (strong) Color.rgb(95, 220, 145) else white)
            setTypeface(null, Typeface.BOLD)
        })
    }

    private fun button(t: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = tr(t); isAllCaps = false; textSize = 13f; setTextColor(white)
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
                    showTab(tab)
                }
            }
            item.addView(TextView(this).apply {
                text = symbol; textSize = 21f; gravity = Gravity.CENTER
                setTextColor(if (chosen) white else muted)
            }, LinearLayout.LayoutParams(-1, dp(26)))
            item.addView(TextView(this).apply {
                text = tr(label); textSize = 10f; gravity = Gravity.CENTER
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
    override fun onDestroy() { stopNavigationUpdates(); tts?.stop(); tts?.shutdown(); mapView.onDestroy(); super.onDestroy() }
}
