package com.aishield.detector.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aishield.detector.core.AccountStore
import com.aishield.detector.databinding.ActivityAuthBinding
import com.aishield.detector.util.BackendApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guest or email sign-in. Guest works fully offline; email accounts are
 * handled by the project backend (swap-in point for Firebase Auth).
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGuest.setOnClickListener {
            AccountStore.signInAsGuest()
            goMain()
        }

        binding.btnSignIn.setOnClickListener { doAuth(isRegister = false) }
        binding.btnSignUp.setOnClickListener { doAuth(isRegister = true) }
    }

    private fun doAuth(isRegister: Boolean) {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        if (!email.contains("@") || password.length < 6) {
            binding.tvAuthMsg.text = getString(com.aishield.detector.R.string.auth_invalid)
            binding.tvAuthMsg.visibility = View.VISIBLE
            return
        }
        binding.btnSignIn.isEnabled = false
        binding.btnSignUp.isEnabled = false
        binding.tvAuthMsg.visibility = View.VISIBLE
        binding.tvAuthMsg.text = getString(com.aishield.detector.R.string.auth_working)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (isRegister) BackendApi.register(email, password)
                else BackendApi.login(email, password)
            }
            result.onSuccess { user ->
                AccountStore.save(user.token, user.email, guest = false)
                goMain()
            }.onFailure {
                binding.tvAuthMsg.text = getString(com.aishield.detector.R.string.auth_failed)
                binding.btnSignIn.isEnabled = true
                binding.btnSignUp.isEnabled = true
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
