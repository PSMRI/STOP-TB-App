package org.piramalswasthya.stoptb.ui.login_activity.sign_in

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.BuildConfig
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentSignInBinding
import org.piramalswasthya.stoptb.helpers.ImageUtils
import org.piramalswasthya.stoptb.helpers.Languages

import org.piramalswasthya.stoptb.helpers.Languages.ASSAMESE
import org.piramalswasthya.stoptb.helpers.Languages.ENGLISH
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.ui.login_activity.LoginActivity
import org.piramalswasthya.stoptb.utils.NoCopyPasteHelper
import org.piramalswasthya.stoptb.work.WorkerUtils
import javax.inject.Inject
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.ui.login_activity.camp_mode.CampModeConnectFragment
import org.piramalswasthya.stoptb.ui.login_activity.sign_in.SignInViewModel.CampHubStatus
import org.piramalswasthya.stoptb.helpers.RoleManager


@AndroidEntryPoint
class SignInFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    @Inject
    lateinit var roleManager: RoleManager

    private var _binding: FragmentSignInBinding? = null
    private val binding: FragmentSignInBinding
        get() = _binding!!


    private val viewModel: SignInViewModel by viewModels()
    private var suppressCampModeListener = false
    private var pendingLoginAfterCampCheck = false

    /**
     * True between handing a login to the camp hub and its result arriving. Lets the error branch
     * tell "the hub went away mid-request" (fall back to a local login) from "the hub answered and
     * rejected these credentials" (show the error).
     */
    private var campAuthInFlight = false

    private val stateUnselectedAlert by lazy {
        AlertDialog.Builder(context).setTitle("State Missing")
            .setMessage("Please choose user registered state: ")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }.create()
    }

    private val userChangeAlert by lazy {
        var username = "<b>${viewModel.getLoggedInUser()?.userName}</b>"
        var name = "<b>${viewModel.getLoggedInUser()?.name}</b>"

        var str =
            getString(R.string.login_diff_user).replace("@username", username).replace("asha", name)

        viewModel.unprocessedRecordsCount.value?.let {
            if (it > 0) {
                var count = viewModel.unprocessedRecordsCount.value
                str += getString(R.string.unsync_record_count).replace(oldValue = "@count", newValue = count.toString())
            }
        }

        MaterialAlertDialogBuilder(requireContext()).setTitle(resources.getString(R.string.logout))
            .setMessage(Html.fromHtml(str))
            .setPositiveButton(resources.getString(R.string.yes)) { dialog, _ ->
                viewModel.unprocessedRecordsCount.value?.let {
                    if (it > 0) {
                        WorkerUtils.triggerAmritPushWorker(requireContext())
                    } else {
                        lifecycleScope.launch {
                            viewModel.logout()
                        }
                        ImageUtils.removeAllBenImages(requireContext())
                        prefDao.deleteJWTToken()
                        WorkerUtils.cancelAllWork(requireContext())
                    }
                }
                dialog.dismiss()
            }.setNegativeButton(resources.getString(R.string.no)) { dialog, _ ->
                viewModel.updateState(NetworkResponse.Idle())
                dialog.dismiss()
            }.create()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignInBinding.inflate(layoutInflater, container, false)

        NoCopyPasteHelper.disableCopyPaste(binding.etPassword)
        NoCopyPasteHelper.disableCopyPaste(binding.etUsername)
        binding.etUsername.isEnabled = true
        binding.etUsername.isFocusable = true
        binding.etUsername.isFocusableInTouchMode = true
        binding.etPassword.isEnabled = true
        binding.etPassword.isFocusable = true
        binding.etPassword.isFocusableInTouchMode = true
        binding.rbAssamese.visibility = View.GONE

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateLoginAppName()
        binding.tvLoginVersion?.text = getString(R.string.login_app_version, BuildConfig.VERSION_NAME)
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val isKeyboardVisible = imeBottom > 0

            // The footer is decorative; hiding it while typing prevents it from
            // overlapping the Login button in the resized keyboard viewport.
            binding.textView12.visibility = if (isKeyboardVisible) View.GONE else View.VISIBLE
            binding.tvLoginVersion.visibility = if (isKeyboardVisible) View.GONE else View.VISIBLE
            v.updatePadding(
                left = initialLeft,
                top = initialTop,
                right = initialRight,
                bottom = initialBottom + maxOf(imeBottom, systemBottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.etUsername.doOnTextChanged { _, _, _, _ ->
            binding.tilUsername.error = null
        }
        binding.etPassword.doOnTextChanged { _, _, _, _ ->
            binding.tilPassword.error = null
        }

        binding.btnLogin.setOnClickListener { startLoginAttempt() }

        refreshCampModeUi()

        binding.cbCampMode.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCampModeListener) return@setOnCheckedChangeListener

            if (isChecked) {
                suppressCampModeListener = true
                binding.cbCampMode.isChecked = false
                suppressCampModeListener = false
                findNavController().navigate(R.id.action_signInFragment_to_campModeConnectFragment)
                return@setOnCheckedChangeListener
            }

            if (viewModel.isCampModeEnabled() || viewModel.isCampHubConnected()) {
                showCampDisconnectConfirmation()
                return@setOnCheckedChangeListener
            }

            viewModel.setCampModeEnabled(isChecked)
            refreshCampModeUi()
        }

        binding.btnCampRetry.setOnClickListener {
            findNavController().navigate(R.id.action_signInFragment_to_campModeConnectFragment)
        }

        viewModel.campHubStatus.observe(viewLifecycleOwner) { status ->
            updateCampHubStatus(status)
            when (status) {
                // CHECKING is the only non-terminal status. Every other one has to release a
                // pending login, or a login attempt would sit on the spinner forever.
                CampHubStatus.CHECKING -> Unit
                CampHubStatus.NOT_CONNECTED, CampHubStatus.IDLE -> {
                    releasePendingLogin()
                    refreshCampModeUi()
                }
                CampHubStatus.CONNECTED -> releasePendingLogin()
            }
        }

        if (viewModel.isCampModeEnabled()) {
            viewModel.checkCampHubConnection()
        }

        when (prefDao.getCurrentLanguage()) {
            ENGLISH -> binding.rgLangSelect.check(binding.rbEng.id)
            Languages.HINDI -> binding.rgLangSelect.check(binding.rbHindi.id)
            ASSAMESE -> binding.rgLangSelect.check(binding.rbAssamese.id)
        }

        binding.rgLangSelect.setOnCheckedChangeListener { _, i ->
            val currentLanguage = when (i) {
                binding.rbEng.id -> ENGLISH
                binding.rbHindi.id -> Languages.HINDI
                binding.rbAssamese.id -> ASSAMESE
                else -> ENGLISH
            }
            prefDao.saveSetLanguage(currentLanguage)
            val refresh = Intent(requireContext(), LoginActivity::class.java)
            requireActivity().finish()
            startActivity(refresh)
            activity?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)


        }

        binding.tvDeleteAccount?.setOnClickListener {
            var url = ""

            if (BuildConfig.FLAVOR.equals("saksham", true) ||BuildConfig.FLAVOR.equals("niramay", true) || BuildConfig.FLAVOR.equals("xushrukha", true)) {
                url = "https://forms.office.com/r/HkE3c0tGr6"
            } else {
                url =
                    "https://forms.office.com/Pages/ResponsePage.aspx?id=jQ49md0HKEGgbxRJvtPnRISY9UjAA01KtsFKYKhp1nNURUpKQzNJUkE1OUc0SllXQ0IzRFVJNlM2SC4u"
            }

            if (url.isNotEmpty()){
                val i = Intent(Intent.ACTION_VIEW)
                i.setData(Uri.parse(url))
                startActivity(i)
            }
        }


        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is NetworkResponse.Idle -> {
                    binding.clContent.visibility = View.VISIBLE
                    binding.pbSignIn.visibility = View.INVISIBLE
                    clearLoginFieldErrors()
                    var hasRememberMeUsername = false
                    var hasRememberMePassword = false
                    viewModel.fetchRememberedUserName()?.let {
                        binding.etUsername.setText(it)
                        hasRememberMeUsername = true
                    }
                    viewModel.fetchRememberedPassword()?.takeIf { it.isNotEmpty() }?.let {
                        binding.etPassword.setText(it)
                        binding.cbRemember.isChecked = true
                        hasRememberMePassword = true
                    } ?: binding.etPassword.text?.clear()
                    binding.cbRemember.isChecked = hasRememberMeUsername
                    campAuthInFlight = false
                    if (hasRememberMeUsername && hasRememberMePassword) {
                        // No isCampHubConnected() guard: the attempt probes the hub itself and
                        // falls back to a local login when it is unreachable.
                        startLoginAttempt()
                    }
                }

                is NetworkResponse.Loading -> validateInput()
                is NetworkResponse.Error -> {
                    if (campAuthInFlight) {
                        campAuthInFlight = false
                        // CampModeUrlInterceptor clears this flag once it confirms the hub became
                        // unreachable during the call, which is the only failure that should fall
                        // back to a local login. A 401 or a server error leaves it set, so bad
                        // credentials still surface instead of silently signing the user in.
                        if (!viewModel.isCampHubConnected()) {
                            loginLocally(
                                binding.etUsername.text.toString(),
                                binding.etPassword.text.toString()
                            )
                            return@observe
                        }
                    }
                    binding.pbSignIn.visibility = View.GONE
                    binding.clContent.visibility = View.VISIBLE
                    clearLoginFieldErrors()
                    when (val msg = state.message.orEmpty()) {
                        getString(R.string.error_login_invalid_password) -> {
                            binding.tilPassword.error = msg
                            binding.tvError.visibility = View.GONE
                        }
                        getString(R.string.error_sign_in_invalid_u_p) -> {
                            binding.tilUsername.error = msg
                            binding.tilPassword.error = msg
                            binding.tvError.visibility = View.GONE
                        }
                        else -> {
                            binding.tvError.text = state.message
                            binding.tvError.visibility = View.VISIBLE
                        }
                    }
                }

