package org.piramalswasthya.stoptb.ui.login_activity.sign_in

import android.app.AlertDialog
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.piramalswasthya.stoptb.ui.login_activity.sign_in.SignInViewModel.CampHubStatus
import org.piramalswasthya.stoptb.utils.RoleConstants


@AndroidEntryPoint
class SignInFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentSignInBinding? = null
    private val binding: FragmentSignInBinding
        get() = _binding!!


    private val viewModel: SignInViewModel by viewModels()
    private var suppressCampModeListener = false

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
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
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

        binding.btnLogin.setOnClickListener {
            view.findFocus()?.let { view ->
                val imm =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
            if (viewModel.isCampModeEnabled() && !viewModel.isCampHubConnected()) {
                binding.tvError.text = getString(R.string.camp_hub_login_blocked)
                binding.tvError.visibility = View.VISIBLE
                viewModel.checkCampHubConnection()
                return@setOnClickListener
            }
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            if (!applyLoginEmptyFieldErrors(username, password)) {
                binding.tvError.visibility = View.GONE
                return@setOnClickListener
            }
            viewModel.loginInClicked()
        }

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

            viewModel.setCampModeEnabled(isChecked)
            refreshCampModeUi()
        }

        binding.btnCampRetry.setOnClickListener {
            findNavController().navigate(R.id.action_signInFragment_to_campModeConnectFragment)
        }

        viewModel.campHubStatus.observe(viewLifecycleOwner) { status ->
            updateCampHubStatus(status)
            if (status == CampHubStatus.NOT_CONNECTED) {
                refreshCampModeUi()
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
                        hasRememberMePassword = true
                    } ?: binding.etPassword.text?.clear()
                    binding.cbRemember.isChecked = hasRememberMeUsername
                    if (hasRememberMeUsername && hasRememberMePassword && !viewModel.isCampModeEnabled()) {
                        validateInput()
                    }
                }

                is NetworkResponse.Loading -> validateInput()
                is NetworkResponse.Error -> {
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

                    val user = state.data  // ya loggedInUser use karo

                    if (RoleConstants.isAllowedStopTbRole(user?.role)) {
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
    }

    private fun showLoginRoleToast(user: org.piramalswasthya.stoptb.model.User?) {
        val role = user?.role?.takeIf { it.isNotBlank() } ?: "Unknown"
        val tuStatus = if (user?.tus.orEmpty().isNotEmpty()) "TU: Yes" else "TU: No"
        val healthFacilityStatus =
            if (user?.healthFacilities.orEmpty().isNotEmpty()) "Health Facility: Yes" else "Health Facility: No"
        Toast.makeText(
            requireContext(),
            "Role: $role\n$tuStatus, $healthFacilityStatus",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onResume() {
        super.onResume()
        refreshCampModeUi()
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
                binding.btnLogin.isEnabled = true
            }
            CampHubStatus.CHECKING -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_checking)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant))
                binding.btnLogin.isEnabled = false
            }
            CampHubStatus.CONNECTED -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_connected)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary))
                binding.btnLogin.isEnabled = true
            }
            CampHubStatus.NOT_CONNECTED -> {
                binding.tvCampStatus.text = getString(R.string.camp_hub_not_connected)
                binding.tvCampStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                binding.btnLogin.isEnabled = !viewModel.isCampModeEnabled()
            }
        }
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

        if (viewModel.isCampModeEnabled()) {
            if (viewModel.isCampHubConnected()) {
                viewModel.authUser(username, password)
            } else {
                viewModel.updateState(NetworkResponse.Error(getString(R.string.camp_hub_login_blocked)))
            }
            return
        }

        continueNormalLogin(username, password)
    }

    private fun continueNormalLogin(username: String, password: String) {
        val loggedInUser = viewModel.getLoggedInUser()

        if (loggedInUser == null) {
            viewModel.authUser(username, password)
        } else {
            if (loggedInUser.userName.equals(username.trim(), true)) {
                if (loggedInUser.password == password) {
                    if(isInternetAvailable(requireActivity())){
                        lifecycleScope.launch {
                            migrateLegacySessionIfNeeded()
                            viewModel.updateState(NetworkResponse.Success(loggedInUser))
                        }
                    }else{
                        viewModel.updateState(NetworkResponse.Success(loggedInUser))
                    }
                } else {
                    viewModel.updateState(
                        NetworkResponse.Error(getString(R.string.error_login_invalid_password))
                    )
                }
            } else {
                userChangeAlert.setCanceledOnTouchOutside(false)
                userChangeAlert.show()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }


    private suspend fun migrateLegacySessionIfNeeded() {
        if (!prefDao.getJWTAmritToken().isNullOrBlank()) return

        val user = prefDao.getLoggedInUser() ?: return

        when (
            val result = viewModel.authenticateForMigration(
                user.userName,
                user.password
            )
        ) {
            is NetworkResponse.Success -> {
                //Currenltly Implementation not required
            }

            is NetworkResponse.Error -> {
               //Currenltly Implementation not required
            }

            else -> Unit
        }
    }

    @Suppress("deprecation")
    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        } else {
            connectivityManager.activeNetworkInfo?.let { it.isAvailable && it.isConnected } == true
        }
    }

}
