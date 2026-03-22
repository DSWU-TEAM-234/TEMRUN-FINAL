package com.temrun_finalprojects.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.temrun_finalprojects.network.RunResultRequest
import com.temrun_finalprojects.network.RunRepository
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import android.widget.Toast



class ResultViewModel(
    private val repo: RunRepository = RunRepository()
) : ViewModel() {

    enum class SaveState { Idle, Saving, Success, Error }

    private val _saveState = MutableLiveData(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    fun save(runId: String, req: RunResultRequest, token: String? = null) {
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val res = repo.saveRunResult(runId, req, token)
                _saveState.postValue(
                    if (res.isSuccessful) SaveState.Success else SaveState.Error
                )
            } catch (_: Exception) {
                _saveState.postValue(SaveState.Error)
            }
        }
    }
}
