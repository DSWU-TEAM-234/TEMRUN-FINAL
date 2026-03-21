package com.temrun_finalprojects.network

import com.google.gson.annotations.SerializedName


data class RunResultRequest(
    @SerializedName("duration") val duration: Int,                // 총 러닝 시간(초)
    @SerializedName("distance") val distance: Double,             // km
    @SerializedName("calories") val calories: Int,                // kcal
    @SerializedName("avgCadence") val avgCadence: Double,         // 평균 케이던스(spm)
    @SerializedName("maxCadence") val maxCadence: Int,            // 최대 케이던스
    @SerializedName("minCadence") val minCadence: Int,            // 최소 케이던스
    @SerializedName("abnormalCount") val abnormalCount: Int,      // 비정상 호흡 총합
    @SerializedName("musicCount") val musicCount: Int,            // 재생 곡 수
    @SerializedName("cadenceAccuracy") val cadenceAccuracy: Double,   // 케이던스 정확도(%)
    @SerializedName("breathNormalAcc") val breathNormalAcc: Int,      // 정상 호흡 비율(%)
    @SerializedName("breathAbnormalAcc") val breathAbnormalAcc: Int,  // 비정상 호흡 비율(%)
    @SerializedName("musicBpmList") val musicBpmList: List<Int>,      // 곡 BPM 목록(없으면 빈 리스트)
    @SerializedName("feedbackSummary") val feedbackSummary: FeedbackSummary, // 비정상 세부 유형 합계
    @SerializedName("cadenceHistory") val cadenceHistory: List<Int>   // 실시간 케이던스 기록(차트 원본)
)

/** 비정상 호흡 세부 유형 합계 */
data class FeedbackSummary(
    @SerializedName("breath_abnormal_type_1") val type1: Int, // 호흡 패턴만 불일치
    @SerializedName("breath_abnormal_type_2") val type2: Int, // 호흡 기관만 불일치
    @SerializedName("breath_abnormal_type_3") val type3: Int  // 둘 다 불일치
)
