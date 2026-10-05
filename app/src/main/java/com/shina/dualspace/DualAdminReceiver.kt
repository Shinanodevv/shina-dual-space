package com.shina.dualspace

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.widget.Toast

class DualAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DualAdminReceiver::class.java)
        try {
            dpm.setProfileName(admin, "Shina Dual")
            dpm.setProfileEnabled(admin)
        } catch (e: Exception) {
            // Profile will be enabled on first launch inside the work profile instead.
        }
        // Register cross-profile intent filters NOW (this receiver runs inside the
        // new work profile). v2.0 only did this when the work-side MainActivity
        // happened to launch, so clone requests from the personal side silently
        // went nowhere if the user never opened the work-side app first.
        try {
            val flags = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT or
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            val cloneFilter = android.content.IntentFilter(MainActivity.ACTION_CLONE)
            cloneFilter.addCategory(Intent.CATEGORY_DEFAULT)
            dpm.addCrossProfileIntentFilter(admin, cloneFilter, flags)
            val setupFilter = android.content.IntentFilter(MainActivity.ACTION_SETUP_PROFILE)
            setupFilter.addCategory(Intent.CATEGORY_DEFAULT)
            dpm.addCrossProfileIntentFilter(admin, setupFilter, flags)
        } catch (e: Exception) {
            // Work-side MainActivity registers the same filters as a fallback.
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                dpm.setCrossProfilePackages(admin, setOf(context.packageName))
            } catch (e: Exception) {
                // ignore
            }
        }
        val launch = Intent(context, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Toast.makeText(context, "Shina Dual profile created", Toast.LENGTH_LONG).show()
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Shina Dual admin enabled", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Removing Shina Dual will delete the work profile and all cloned apps inside it."
    }
}
