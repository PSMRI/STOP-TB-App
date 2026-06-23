package org.piramalswasthya.stoptb.ui.counselling_activity

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.ActivityCounsellingBinding
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import android.widget.Toast
import org.piramalswasthya.stoptb.model.CounsellingOverviewData
import timber.log.Timber

@AndroidEntryPoint
class CounsellingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCounsellingBinding
    private val viewModel: CounsellingViewModel by viewModels()

    // Progress bar stays visible until overview has loaded.
    private var isOverviewReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCounsellingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.nsvContent) { view, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(
                bottom = if (imeInset > 0) imeInset + binding.navigationFooter.btnNext.height
                else resources.getDimensionPixelSize(R.dimen.nsv_counselling_padding_bottom)
            )
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.counselling_overview_title)
        }
        setupNavigationFooter()
        observeViewModel()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is CounsellingFormFragment) {
                    val currentStep = viewModel.currentStep.value ?: 0
                    if (currentStep > 0) {
                        viewModel.previousSection()
                    } else {
                        showOverviewScreen()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupNavigationFooter() {
        val overviewData = (viewModel.overview.value as? NetworkResponse.Success)?.data
        val status = overviewData?.status

        binding.navigationFooter.root.visibility = View.VISIBLE
        binding.navigationFooter.btnNext.text = getString(R.string.counselling_start_button)
        binding.navigationFooter.btnNext.visibility = View.VISIBLE
        binding.navigationFooter.btnNext.setOnClickListener {
            viewModel.startCounselling()
        }

        if (status == getString(R.string.submitted) || status == getString(R.string.completed)) {
            binding.navigationFooter.btnBack.text = getString(R.string.counselling_follow_up_button)
            binding.navigationFooter.btnBack.visibility = View.VISIBLE
            binding.navigationFooter.btnBack.setOnClickListener {
                viewModel.startFollowUp()
            }
        } else {
            binding.navigationFooter.btnBack.visibility = View.GONE
        }
    }

    private fun switchToFormView() {
        binding.patientHeader.root.visibility = View.GONE
        binding.llCounsellingInfo.visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, CounsellingFormFragment())
            .commitNow()

        val step = viewModel.currentStep.value ?: 0
        updateSectionTitle(step)

        val totalSections = viewModel.schemaData?.sections?.size ?: 1
        binding.navigationFooter.btnNext.text =
            if (step == totalSections - 1) getString(R.string.btn_submit)
            else getString(R.string.btn_next_text)

        binding.navigationFooter.btnNext.setOnClickListener {
            hideKeyboard()
            clearActiveFocus()
            viewModel.nextSection()

        }

        binding.navigationFooter.btnBack.text = getString(R.string.btn_back_text)
        binding.navigationFooter.btnBack.visibility = if (step == 0) View.GONE else View.VISIBLE
        binding.navigationFooter.btnBack.setOnClickListener {
            hideKeyboard()
            clearActiveFocus()
            viewModel.previousSection()
        }
    }

    private fun updateSectionTitle(step: Int) {
        viewModel.schemaData?.sections?.getOrNull(step)?.let { sec ->
            val letter = if (sec.sectionPhase == "POST_SUBMIT") "F"
            else ('A' + sec.displayOrder - 1).toChar().toString()
            supportActionBar?.title = "Section $letter - ${sec.sectionName}"
        }
    }

    private fun observeViewModel() {
        viewModel.overview.observe(this) { state ->
            when (state) {
                is NetworkResponse.Idle -> showLoading()
                is NetworkResponse.Loading -> showLoading()
                is NetworkResponse.Success -> {
                    state.data?.let {
                        populatePatientHeader(it)
                        binding.etCounsellingDate.setText(it.counsellingDate)
                        binding.etCounsellingOfficer.setText(it.counsellingOfficer)
                        isOverviewReady = true
                        maybeShowContent()
                        setupNavigationFooter()
                    }
                }
                is NetworkResponse.Error -> {
                    Timber.e("Failed to load counselling overview: ${state.message}")
                    showError(state.message) {
                        isOverviewReady = false
                        viewModel.loadOverview()
                    }
                }
            }
        }

        viewModel.formSchema.observe(this) { state ->
            when (state) {
                is NetworkResponse.Idle -> Unit
                is NetworkResponse.Loading -> showLoading()
                is NetworkResponse.Success -> {
                    showContent()
                    switchToFormView()
                }
                is NetworkResponse.Error -> {
                    Timber.e("Failed to load form schema: ${state.message}")
                    showError(state.message ?: getString(R.string.counselling_data_load_error)) {
                        viewModel.retryLoadFormSchema()
                    }
                }
            }
        }

        viewModel.formSubmitted.observe(this) { submitted ->
            if (submitted == true) {
                Timber.d("Form phase completed!")
                viewModel.resetFormSubmitted()
                viewModel.loadOverview()
                showOverviewScreen()
            }
        }

        viewModel.currentStep.observe(this) { step ->
            // Only update navigation state while the form is active.
            if (supportFragmentManager.findFragmentById(R.id.fragment_container) !is CounsellingFormFragment) return@observe

            binding.nsvContent.post { binding.nsvContent.scrollTo(0, 0) }

            val section = viewModel.schemaData?.sections?.getOrNull(step)
            val total = viewModel.schemaData?.sections?.size ?: 1
            val isEditable = viewModel.isFormEditable.value ?: true

            binding.navigationFooter.btnNext.text = when {
                step == total - 1 && isEditable -> getString(R.string.btn_submit)
                step == total - 1 && !isEditable -> getString(R.string.btn_finish)
                else -> getString(R.string.btn_next_text)
            }
            
            binding.navigationFooter.btnBack.text = getString(R.string.btn_back_text)
            binding.navigationFooter.btnBack.visibility = if (step == 0) View.GONE else View.VISIBLE

            section?.let {
                updateSectionTitle(step)
            }
        }

        viewModel.saveError.observe(this) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                viewModel.resetSaveError()
            }
        }
    }

    private fun maybeShowContent() {
        if (isOverviewReady) showContent()
    }

    private fun showLoading() {
        binding.nsvContent.visibility = View.GONE
        binding.llError.visibility = View.GONE
        binding.navigationFooter.root.visibility = View.GONE
        binding.flLoading.visibility = View.VISIBLE
    }

    private fun showContent() {
        binding.flLoading.visibility = View.GONE
        binding.llError.visibility = View.GONE
        binding.nsvContent.visibility = View.VISIBLE
        binding.navigationFooter.root.visibility = View.VISIBLE
    }

    private fun showError(message: String?, onRetry: () -> Unit) {
        binding.flLoading.visibility = View.GONE
        binding.nsvContent.visibility = View.GONE
        binding.navigationFooter.root.visibility = View.GONE
        binding.tvErrorMessage.text = message ?: getString(R.string.counselling_load_error)
        binding.btnRetry.setOnClickListener {
            showLoading()
            onRetry()
        }
        binding.llError.visibility = View.VISIBLE
    }

    private fun populatePatientHeader(data: CounsellingOverviewData) {
        binding.patientHeader.tvPatientName.text = data.patientName
        binding.patientHeader.tvNikshayIdHeader.text =
            getString(R.string.counselling_nikshay_id_format, data.nikshayId)
        binding.patientHeader.tvBeneficiaryId.text = data.beneficiaryId
        binding.patientHeader.tvNikshayId.text = data.nikshayId
        binding.patientHeader.tvAgeGender.text = data.ageGender
        binding.patientHeader.tvDiagnosis.text = data.diagnosis
    }

    private fun showOverviewScreen() {
        // Remove the form fragment so its views don't remain visible beneath the overview.
        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { frag ->
            supportFragmentManager.beginTransaction().remove(frag).commitNow()
        }

        binding.patientHeader.root.visibility = View.VISIBLE
        binding.llCounsellingInfo.visibility = View.VISIBLE
        supportActionBar?.title = getString(R.string.counselling_overview_title)
        setupNavigationFooter()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun clearActiveFocus() {
        currentFocus?.clearFocus()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

}
