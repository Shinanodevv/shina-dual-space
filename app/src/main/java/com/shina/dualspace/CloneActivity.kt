package com.shina.dualspace

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * The ONLY exported entry point for cross-profile clone requests.
 * Protected by a signature-level permission: only apps signed with the same
 * certificate (our personal-profile and work-profile copies) can trigger it,
 * and every request requires an explicit user confirmation in this activity
 * before anything is installed. v2.0/v2.1 put the CLONE filter on the exported
 * MainActivity with no permission and cloned immediately.
 */
class CloneActivity : AppCompatActivity() {

    private val admin: ComponentName by lazy { ComponentName(this, DualAdminReceiver::class.java) }
    private val dpm: DevicePolicyManager by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != MainActivity.ACTION_CLONE) {
            finish()
            return
        }
        val pkg = intent.getStringExtra(MainActivity.EXTRA_PKG)
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }

        registerCrossProfileFilters()

        if (!isInWorkProfile() && !isProfileOwner()) {
            Toast.makeText(
                this,
                "Clone cuma bisa jalan di dalam Work Profile. Buka Shina Dual versi badge koper dulu.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        if (FinanceGuard.isFinanceApp(pkg)) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ App keuangan terdeteksi")
                .setMessage(
                    "$pkg kelihatan seperti app bank / e-wallet.\n\n" +
                        "Clone app keuangan ke Work Profile baru bisa dianggap sinyal high-risk oleh bank " +
                        "(perangkat/profil baru), dan pola ini lagi diawasi ketat. Sangat disarankan JANGAN clone app ini.\n\n" +
                        "Tetap lanjut cuma kalau kamu paham risikonya."
                )
                .setPositiveButton("Tetap lanjut, saya paham") { _, _ -> confirmClone(pkg) }
                .setNegativeButton("Batalkan") { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        confirmClone(pkg)
    }

    private fun confirmClone(pkg: String) {
        AlertDialog.Builder(this)
            .setTitle("Clone app ini?")
            .setMessage("Clone $pkg ke Work Profile Shina Dual?\nClone punya data & login sendiri, pisah dari app utama.")
            .setPositiveButton("Ya, clone") { _, _ ->
                doClone(pkg)
                finish()
            }
            .setNegativeButton("Batal") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun doClone(pkg: String) {
        try {
            val ok = dpm.installExistingPackage(admin, pkg)
            Toast.makeText(
                this,
                if (ok) "Clone dibuat: $pkg" else "Clone gagal / udah ada: $pkg",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Clone gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInWorkProfile(): Boolean = try {
        dpm.isManagedProfile(admin)
    } catch (e: Exception) {
        false
    }

    private fun isProfileOwner(): Boolean = try {
        dpm.isProfileOwnerApp(packageName)
    } catch (e: Exception) {
        false
    }

    private fun registerCrossProfileFilters() {
        try {
            val flags = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT or
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            val filter = IntentFilter(MainActivity.ACTION_CLONE)
            filter.addCategory(android.content.Intent.CATEGORY_DEFAULT)
            dpm.addCrossProfileIntentFilter(admin, filter, flags)
        } catch (e: Exception) {
            // already registered / not owner in this profile
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                dpm.setCrossProfilePackages(admin, setOf(packageName))
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
