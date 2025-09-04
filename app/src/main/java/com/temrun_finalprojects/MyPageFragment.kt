package com.temrun_finalprojects

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import okhttp3.*
import java.io.IOException

class MyPageFragment : Fragment(R.layout.fragment_account) {

    private val client by lazy { OkHttpClient() }
    // TODO: 서버 재기동 시 교체
    private val BASE_URL ="https://d07802f0f999.ngrok-free.app"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        view.findViewById<Button>(R.id.btnDeleteAccount)?.setOnClickListener {
            showDeleteConfirm()
        }
    }

    private fun showDeleteConfirm() {
        AlertDialog.Builder(requireContext())
            .setTitle("회원탈퇴")
            .setMessage("모든 데이터가 삭제됩니다. 정말 탈퇴하시겠습니까?")
            .setPositiveButton("탈퇴") { _, _ -> deleteAccount() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteAccount() {
        val prefs = requireContext().getSharedPreferences("AppUser", android.content.Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val req = Request.Builder()

            .url("$BASE_URL/api/auth/$userId")
            .delete()
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "탈퇴 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    requireActivity().runOnUiThread {
                        if (response.isSuccessful) {
                            onAccountDeleted()
                        } else {
                            Toast.makeText(requireContext(),
                                "탈퇴 실패(${response.code})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }


    private fun onAccountDeleted() {
        // AppUser SharedPreferences 포함해서 모두 삭제
        listOf("AppUser", "Spotify", "AppPrefs").forEach { name ->
            requireContext().getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        val intent = Intent(requireContext(), StartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

}
