package com.temrun_finalprojects.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.temrun_finalprojects.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.annotations.SerializedName
import com.temrun_finalprojects.result.ResultPagerAdapter

class RunningResultBottomSheet : BottomSheetDialogFragment() {

    interface LocalApi {
        @GET("/api/runs/{run_id}/view")
        suspend fun getRunDetail(@Path("run_id") runId: String): Response<RunDetailResponse>
    }

    @Parcelize
    data class RunDetailResponse(
        val startTime: String,
        val duration: Int,
        @SerializedName("avr_music_bpm") val avrMusicBpm: Int,
        val calories: Int,
        @SerializedName("cadence_accuracy") val cadenceAccuracy: Double,
        val avgCadence: Double,
        val distance: Double,
        @SerializedName("taget_cadence") val targetCadence: Int,
        @SerializedName("cadence_history") val cadenceHistory: List<Int>,
        @SerializedName("breath_normal_acc") val breathNormalAcc: Int,
        @SerializedName("breath_abnormal_acc") val breathAbnormalAcc: Int,
        @SerializedName("breath_abnormal_type_1") val breathAbnormalType1: Double,
        @SerializedName("breath_abnormal_type_2") val breathAbnormalType2: Double,
        @SerializedName("breath_abnormal_type_3") val breathAbnormalType3: Double
    ) : Parcelable

    private val api: LocalApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://58d615726e5f.ngrok-free.app")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LocalApi::class.java)
    }

    private lateinit var tvTime: TextView
    private lateinit var tvBpm: TextView
    private lateinit var tvCalorie: TextView
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_running_result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvTime = view.findViewById(R.id.textViewTime)
        tvBpm = view.findViewById(R.id.textViewBpm)
        tvCalorie = view.findViewById(R.id.textViewCalorie)
        viewPager = view.findViewById(R.id.viewPager)

        val runId = arguments?.getString("runId") ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val resp = api.getRunDetail(runId)
            if (resp.isSuccessful) {
                resp.body()?.let { d ->
                    // 상단 요약 카드뷰에 데이터 바인딩
                    tvTime.text = formatDuration(d.duration)
                    tvBpm.text = "${d.avrMusicBpm}"
                    tvCalorie.text = "${d.calories}"

                    // ViewPager2 어댑터 설정 (호스트 패턴 없이 간단하게 전달)
                    viewPager.adapter = ResultPagerAdapter(this@RunningResultBottomSheet, d)
                }
            }
        }
    }

    private fun formatDuration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
        //AI 피드백 텍스트 설정 (나중에 서버로부터 받아오는 구조로 바꿔도 됨)
        /*
        binding.textFeedback1.text = "페이스가 안정적으로 유지되고 있습니다."
        binding.textFeedback2.text = "호흡을 적절히 유지하고 있습니다."
        binding.textFeedback3.text = "다음 목표: 6km 달성에 도전해보세요."
         */
    }

    fun show(fm: FragmentManager) {
        show(fm, tag)
    }
}
