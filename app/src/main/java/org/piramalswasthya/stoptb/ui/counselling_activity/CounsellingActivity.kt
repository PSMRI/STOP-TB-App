package org.piramalswasthya.stoptb.ui.counselling_activity

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.dynamicAdapter.CounsellingDynamicAdapter
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.ActivityCounsellingBinding
import org.piramalswasthya.stoptb.helpers.MyContextWrapper
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.CounsellingOverviewData
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class CounsellingActivity : AppCompatActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WrapperEntryPoint {
        val pref: PreferenceDao
    }

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

    private lateinit var binding: ActivityCounsellingBinding
    private val viewModel: CounsellingViewModel by viewModels()
    private lateinit var generalInfoAdapter: CounsellingDynamicAdapter

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
        setupGeneralInfoSection()
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

    private fun setupGeneralInfoSection() {
        generalInfoAdapter = CounsellingDynamicAdapter(
            questions = emptyList(),
            onValueChanged = { updatedQ -> viewModel.evaluateGeneralInfoConditions(updatedQ) }
        )
        binding.rvGeneralInfo.layoutManager = LinearLayoutManager(this)
        binding.rvGeneralInfo.adapter = generalInfoAdapter

//        binding.etCounsellingDate.setOnClickListener {
//            if (viewModel.isFormEditable.value == false) return@setOnClickListener
//            val overviewData = (viewModel.overview.value as? NetworkResponse.Success)?.data ?: return@setOnClickListener
//            val cal = Calendar.getInstance()
//            val currentText = binding.etCounsellingDate.text.toString()
//            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
//            try {
//                sdf.parse(currentText)?.let {
//                    cal.time = it
//                }
//            } catch (e: Exception) {}
//
//            val dpd = DatePickerDialog(
//                this,
//                { _, year, month, day ->
//                    val selected = Calendar.getInstance().apply {
//                        set(Calendar.HOUR_OF_DAY, 0)
//                        set(Calendar.MINUTE, 0)
//                        set(Calendar.SECOND, 0)
//                        set(Calendar.MILLISECOND, 0)
//                        set(year, month, day)
//                    }
//                    viewModel.updateCounsellingDate(selected.timeInMillis)
//                },
//                cal.get(Calendar.YEAR),
//                cal.get(Calendar.MONTH),
//                cal.get(Calendar.DAY_OF_MONTH)
//            )
//            dpd.datePicker.maxDate = System.currentTimeMillis()
//            dpd.datePicker.minDate = overviewData.regDate
//            dpd.show()
//        }
    }

    private fun updateGeneralInfoUi() {
        val questions = viewModel.generalInfoQuestions.value.orEmpty()

        if (questions.isEmpty()) {
            binding.rvGeneralInfo.visibility = View.GONE
            binding.ConsentToggleButton.visibility = View.GONE
            binding.consentToggleHeader.visibility = View.GONE
            return
        }

        binding.ConsentToggleButton.visibility = View.VISIBLE
        binding.consentToggleHeader.visibility = View.VISIBLE

        val consentQuestion = questions.firstOrNull { it.questionUuid == "TB2_GI_Q1" || it.questionUuid == "TB_A_Q1" }
        val consentAnswer = when (val v = consentQuestion?.value) {
            is List<*> -> v.firstOrNull()?.toString()
            is String -> v
            else -> null
        }

        val toggleOn = viewModel.isGeneralInfoToggleOn.value ?: true

        binding.ConsentToggleButton.setOnCheckedChangeListener(null)
        binding.ConsentToggleButton.isChecked = toggleOn
        binding.ConsentToggleButton.setOnCheckedChangeListener { _, checked ->
            viewModel.setGeneralInfoToggle(checked)
        }

        val overviewData = (viewModel.overview.value as? NetworkResponse.Success)?.data
        val formFullySubmitted = overviewData?.status in setOf("COMPLETE", "COMPLETED")
        val isEditable = !formFullySubmitted
//        binding.etCounsellingDate.isEnabled = isEditable

        if (toggleOn) {
            generalInfoAdapter.submitList(questions, isEditable)
            binding.rvGeneralInfo.visibility = View.VISIBLE
        } else {
            binding.rvGeneralInfo.visibility = View.GONE
        }

        binding.navigationFooter.root.visibility = View.VISIBLE
        binding.navigationFooter.btnBack.visibility = View.GONE

        val preSubmitSubmitted = overviewData?.preSubmitSubmitted == true

        if (preSubmitSubmitted) {
            binding.ConsentToggleButton.isEnabled = isEditable
            binding.navigationFooter.btnNext.text = getString(R.string.counselled)
            binding.navigationFooter.btnNext.visibility = View.VISIBLE
            binding.navigationFooter.btnNext.setOnClickListener {
                viewModel.startCounselling()
            }
            binding.navigationFooter.btnBack.visibility = View.GONE
        } else {
            binding.ConsentToggleButton.isEnabled = isEditable
            if (toggleOn) {
                when {
                    consentAnswer?.equals("NO", ignoreCase = true) == true -> {
                        binding.navigationFooter.btnNext.text = getString(R.string.btn_submit)
                        binding.navigationFooter.btnNext.visibility = if (isEditable) View.VISIBLE else View.GONE
                        binding.navigationFooter.btnNext.setOnClickListener {
                            viewModel.submitGeneralInfoRefusal()
                        }
                    }
                    consentAnswer?.equals("YES", ignoreCase = true) == true -> {
                        binding.navigationFooter.btnNext.text = getString(R.string.counselling_start_button)
                        binding.navigationFooter.btnNext.visibility = View.VISIBLE
                        binding.navigationFooter.btnNext.setOnClickListener {
                            viewModel.startCounselling()
                        }
                    }
                    else -> {
                        binding.navigationFooter.btnNext.visibility = View.GONE
                    }
                }
            } else {
                binding.navigationFooter.btnNext.visibility = View.GONE
            }
        }
    }

    private fun setupNavigationFooter() {
        val overviewData = (viewModel.overview.value as? NetworkResponse.Success)?.data

        binding.navigationFooter.root.visibility = View.VISIBLE
        if (overviewData?.preSubmitSubmitted == true) {
            binding.navigationFooter.btnNext.text = getString(R.string.counselled)
        } else {
            binding.navigationFooter.btnNext.text = getString(R.string.counselling_start_button)
        }
        binding.navigationFooter.btnNext.visibility = View.VISIBLE
        binding.navigationFooter.btnNext.setOnClickListener {
            viewModel.startCounselling()
        }
        if (overviewData?.preSubmitSubmitted == true) {
            binding.navigationFooter.btnNext.text = getString(R.string.counselled)
            binding.navigationFooter.btnNext.visibility = View.VISIBLE
            binding.navigationFooter.btnBack.visibility = View.GONE
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
            else {
                val preSubmitSections = viewModel.schemaData?.sections?.filter { it.sectionPhase == "PRE_SUBMIT" } ?: emptyList()
                val idx = preSubmitSections.indexOf(sec)
                if (idx != -1) ('A' + idx).toString() else ('A' + sec.displayOrder - 2).toChar().toString()
            }
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
                        updateGeneralInfoUi()
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
            val isEditable = viewModel.isSectionEditable(section)

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

        viewModel.generalInfoQuestions.observe(this) { updateGeneralInfoUi() }
        viewModel.isGeneralInfoToggleOn.observe(this) { updateGeneralInfoUi() }
        viewModel.isFormEditable.observe(this) { updateGeneralInfoUi() }
        viewModel.generalInfoRefusalSubmitted.observe(this) { submitted ->
            if (submitted == true) finish()
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
        updateGeneralInfoUi()
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