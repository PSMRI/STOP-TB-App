package org.piramalswasthya.stoptb.ui.volunteer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.piramalswasthya.stoptb.BuildConfig
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.ActivityVolunteerBinding
import org.piramalswasthya.stoptb.helpers.AutoFlowBackNavigationHost
import org.piramalswasthya.stoptb.helpers.ImageUtils
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.helpers.MyContextWrapper
import org.piramalswasthya.stoptb.helpers.TapjackingProtectionHelper
import org.piramalswasthya.stoptb.ui.abha_id_activity.AbhaIdActivity
import org.piramalswasthya.stoptb.ui.home_activity.sync.SyncBottomSheetFragment
import org.piramalswasthya.stoptb.ui.login_activity.LoginActivity
import org.piramalswasthya.stoptb.ui.service_location_activity.ServiceLocationActivity
import org.piramalswasthya.stoptb.ui.volunteer.fragment.VolunteerViewModel
import org.piramalswasthya.stoptb.work.WorkerUtils
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VolunteerActivity : AppCompatActivity(), AutoFlowBackNavigationHost {

    private var autoFlowBackNavigationBlocked: Boolean = false

    /** Mirrors the same listener in HomeActivity — see comments there. */
    private val campHubPrefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == pref.getCampHubConnectedKey()) {
                runOnUiThread { refreshCampHubOfflineBanner() }
            }
        }

    // ── WiFi / NetworkCallback ────────────────────────────────────────────────

    private var isNetworkCallbackRegistered = false

    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Detects WiFi loss/availability at OS level — fires instantly when WiFi
     * disconnects even if no HTTP request has been made to the camp hub.
     *
     * onLost  → mark camp hub disconnected + show banner immediately
     * onAvailable → ping camp hub; reconnect only if it responds
     */
    private val wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            // WiFi dropped — if camp mode was active, mark disconnected at once
            if (pref.isCampModeEnabled() && pref.isCampHubConnected()) {
                pref.setCampHubConnected(false)
                runOnUiThread { refreshCampHubOfflineBanner() }
            }
        }

        override fun onAvailable(network: Network) {
            // WiFi came back — only try to reconnect if we're not already connected
            if (!pref.isCampModeEnabled() || pref.isCampHubConnected()) return
            lifecycleScope.launch(Dispatchers.IO) {
                val reached = pingCampHub()
                if (pref.isCampModeEnabled()) {           // double-check after IO
                    pref.setCampHubConnected(reached)
                    runOnUiThread { refreshCampHubOfflineBanner() }
                }
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WrapperEntryPoint {
        val pref: PreferenceDao
    }

    @Inject
    lateinit var pref: PreferenceDao

    private var _binding: ActivityVolunteerBinding? = null
    private val binding: ActivityVolunteerBinding
        get() = _binding!!

    private val viewModel: VolunteerViewModel by viewModels()

    private val syncBottomSheet: SyncBottomSheetFragment by lazy {
        SyncBottomSheetFragment()
    }

    var lastClickTime: Long = 0L
    private val onClickTitleBar = android.view.View.OnClickListener {
        finishAndStartServiceLocationActivity()
    }

    private val logoutAlert by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.logout))
            .setMessage(resources.getString(R.string.are_you_sure_to_logout))
            .setPositiveButton(resources.getString(R.string.yes)) { dialog, _ ->
                viewModel.logout()
                ImageUtils.removeAllBenImages(this)
                WorkerUtils.cancelAllWork(this)
                dialog.dismiss()
            }
            .setNegativeButton(resources.getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
            }.create()
    }

    private val langChooseAlert by lazy {
        val currentLanguageIndex = when (pref.getCurrentLanguage()) {
            Languages.ENGLISH -> 0
            Languages.HINDI -> 1
            Languages.ASSAMESE -> 2
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.choose_application_language))
            .setSingleChoiceItems(
                arrayOf(
                    resources.getString(R.string.english),
                    resources.getString(R.string.hindi),
//                    resources.getString(R.string.assamese)
                ), currentLanguageIndex
            ) { di, checkedItemIndex ->
                val checkedLanguage = when (checkedItemIndex) {
                    0 -> Languages.ENGLISH
                    1 -> Languages.HINDI
//                    2 -> Languages.ASSAMESE
                    else -> throw IllegalStateException("Unknown language index $checkedItemIndex")
                }
                if (checkedItemIndex == currentLanguageIndex) {
                    di.dismiss()
                } else {
                    pref.saveSetLanguage(checkedLanguage)
                    val restart = Intent(this, VolunteerActivity::class.java)
                    finish()
                    startActivity(restart)
                }
            }.create()
    }

    private val navController by lazy {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_volunteer) as NavHostFragment
        navHostFragment.navController
    }
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun attachBaseContext(newBase: Context) {
        val pref = EntryPointAccessors.fromApplication(
            newBase, WrapperEntryPoint::class.java
        ).pref
        super.attachBaseContext(
            MyContextWrapper.wrap(
                newBase,
                newBase.applicationContext,
                pref.getCurrentLanguage().symbol
            )
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return if (TapjackingProtectionHelper.isTouchAllowed(this, ev)) {
            super.dispatchTouchEvent(ev)
        } else {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        TapjackingProtectionHelper.applyWindowSecurity(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityVolunteerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        TapjackingProtectionHelper.enableTouchFiltering(this)
        setUpActionBar()
        setUpNavHeader()
        setUpMenu()
        askForPermissions()
        binding.tvCampHubOffline.setOnClickListener {
            openCampHubConnect()
        }
        refreshCampHubOfflineBanner()

        binding.versionName.text = "App Version ${BuildConfig.VERSION_NAME}"


        viewModel.navigateToLoginPage.observe(this) {
            if (it) {
                startActivity(Intent(this, LoginActivity::class.java))
                viewModel.navigateToLoginPageComplete()
                finish()
            }
        }

        // Redirect to ServiceLocationActivity if location not set (multi-village users)
        if (pref.getLocationRecord() == null) {
            val intent = Intent(this, org.piramalswasthya.stoptb.ui.service_location_activity.ServiceLocationActivity::class.java)
            intent.putExtra("fromVolunteer", true)
            startActivity(intent)
            finish()
            return
        }

        // Auto-trigger sync on first launch (when full pull hasn't completed yet)
        if (!pref.isFullPullComplete) {
            WorkerUtils.triggerAmritPullWorker(this)
        }
        WorkerUtils.triggerCampQuickPullIfConnected(this, pref)
    }

    private fun askForPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        ActivityCompat.requestPermissions(
            this,
            permissions,
            1010
        )
    }

    private fun setUpMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_toolbar, menu)
                menu.findItem(R.id.toolbar_menu_home)?.isVisible = false
                menu.findItem(R.id.toolbar_menu_language)?.isVisible = true
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.toolbar_menu_language -> {
                        langChooseAlert.show()
                        return true
                    }
                    R.id.sync_status -> {
                        if (!syncBottomSheet.isVisible)
                            syncBottomSheet.show(
                                supportFragmentManager,
                                resources.getString(R.string.sync)
                            )
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setUpNavHeader() {
        val headerView = binding.navView.getHeaderView(0)
        viewModel.currentUser?.let {
            headerView.findViewById<TextView>(R.id.tv_nav_name).text =
                resources.getString(R.string.nav_item_1_text, it.name)
            headerView.findViewById<TextView>(R.id.tv_nav_role).text =
                resources.getString(R.string.nav_item_2_text, it.userName)

            val englishId = String.format(Locale.ENGLISH, "%s", it.userId)
            val formatted = HtmlCompat.fromHtml(
                getString(R.string.nav_item_3_text, englishId),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            headerView.findViewById<TextView>(R.id.tv_nav_id).text = formatted
        }
    }

    private fun setUpActionBar() {
        setSupportActionBar(binding.toolbar)
        binding.navView.setupWithNavController(navController)

        appBarConfiguration = AppBarConfiguration.Builder(
            setOf(R.id.volunteerHomeFragment)
        ).setOpenableLayout(binding.drawerLayout).build()

        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)


        // Fix toolbar title appearing beside back button
        navController.addOnDestinationChangedListener { _, _, _ ->
            binding.toolbar.title = ""
            supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        // ── Logout ──────────────────────────────────────────────────────
        binding.navView.menu.findItem(R.id.menu_logout)?.setOnMenuItemClickListener {
            logoutAlert.show()
            true
        }

        // ── Sync Records ─────────────────────────────────────────────────
        binding.navView.menu.findItem(R.id.sync_pending_records)?.setOnMenuItemClickListener {
            if (SystemClock.elapsedRealtime() - lastClickTime < 900000L) {
                Toast.makeText(this, "Please wait Syncing in Progress", Toast.LENGTH_SHORT).show()
                return@setOnMenuItemClickListener true
            }
            lastClickTime = SystemClock.elapsedRealtime()
            WorkerUtils.triggerAmritPushWorker(this)
            if (!pref.isFullPullComplete)
                WorkerUtils.triggerAmritPullWorker(this)
            binding.drawerLayout.close()
            true
        }

        binding.navView.menu.findItem(R.id.menu_connect_camp_hub)?.setOnMenuItemClickListener {
            binding.drawerLayout.close()
            openCampHubConnect()
            true
        }

        // ── Create ABHA ID ───────────────────────────────────────────────
        refreshCampHubDrawerItem()

        binding.navView.menu.findItem(R.id.abha_id_activity)?.setOnMenuItemClickListener {
            startActivity(Intent(this, AbhaIdActivity::class.java))
            binding.drawerLayout.close()
            true
        }

        // ── Support ──────────────────────────────────────────────────────
        binding.navView.menu.findItem(R.id.menu_support)?.setOnMenuItemClickListener {
            val url = "https://forms.office.com/r/AqY1KqAz3v"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            binding.drawerLayout.close()
            true
        }

        // ── Request to Delete Account ────────────────────────────────────
        binding.navView.menu.findItem(R.id.menu_delete_account)?.setOnMenuItemClickListener {
//            val url = "https://forms.office.com/Pages/ResponsePage.aspx?id=jQ49md0HKEGgbxRJvtPnRISY9UjAA01KtsFKYKhp1nNURUpKQzNJUkE1OUc0SllXQ0IzRFVJNlM2SC4u"
            val url = "https://forms.cloud.microsoft/r/iVtay7Kf6V"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            binding.drawerLayout.close()
            true
        }
    }

    fun updateActionBar(icon: Int, title: String) {
        binding.ivToolbar.setImageResource(icon)
//        binding.toolbar.title = null
        binding.toolbar.title = ""
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.tvToolbar.text = title

    }

    fun addClickListenerToHomepageActionBarTitle() {
        binding.toolbar.setOnClickListener(onClickTitleBar)
    }

    fun removeClickListenerToHomepageActionBarTitle() {
        binding.toolbar.setOnClickListener(null)
        binding.toolbar.subtitle = null
    }

    private fun finishAndStartServiceLocationActivity() {
        val serviceLocationActivity = Intent(this, ServiceLocationActivity::class.java)
            .putExtra("fromVolunteer", true)
            .putExtra(ServiceLocationActivity.EXTRA_FROM_HOME_SWITCH, true)
        finish()
        startActivity(serviceLocationActivity)
    }

    fun restoreToolbarNavigation() {
        if (!::appBarConfiguration.isInitialized) return
        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        // Fix toolbar title appearing beside back button
        navController.addOnDestinationChangedListener { _, _, _ ->
            binding.toolbar.title = ""
            supportActionBar?.setDisplayShowTitleEnabled(false)
        }
    }

    override fun setAutoFlowBackNavigationBlocked(blocked: Boolean) {
        autoFlowBackNavigationBlocked = blocked
        if (blocked) {
            binding.toolbar.navigationIcon = null
            binding.toolbar.setNavigationOnClickListener(null)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        } else if (::appBarConfiguration.isInitialized) {
            restoreToolbarNavigation()
        }
    }

    fun setToolbarNavigationVisible(visible: Boolean) {
        if (!visible || autoFlowBackNavigationBlocked) {
            binding.toolbar.navigationIcon = null
            binding.toolbar.setNavigationOnClickListener(null)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        } else {
            restoreToolbarNavigation()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (autoFlowBackNavigationBlocked) return false
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onPause() {
        super.onPause()
        pref.removeOnPreferenceChangeListener(campHubPrefListener)
        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(wifiNetworkCallback)
            } catch (_: Exception) { }
            isNetworkCallbackRegistered = false
        }
        window.decorView.alpha = 0f
    }

    override fun onResume() {
        super.onResume()
        pref.addOnPreferenceChangeListener(campHubPrefListener)

        // If WiFi was lost while the app was in the background, the NetworkCallback
        // couldn't fire (it was unregistered). Catch that case here on resume.
        if (pref.isCampModeEnabled() && pref.isCampHubConnected()) {
            val hasWifi = connectivityManager.activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: false
            if (!hasWifi) pref.setCampHubConnected(false)
        }

        // Register WiFi callback (guard against double-registration)
        if (!isNetworkCallbackRegistered) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, wifiNetworkCallback)
            isNetworkCallbackRegistered = true
        }

        window.decorView.alpha = 1f
        refreshCampHubOfflineBanner()
        refreshCampHubDrawerItem()
        WorkerUtils.triggerCampQuickPullIfConnected(this, pref)
    }

    private fun refreshCampHubOfflineBanner() {
        binding.tvCampHubOffline.visibility =
            if (pref.isCampModeEnabled() && !pref.isCampHubConnected()) View.VISIBLE else View.GONE
    }

    private fun refreshCampHubDrawerItem() {
        binding.navView.menu.findItem(R.id.menu_connect_camp_hub)?.isVisible =
            pref.isCampModeEnabled()
    }

    private fun openCampHubConnect() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_OPEN_CAMP_CONNECT, true)
        )
    }

    /**
     * Tries to reach the camp hub URL over HTTP (3 s timeout).
     * Any HTTP response (even 4xx) means the server is up.
     * Must be called from a background thread.
     */
    private fun pingCampHub(): Boolean {
        return try {
            val url = java.net.URL(pref.getCampHubUrl())
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 100..499
        } catch (e: Exception) {
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
