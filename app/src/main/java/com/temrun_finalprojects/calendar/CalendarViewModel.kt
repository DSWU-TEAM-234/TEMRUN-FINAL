package com.temrun_finalprojects.calendar

import android.util.Log
import androidx.lifecycle.*
import com.temrun_finalprojects.calendar.data.CalendarSummary
import com.temrun_finalprojects.network.RetrofitClient
import kotlinx.coroutines.launch

class CalendarViewModel : ViewModel() {

    // 응답 데이터를 담아둘 LiveData
    private val _summary = MutableLiveData<CalendarSummary>()
    val summary: LiveData<CalendarSummary> = _summary

    // 에러 메시지도 상태로 보관
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // 서버 호출 함수
    fun fetchCalendar(userId: String, yearMonth: String) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getCalendar(userId, yearMonth)
                if (res.isSuccessful) {
                    res.body()?.let {
                        Log.d("CalendarViewModel", "요청 성공: $yearMonth, runningDates=${it.runningDates}")
                        _summary.value = it // LiveData에 결과 저장
                    } ?: run {
                        Log.e("CalendarViewModel", "응답 body가 null입니다.")
                        _error.value = "Empty response body"
                    }
                } else {
                    Log.e("CalendarViewModel", "요청 실패: HTTP ${res.code()} ${res.message()}")
                    _error.value = "HTTP ${res.code()}"
                }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "네트워크 예외 발생: ${e.message}", e)
                _error.value = e.message
            }
        }
    }
}
