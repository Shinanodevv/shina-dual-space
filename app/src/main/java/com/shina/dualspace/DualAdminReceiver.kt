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
