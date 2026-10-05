package com.shina.dualspace

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

class MainActivity : AppCompatActivity() {

    private enum class Section { DUAL, SECRET, PICKER }

    private lateinit var appGrid: GridView
    private lateinit var pickerList: ListView
    private lateinit var emptyText: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var profileStatus: TextView
    private lateinit var setupProfileBtn: Button

    private var allApps: List<AppEntry> = emptyList()
    private var section: Section = Section.DUAL
    private var pickerTarget: Section = Section.DUAL
    private var query: String = ""
    private var unlocked: Boolean = false

    private val prefs by lazy { getSharedPreferences("dualspace", MODE_PRIVATE) }
    private val admin: ComponentName by lazy { ComponentName(this, DualAdminReceiver::class.java) }
    private val dpm: DevicePolicyManager by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val userManager: UserManager by lazy { getSystemService(Context.USER_SERVICE) as UserManager }
    private val launcherApps: LauncherApps by lazy { getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps }

    companion object {
        const val ACTION_CLONE = "com.shina.dualspace.CLONE"
        const val ACTION_SETUP_PROFILE = "com.shina.dualspace.SETUP_PROFILE"
        const val EXTRA_PKG = "pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appGrid = findViewById(R.id.appGrid)
        pickerList = findViewById(R.id.pickerList)
        emptyText = findViewById(R.id.emptyText)
        sectionTitle = findViewById(R.id.sectionTitle)
        profileStatus = findViewById(R.id.profileStatus)
        setupProfileBtn = findViewById(R.id.setupProfileBtn)

        if (isInWorkProfile()) {
            setupWorkProfileSide()
        }

        handleIncomingIntent(intent)
        loadApps()
        updateProfileStatus()

        findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; refresh() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<Button>(R.id.tabDualBtn).setOnClickListener { section = Section.DUAL; refresh() }
        findViewById<Button>(R.id.tabSecretBtn).setOnClickListener { openSecret() }
        findViewById<Button>(R.id.addBtn).setOnClickListener {
            pickerTarget = if (section == Section.SECRET) Section.SECRET else Section.DUAL
            section = Section.PICKER
            refresh()
        }
        findViewById<Button>(R.id.lockBtn).setOnClickListener { pinMenu() }
        setupProfileBtn.setOnClickListener { onSetupButton() }

        appGrid.setOnItemClickListener { _, _, pos, _ ->
            val entry = displayedEntries().getOrNull(pos) ?: return@setOnItemClickListener
            launchCloneOrOriginal(entry.pkg)
        }
        appGrid.setOnItemLongClickListener { _, _, pos, _ ->
            val entry = displayedEntries().getOrNull(pos) ?: return@setOnItemLongClickListener true
            manageApp(entry)
            true
        }
        pickerList.setOnItemClickListener { _, _, pos, _ ->
            val entry = displayedPicker().getOrNull(pos) ?: return@setOnItemClickListener
            addTo(entry.pkg, pickerTarget)
            requestClone(entry.pkg)
            section = pickerTarget
            refresh()
        }

        refresh()

        if (hasPin() && !isInWorkProfile()) {
            verifyPin("Masukin PIN buat buka Shina Dual Space") { finish() }
        } else {
            unlocked = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        updateProfileStatus()
        if (section != Section.PICKER) refresh()
    }

    // ---- Work Profile detection ----

    private fun isInWorkProfile(): Boolean {
        return try {
            dpm.isManagedProfile(admin)
        } catch (e: Exception) {
            false
        }
    }

    private fun isProfileOwner(): Boolean {
        return try {
            dpm.isProfileOwnerApp(packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun workProfileUser(): UserHandle? {
        val myHandle = Process.myUserHandle()
        val profiles = try { userManager.userProfiles } catch (e: Exception) { emptyList() }
        if (isInWorkProfile()) return myHandle
        for (profile in profiles) {
            if (profile != myHandle) {
                // Confirm our app exists in that profile (= likely our work profile)
                try {
                    val apps = launcherApps.getActivityList(packageName, profile)
                    if (apps.isNotEmpty()) return profile
                } catch (e: Exception) {
                    // keep looking
                }
            }
        }
        // Fallback: any non-primary profile
        return profiles.firstOrNull { it != myHandle }
    }

    private fun hasWorkProfile(): Boolean = workProfileUser() != null

    private fun updateProfileStatus() {
        val inWork = isInWorkProfile()
        val hasWork = hasWorkProfile()
        when {
            inWork -> {
                profileStatus.text = "Work Profile: AKTIF (kamu lagi di dalam profil clone)"
                setupProfileBtn.text = "Profil udah aktif"
                setupProfileBtn.isEnabled = false
            }
            hasWork -> {
                profileStatus.text = "Work Profile: AKTIF ✓ clone pisah data siap"
                setupProfileBtn.text = "Work Profile udah ada"
                setupProfileBtn.isEnabled = false
            }
            else -> {
                profileStatus.text = "Work Profile: BELUM SETUP — tap tombol di bawah"
                setupProfileBtn.text = "Setup Work Profile"
                setupProfileBtn.isEnabled = true
            }
        }
    }

    private fun onSetupButton() {
        if (isInWorkProfile() || hasWorkProfile()) {
            Toast.makeText(this, "Work Profile udah aktif", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Setup Work Profile")
            .setMessage("Android bakal minta izin bikin Work Profile baru buat Shina Dual Space. Di profil itu clone app punya data & login sendiri, pisah dari app utama.\n\nCatatan: kalau HP kamu udah punya Work Profile dari kantor/Shelter/Island, setup ini bisa gagal — bilang ke Shina ya.")
            .setPositiveButton("Lanjut") { _, _ -> startProvisioning() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startProvisioning() {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                    android.os.PersistableBundle().apply {
                        putString("shina", "dual")
                    }
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mulai setup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Runs inside the work profile after provisioning
    private fun setupWorkProfileSide() {
        try {
            dpm.setProfileName(admin, "Shina Dual")
            dpm.setProfileEnabled(admin)
        } catch (e: Exception) {
            // ignore
        }
        try {
            val filter = IntentFilter(ACTION_CLONE)
            filter.addCategory(Intent.CATEGORY_DEFAULT)
            val filter2 = IntentFilter(ACTION_SETUP_PROFILE)
            filter2.addCategory(Intent.CATEGORY_DEFAULT)
            val flags = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT or DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            dpm.addCrossProfileIntentFilter(admin, filter, flags)
            dpm.addCrossProfileIntentFilter(admin, filter2, flags)
        } catch (e: Exception) {
            // ignore
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                dpm.setCrossProfilePackages(admin, setOf(packageName))
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == ACTION_CLONE) {
            val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
            if (isInWorkProfile() || isProfileOwner()) {
                cloneInsideWork(pkg)
            }
        }
    }

    private fun cloneInsideWork(pkg: String) {
        try {
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.installExistingPackage(admin, pkg)
            } else {
                @Suppress("DEPRECATION")
                dpm.installExistingPackage(admin, pkg)
            }
            Toast.makeText(this, if (ok) "Clone dibuat: $pkg" else "Clone gagal / udah ada: $pkg", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Clone gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestClone(pkg: String) {
        if (isInWorkProfile()) {
            cloneInsideWork(pkg)
            return
        }
        if (!hasWorkProfile()) {
            Toast.makeText(this, "Udah ditambahin. Setup Work Profile dulu biar jadi clone beneran (data pisah).", Toast.LENGTH_LONG).show()
            return
        }
        // Ask the work-profile side of our app to install the existing package there
        try {
            val intent = Intent(ACTION_CLONE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.putExtra(EXTRA_PKG, pkg)
            intent.setPackage(packageName)
            startActivity(intent)
            Toast.makeText(this, "Minta clone $pkg ke Work Profile...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Belum bisa clone otomatis. Buka Shina Dual Space dari Work Profile (ikon badge koper), lalu clone dari sana.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isCloned(pkg: String): Boolean {
        val work = workProfileUser() ?: return false
        if (isInWorkProfile()) return true
        return try {
            launcherApps.getActivityList(pkg, work).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCloneOrOriginal(pkg: String) {
        if (isInWorkProfile()) {
            launchOriginal(pkg)
            return
        }
        val work = workProfileUser()
        if (work != null) {
            try {
                val activities = launcherApps.getActivityList(pkg, work)
                if (activities.isNotEmpty()) {
                    val comp = activities[0].componentName
                    launcherApps.startMainActivity(comp, work, null, null)
                    return
                }
            } catch (e: Exception) {
                // fall through
            }
            // Not cloned yet
            AlertDialog.Builder(this)
                .setTitle("Belum diclone")
                .setMessage("App ini belum ada di Work Profile. Clone sekarang biar data & login-nya pisah?")
                .setPositiveButton("Clone") { _, _ -> requestClone(pkg) }
                .setNegativeButton("Buka app utama") { _, _ -> launchOriginal(pkg) }
                .show()
            return
        }
        launchOriginal(pkg)
    }

    private fun launchOriginal(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "App ini ga bisa dibuka dari sini", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ---- App list ----

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try { pm.queryIntentActivities(intent, 0) } catch (e: Exception) { emptyList() }
        val seen = HashSet<String>()
        val list = ArrayList<AppEntry>()
        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName) continue
            if (!seen.add(pkg)) continue
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                list.add(AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info)))
            } catch (e: PackageManager.NameNotFoundException) {
                // skip
            }
        }
        allApps = list.sortedBy { it.label.lowercase() }
    }

    private fun dualSet(): MutableSet<String> = HashSet(prefs.getStringSet("dual_pkgs", emptySet()) ?: emptySet())
    private fun secretSet(): MutableSet<String> = HashSet(prefs.getStringSet("secret_pkgs", emptySet()) ?: emptySet())

    private fun saveSet(key: String, set: Set<String>) {
        prefs.edit().putStringSet(key, HashSet(set)).apply()
    }

    private fun displayedEntries(): List<AppEntry> {
        val pkgs = when (section) {
            Section.SECRET -> secretSet()
            else -> dualSet()
        }
        return allApps.filter { pkgs.contains(it.pkg) }
            .filter { query.isEmpty() || it.label.lowercase().contains(query.lowercase()) }
    }

    private fun displayedPicker(): List<AppEntry> {
        val taken = dualSet() + secretSet()
        return allApps.filter { !taken.contains(it.pkg) }
            .filter { query.isEmpty() || it.label.lowercase().contains(query.lowercase()) }
    }

    private fun refresh() {
        if (section == Section.PICKER) {
            appGrid.visibility = View.GONE
            emptyText.visibility = View.GONE
            pickerList.visibility = View.VISIBLE
            sectionTitle.text = "Pilih app buat diclone"
            pickerList.adapter = PickerAdapter(displayedPicker())
            return
        }
        pickerList.visibility = View.GONE
        appGrid.visibility = View.VISIBLE
        sectionTitle.text = if (section == Section.SECRET) "Secret Zone" else "App di Dual Space"
        val data = displayedEntries()
        emptyText.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (section == Section.SECRET)
            "Secret Zone kosong. Tap + Tambah pas lagi di tab ini buat sembunyiin app di sini."
        else
            "Belum ada app. Tap + Tambah, pilih app, lalu clone ke Work Profile."
        appGrid.adapter = GridAdapter(data)
    }

    private fun addTo(pkg: String, target: Section) {
        if (target == Section.SECRET) {
            val s = secretSet(); s.add(pkg); saveSet("secret_pkgs", s)
            val d = dualSet(); d.remove(pkg); saveSet("dual_pkgs", d)
        } else {
            val d = dualSet(); d.add(pkg); saveSet("dual_pkgs", d)
            val s = secretSet(); s.remove(pkg); saveSet("secret_pkgs", s)
        }
    }

    private fun removeFrom(pkg: String) {
        val d = dualSet(); d.remove(pkg); saveSet("dual_pkgs", d)
        val s = secretSet(); s.remove(pkg); saveSet("secret_pkgs", s)
        refresh()
        Toast.makeText(this, "Dihapus dari daftar. Clone di Work Profile hapus dari Settings > Work Profile kalau mau bersih total.", Toast.LENGTH_LONG).show()
    }

    private fun manageApp(entry: AppEntry) {
        val inSecret = secretSet().contains(entry.pkg)
        val cloned = isCloned(entry.pkg)
        val moveLabel = if (inSecret) "Pindah ke Dual Space" else "Sembunyiin ke Secret Zone"
        val cloneLabel = if (cloned) "Clone ulang / cek clone" else "Clone ke Work Profile"
        val options = arrayOf("Buka (clone kalau ada)", cloneLabel, moveLabel, "Hapus dari ruang", "Batal")
        AlertDialog.Builder(this)
            .setTitle(entry.label + if (cloned) " ✓ diclone" else "")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCloneOrOriginal(entry.pkg)
                    1 -> requestClone(entry.pkg)
                    2 -> { addTo(entry.pkg, if (inSecret) Section.DUAL else Section.SECRET); refresh() }
                    3 -> removeFrom(entry.pkg)
                }
            }
            .show()
    }

    private fun openSecret() {
        if (hasPin() && !unlocked) {
            verifyPin("Masukin PIN buat buka Secret Zone") { /* stay */ }
            return
        }
        section = Section.SECRET
        refresh()
    }

    // ---- PIN ----

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((pin + ":shina-dual").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    private fun verifyPin(title: String, onFail: () -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "PIN 4-6 angka"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Buka") { _, _ ->
                if (hashPin(input.text.toString()) == prefs.getString("pin_hash", "")) {
                    unlocked = true
                    if (section == Section.SECRET) refresh()
                } else {
                    Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
                    onFail()
                }
            }
            .setNegativeButton("Batal") { _, _ -> onFail() }
            .show()
    }

    private fun setPinFlow() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "PIN baru 4-6 angka"
        AlertDialog.Builder(this)
            .setTitle("Set PIN")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val pin = input.text.toString()
                if (pin.length !in 4..6) {
                    Toast.makeText(this, "PIN harus 4-6 angka", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.edit().putString("pin_hash", hashPin(pin)).apply()
                unlocked = true
                Toast.makeText(this, "PIN aktif", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pinMenu() {
        if (!hasPin()) { setPinFlow(); return }
        val options = arrayOf("Kunci sekarang", "Ganti PIN", "Matikan PIN", "Batal")
        AlertDialog.Builder(this)
            .setTitle("Keamanan")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { unlocked = false; verifyPin("Masukin PIN buat buka lagi") { finish() } }
                    1 -> verifyPinThen { setPinFlow() }
                    2 -> verifyPinThen {
                        prefs.edit().remove("pin_hash").apply()
                        Toast.makeText(this, "PIN dimatikan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun verifyPinThen(action: () -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "PIN sekarang"
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi PIN")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (hashPin(input.text.toString()) == prefs.getString("pin_hash", "")) action()
                else Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---- Adapters ----

    private inner class GridAdapter(private val data: List<AppEntry>) : BaseAdapter() {
        override fun getCount(): Int = data.size
        override fun getItem(position: Int): Any = data[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = data[position]
            val layout = LinearLayout(this@MainActivity)
            layout.orientation = LinearLayout.VERTICAL
            layout.gravity = android.view.Gravity.CENTER
            layout.setPadding(6, 10, 6, 10)
            layout.background = getDrawable(R.drawable.bg_card)
            val icon = ImageView(this@MainActivity)
            icon.setImageDrawable(entry.icon)
            val iconSize = (44 * resources.displayMetrics.density).toInt()
            icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            layout.addView(icon)
            val label = TextView(this@MainActivity)
            val clonedMark = if (isCloned(entry.pkg)) " ✓" else ""
            label.text = entry.label + clonedMark
            label.setTextColor(0xFFFFFFFF.toInt())
            label.textSize = 11f
            label.maxLines = 1
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            label.gravity = android.view.Gravity.CENTER
            label.setPadding(2, 6, 2, 0)
            layout.addView(label)
            return layout
        }
    }

    private inner class PickerAdapter(private val data: List<AppEntry>) : BaseAdapter() {
        override fun getCount(): Int = data.size
        override fun getItem(position: Int): Any = data[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = data[position]
            val layout = LinearLayout(this@MainActivity)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.gravity = android.view.Gravity.CENTER_VERTICAL
            layout.setPadding(10, 10, 10, 10)
            val icon = ImageView(this@MainActivity)
            icon.setImageDrawable(entry.icon)
            val iconSize = (40 * resources.displayMetrics.density).toInt()
            icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            layout.addView(icon)
            val label = TextView(this@MainActivity)
            label.text = entry.label
            label.setTextColor(0xFFFFFFFF.toInt())
            label.textSize = 15f
            label.setPadding(12, 0, 0, 0)
            layout.addView(label)
            return layout
        }
    }
}