//                is NetworkResponse.Success -> {
//                    if (binding.cbRemember.isChecked) {
//                        val username = binding.etUsername.text.toString()
//                        val password = binding.etPassword.text.toString()
//                        viewModel.rememberUser(username, password)
//                    } else {
//                        viewModel.forgetUser()
//                    }
//                    binding.clContent.visibility = View.INVISIBLE
//                    binding.pbSignIn.visibility = View.VISIBLE
//                    binding.tvError.visibility = View.GONE
//
//                    // TEMP: Volunteer ID aane ke baad role check se replace karein
//                    activity?.finish()
//                    startActivity(Intent(requireContext(), VolunteerActivity::class.java))
//                }

                is NetworkResponse.Success -> {
                    campAuthInFlight = false

                    val user = state.data  // ya loggedInUser use karo

                    // Legacy, kept for reference:
//                    if (RoleConstants.isAllowedStopTbRole(user?.role)) {
                    if (roleManager.hasAnyValidRole()) {
//                        showLoginRoleToast(user)

                        if (binding.cbRemember.isChecked) {
                            val username = binding.etUsername.text.toString()
                            val password = binding.etPassword.text.toString()
                            viewModel.rememberUser(username, password)
                        } else {
                            viewModel.forgetUser()
                        }

                        binding.clContent.visibility = View.INVISIBLE
                        binding.pbSignIn.visibility = View.VISIBLE
                        binding.tvError.visibility = View.GONE
                        clearLoginFieldErrors()

                        activity?.finish()
                        startActivity(Intent(requireContext(), VolunteerActivity::class.java))

                    } else {
                        // ❌ Non-volunteer block
                        binding.pbSignIn.visibility = View.GONE
                        binding.clContent.visibility = View.VISIBLE
                        clearLoginFieldErrors()
                        binding.tvError.text = getString(R.string.error_login_role_not_allowed)
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

//        viewModel.logoutComplete.observe(viewLifecycleOwner) {
//            it?.let {
//                if (it) validateInput()
//            }
//        }

        viewModel.logoutComplete.observe(viewLifecycleOwner) {
            if (it == true) {
                viewModel.updateState(NetworkResponse.Idle())
            }
        }

        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Boolean>(CampModeConnectFragment.CAMP_HUB_CONNECTION_UPDATED)
            ?.observe(viewLifecycleOwner) {
                binding.root.post { refreshCampModeUi() }
            }
    }

    // Dead code — only call site is already commented out above. Left in place, not deleted.
//    private fun showLoginRoleToast(user: org.piramalswasthya.stoptb.model.User?) {
//        val role = user?.role?.takeIf { it.isNotBlank() } ?: "Unknown"
//        val tuStatus = if (user?.tus.orEmpty().isNotEmpty()) "TU: Yes" else "TU: No"
//        val healthFacilityStatus =
//            if (user?.healthFacilities.orEmpty().isNotEmpty()) "Health Facility: Yes" else "Health Facility: No"
//        Toast.makeText(
//            requireContext(),
//            "Role: $role\n$tuStatus, $healthFacilityStatus",
//            Toast.LENGTH_LONG
//        ).show()
//    }

    private fun updateLoginAppName() {
        val titleRes = if (BuildConfig.FLAVOR.contains("uat", ignoreCase = true)) {
            R.string.login_app_native_name_uat
        } else {
            R.string.login_app_native_name
        }
        binding.tvAppName?.text = getString(titleRes)
    }

    private fun showCampDisconnectConfirmation() {
        fun keepCampModeChecked() {
            suppressCampModeListener = true
            binding.cbCampMode?.isChecked = true
            suppressCampModeListener = false
        }

        keepCampModeChecked()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camp_disconnect_title)
            .setMessage(R.string.camp_disconnect_message)
            .setPositiveButton(R.string.camp_disconnect_positive) { dialog, _ ->
                viewModel.setCampModeEnabled(false)
                refreshCampModeUi()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.camp_disconnect_negative) { dialog, _ ->
                keepCampModeChecked()
                dialog.dismiss()
            }
            .setOnCancelListener {
                keepCampModeChecked()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        binding.root.post { refreshCampModeUi() }
    }

    override fun onStart() {
        super.onStart()
        binding.root.post { refreshCampModeUi() }
    }

    private fun refreshCampModeUi() {
        val isCampEnabled = viewModel.isCampModeEnabled()
        suppressCampModeListener = true
        binding.cbCampMode.isChecked = isCampEnabled
        suppressCampModeListener = false
        binding.llCampStatus.visibility = if (isCampEnabled) View.VISIBLE else View.GONE
        if (isCampEnabled && viewModel.isCampHubConnected()) {
            updateCampHubStatus(CampHubStatus.CONNECTED)
        } else if (isCampEnabled) {
            updateCampHubStatus(CampHubStatus.NOT_CONNECTED)
        } else {
            updateCampHubStatus(CampHubStatus.IDLE)
        }
    }

    private fun updateCampHubStatus(status: CampHubStatus) {
        when (status) {
            CampHubStatus.IDLE -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_not_connected)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant))
                setLoginButtonReady(false)
            }
            CampHubStatus.CHECKING -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_checking)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant))
                setLoginButtonReady(false)
            }
            CampHubStatus.CONNECTED -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_connected)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary))
                setLoginButtonReady(true)
            }
            CampHubStatus.NOT_CONNECTED -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_not_connected)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                setLoginButtonReady(false)
            }
        }
    }

    /**
     * Visually flags camp hub reachability on the login button without disabling it. The button
     * stays clickable because a login is still possible while the hub is down: the attempt probes
     * the hub and falls back to a local sign-in for a user already stored on this device.
     */
    private fun setLoginButtonReady(ready: Boolean) {
        binding.btnLogin.alpha = if (ready) 1f else 0.45f
        binding.btnLogin.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (ready) R.color.md_theme_light_primary else android.R.color.darker_gray
            )
        )
    }

    private fun clearLoginFieldErrors() {
        binding.tilUsername.error = null
        binding.tilPassword.error = null
    }

    /** @return true if username and password are both present */
    private fun applyLoginEmptyFieldErrors(username: String, password: String): Boolean {
        clearLoginFieldErrors()
        val usernameMissing = username.trim().isEmpty()
        val passwordMissing = password.isEmpty()
        if (!usernameMissing && !passwordMissing) return true
        if (usernameMissing) binding.tilUsername.error = getString(R.string.error_login_username_required)
        if (passwordMissing) binding.tilPassword.error = getString(R.string.error_login_password_required)
        return false
    }

    /**
     * The single entry point for a login attempt, from the Login button and from the
     * remembered-credentials auto-login.
     *
     * Always probes the camp hub first rather than trusting the cached connected flag, then lets
     * [releasePendingLogin] continue once the probe reaches a terminal status.
     */
    private fun startLoginAttempt() {
        binding.root.findFocus()?.let { focused ->
            val imm =
                activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(focused.windowToken, 0)
        }

        val username = binding.etUsername.text.toString()
        val password = binding.etPassword.text.toString()
        if (!applyLoginEmptyFieldErrors(username, password)) {
            binding.tvError.visibility = View.GONE
            return
        }

        binding.tvError.visibility = View.GONE
        pendingLoginAfterCampCheck = true
        binding.clContent.visibility = View.INVISIBLE
        binding.pbSignIn.visibility = View.VISIBLE
        viewModel.checkCampHubConnection()
    }

    /** Continues an attempt that was waiting on the camp hub probe, whatever the probe decided. */
    private fun releasePendingLogin() {
        if (!pendingLoginAfterCampCheck) return
        pendingLoginAfterCampCheck = false
        viewModel.loginInClicked()
    }

    /**
     * get username and password
     * validate with existing logged in user if exists else call login api
     */
    private fun validateInput() {
        binding.clContent.visibility = View.INVISIBLE
        binding.pbSignIn.visibility = View.VISIBLE
        val username = binding.etUsername.text.toString()
        val password = binding.etPassword.text.toString()

        if (!applyLoginEmptyFieldErrors(username, password)) {
            binding.pbSignIn.visibility = View.GONE
            binding.clContent.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
            viewModel.updateState(NetworkResponse.Idle())
            return
        }

        continueNormalLogin(username, password)
    }

    /**
     * Camp-first: whenever the hub answered the probe, authenticate against it, even for a user who
     * is already stored locally. Resolving a returning user from SharedPreferences instead is what
     * used to make a rejected login unrecoverable, and it also hid server-side role and password
     * changes from the app. The local comparison is now only the offline fallback.
     */
    private fun continueNormalLogin(username: String, password: String) {
        val loggedInUser = viewModel.getLoggedInUser()

        // A different user has to clear the logout confirmation first. Authenticating straight
        // away would overwrite the stored user while the previous user's unsynced rows are still
        // in the database.
        if (loggedInUser != null && !loggedInUser.userName.equals(username.trim(), true)) {
            userChangeAlert.setCanceledOnTouchOutside(false)
            userChangeAlert.show()
            return
        }

        if (viewModel.isCampHubConnected()) {
            campAuthInFlight = true
            viewModel.authUser(username, password)
            return
        }

        loginLocally(username, password)
    }

    /** Offline path: only reachable when the camp hub could not be reached. */
    private fun loginLocally(username: String, password: String) {
        val loggedInUser = viewModel.getLoggedInUser()
        viewModel.updateState(
            when {
                // Nothing to authenticate against - a first login has to go through the hub.
                loggedInUser == null ->
                    NetworkResponse.Error(getString(R.string.camp_hub_login_blocked))

                loggedInUser.password == password ->
                    NetworkResponse.Success(loggedInUser)

                else ->
                    NetworkResponse.Error(getString(R.string.error_login_invalid_password))
            }
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }


}
