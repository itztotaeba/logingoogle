package com.totaeba.masukgoogle

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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // ==== Cek Google Play Services tersedia atau tidak ====
        val gmsAvailable = isGooglePlayServicesAvailable()
        if (!gmsAvailable) {
            // Kalau device tidak ada Google Service (contoh: Huawei HMS only)
            binding.btnGoogleSignIn.visibility = View.GONE
        }

        // Buat CredentialManager
        val credentialManager = CredentialManager.create(this)

        // Buat request untuk Google Sign-In
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()

        // Tombol Sign In Google (kalau tersedia)
        binding.btnGoogleSignIn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = credentialManager.getCredential(this@MainActivity, request)
                    val credential = result.credential

                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.data)

                    val firebaseCredential = GoogleAuthProvider.getCredential(
                        googleIdTokenCredential.idToken, null
                    )

                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                updateUI(auth.currentUser)
                            } else {
                                binding.tvStatus.text = task.exception?.localizedMessage
                                    ?: "Sign-in failed"
                                updateUI(null)
                            }
                        }
                } catch (e: NoCredentialException) {
                    e.printStackTrace()
                    binding.tvStatus.text =
                        "Tidak ada akun Google tersedia di device/emulator.\n" +
                                "Login ke akun Google dulu di Settings."
                    updateUI(null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.tvStatus.text = e.localizedMessage ?: "Login failed"
                    updateUI(null)
                }
            }
        }

        // Tombol Sign Out
        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            updateUI(null)
        }

        // Cek status login saat pertama kali dibuka
        updateUI(auth.currentUser)
    }

    // 🔹 Fungsi cek GMS
    private fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this)
        return availability == ConnectionResult.SUCCESS
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            binding.tvStatus.text = getString(
                R.string.status_signed_in,
                user.displayName ?: user.email ?: ""
            )
            binding.btnGoogleSignIn.visibility = View.GONE
            binding.btnSignOut.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = getString(R.string.status_signed_out)
            // Hanya tampilkan tombol Google Sign-In jika ada GMS
            if (isGooglePlayServicesAvailable()) {
                binding.btnGoogleSignIn.visibility = View.VISIBLE
            } else {
                binding.btnGoogleSignIn.visibility = View.GONE
            }
            binding.btnSignOut.visibility = View.GONE
        }
    }
}
