package com.temrun_finalprojects.data

// 러닝 중 들어오는 호흡 판정 누적용
data class BreathResultCount(
    var normal: Int = 0,         // 정상
    var patternOnly: Int = 0,    // 패턴만 불일치
    var organOnly: Int = 0,     // 호흡 기관만 불일치
    var bothMismatch: Int = 0   // 기관 + 패턴 둘 다 불일치
) {
    fun add(result: String) {
        when (result) {
            "정상" -> normal++

            // 호흡 기관(코/입) 불일치
            "들숨과 날숨의 호흡 기관이 일치하지 않습니다. 코로 들이마쉬고 입으로 내쉬세요!" -> organOnly++

            // 호흡 패턴(1:1, 2:1 등) 불일치
            "처음 선택한 호흡 패턴과 일치하지 않은 패턴으로 달리고 있습니다. " -> patternOnly++

            // 기관 + 패턴 둘 다 불일치
            "들숨은 코로, 날숨은 입으로 호흡하고 음악의 BPM에 맞게 패턴을 유지해주세요!" -> bothMismatch++

            // 알 수 없는 경우 로그 출력 (선택사항)
            else -> {
                android.util.Log.w("BreathResultCount", "예상치 못한 결과: $result")
            }
        }
    }
}
