package org.piramalswasthya.stoptb.ui.abha_id_activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.AbhaTokenResponse
import org.piramalswasthya.stoptb.network.NetworkResult
import org.piramalswasthya.stoptb.network.interceptors.TokenInsertAbhaInterceptor
import org.piramalswasthya.stoptb.repositories.AbhaIdRepo
import org.piramalswasthya.stoptb.repositories.UserRepo
import org.piramalswasthya.stoptb.utils.Log
import javax.inject.Inject

@HiltViewModel
class AbhaIdViewModel @Inject constructor(
    private val abhaIdRepo: AbhaIdRepo,
    private val prefDao: PreferenceDao,
    private val userRepo: UserRepo
) : ViewModel() {

    enum class State {
        LOADING_TOKEN,
        ERROR_SERVER,
        ERROR_NETWORK,
        SUCCESS
    }

    private val _state = MutableLiveData<State>()
    val state: LiveData<State>
        get() = _state

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String>
        get() = _errorMessage

    init {
        generateAmritToken()
//        generateAccessToken()
//        generatePublicKey()
    }

    private var _accessToken: AbhaTokenResponse? = null
    private val accessToken: AbhaTokenResponse
        get() = _accessToken!!

    private var _authCert: String? = null
    private val authCert: String
        get() = _authCert!!


     fun generateAmritToken() {
        _state.value = State.LOADING_TOKEN
        val user = prefDao.getLoggedInUser()
        viewModelScope.launch {
            user?.let {
                if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                    generateAccessToken()
                } else {
                    _state.value = State.ERROR_SERVER
                    Log.e("Error","Server error ${userRepo.refreshTokenTmc(user.userName, user.password)}")
                }
            }
        }
    }

    fun generateAccessToken() {
        _state.value = State.LOADING_TOKEN
        viewModelScope.launch {
            when (val result = abhaIdRepo.getAccessToken()) {
                is NetworkResult.Success -> {
                    _accessToken = result.data
                    TokenInsertAbhaInterceptor.setToken(accessToken.accessToken)
                    generatePublicKey()
                    _state.value = State.SUCCESS
                }

                is NetworkResult.Error -> {
                    _state.value = State.ERROR_SERVER
                    _errorMessage.value = result.message
                }

                is NetworkResult.NetworkError -> {
                    _state.value = State.ERROR_NETWORK
                }
            }
        }
    }

    private fun generatePublicKey() {
        val publicKey = prefDao.getPublicKeyForAbha()
        if (publicKey == null) {
            viewModelScope.launch {
                when (val result = abhaIdRepo.getAuthCert()) {
                    is NetworkResult.Success -> {
                        prefDao.savePublicKeyForAbha(result.data)
                    }

                    is NetworkResult.Error -> {}
                    is NetworkResult.NetworkError -> {}
                }
            }
        }
    }
}