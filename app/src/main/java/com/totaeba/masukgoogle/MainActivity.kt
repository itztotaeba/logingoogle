package com.totaeba.masukgoogle

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.totaeba.masukgoogle.databinding.ActivityMainBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.launch

// Huawei imports
import com.huawei.hms.support.account.AccountAuthManager
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.huawei.hms.support.account.result.AuthAccount
import com.huawei.hms.support.account.service.AccountAuthService

// Google Play Services check
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    private val HUAWEI_SIGN_IN = 8888

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // --- Tampilkan tombol Google hanya jika GMS tersedia ---
        if (isGmsAvailable()) {
            binding.btnGoogleSignIn.visibility = View.VISIBLE
        } else {
            binding.btnGoogleSignIn.visibility = View.GONE
        }

        // Tombol Huawei selalu muncul
        binding.btnHuaweiSignIn.visibility = View.VISIBLE

        // --- Google Sign-In ---
        val credentialManager = CredentialManager.create(this)
        val googleRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()

        binding.btnGoogleSignIn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = credentialManager.getCredential(this@MainActivity, googleRequest)
                    val credential = result.credential
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)

                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                updateUI(auth.currentUser)
                            } else {
                                binding.tvStatus.text = task.exception?.localizedMessage ?: "Sign-in failed"
                                updateUI(null)
                            }
                        }
                } catch (e: NoCredentialException) {
                    binding.tvStatus.text = "No Google account found on device."
                    updateUI(null)
                } catch (e: Exception) {
                    binding.tvStatus.text = e.localizedMessage ?: "Google login failed"
                    updateUI(null)
                }
            }
        }

        // --- Huawei Sign-In ---
        binding.btnHuaweiSignIn.setOnClickListener {
            val params: AccountAuthParams =
                AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                    .setIdToken()
                    .createParams()
            val service: AccountAuthService = AccountAuthManager.getService(this, params)
            startActivityForResult(service.signInIntent, HUAWEI_SIGN_IN)
        }

        // --- Sign Out ---
        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            updateUI(null)
        }

        // Cek status login saat pertama kali dibuka
        updateUI(auth.currentUser)
    }

    private fun isGmsAvailable(): Boolean {
        val resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        return resultCode == ConnectionResult.SUCCESS
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HUAWEI_SIGN_IN) {
            val task = AccountAuthManager.parseAuthResultFromIntent(data)
            if (task.isSuccessful) {
                val account: AuthAccount = task.result
                binding.tvStatus.text = "Signed in with Huawei: ${account.displayName}"
                binding.btnGoogleSignIn.visibility = View.GONE
                binding.btnHuaweiSignIn.visibility = View.GONE
                binding.btnSignOut.visibility = View.VISIBLE
            } else {
                binding.tvStatus.text = "Huawei login failed: ${task.exception.message}"
            }
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            binding.tvStatus.text = getString(
                R.string.status_signed_in,
                user.displayName ?: user.email ?: ""
            )
            binding.btnGoogleSignIn.visibility = if (isGmsAvailable()) View.GONE else View.GONE
            binding.btnHuaweiSignIn.visibility = View.GONE
            binding.btnSignOut.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = getString(R.string.status_signed_out)
            binding.btnGoogleSignIn.visibility = if (isGmsAvailable()) View.VISIBLE else View.GONE
            binding.btnHuaweiSignIn.visibility = View.VISIBLE
            binding.btnSignOut.visibility = View.GONE
        }
    }
}
