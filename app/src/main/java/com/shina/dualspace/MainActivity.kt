package com.shina.dualspace

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
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

    private var allApps: List<AppEntry> = emptyList()
    private var section: Section = Section.DUAL
    private var pickerTarget: Section = Section.DUAL
    private var query: String = ""
    private var unlocked: Boolean = false

    private val prefs by lazy { getSharedPreferences("dualspace", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appGrid = findViewById(R.id.appGrid)
        pickerList = findViewById(R.id.pickerList)
        emptyText = findViewById(R.id.emptyText)
        sectionTitle = findViewById(R.id.sectionTitle)

        loadApps()

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

        appGrid.setOnItemClickListener { _, _, pos, _ ->
            val entry = displayedEntries().getOrNull(pos) ?: return@setOnItemClickListener
            launchApp(entry.pkg)
        }
        appGrid.setOnItemLongClickListener { _, _, pos, _ ->
            val entry = displayedEntries().getOrNull(pos) ?: return@setOnItemLongClickListener true
            manageApp(entry)
            true
        }
        pickerList.setOnItemClickListener { _, _, pos, _ ->
            val entry = displayedPicker().getOrNull(pos) ?: return@setOnItemClickListener
            addTo(entry.pkg, pickerTarget)
            section = pickerTarget
            refresh()
        }

        refresh()

        if (hasPin()) {
            verifyPin("Masukin PIN buat buka Shina Dual Space") { finish() }
        } else {
            unlocked = true
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        if (section != Section.PICKER) refresh()
    }

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
                // skip removed apps
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
            sectionTitle.text = "Pilih app buat ditambah"
            val data = displayedPicker()
            pickerList.adapter = PickerAdapter(data)
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
            "Belum ada app. Tap + Tambah buat masukin app pertamamu."
        appGrid.adapter = GridAdapter(data)
    }

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "App ini ga bisa dibuka dari sini", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun addTo(pkg: String, target: Section) {
        if (target == Section.SECRET) {
            val s = secretSet(); s.add(pkg); saveSet("secret_pkgs", s)
            val d = dualSet(); d.remove(pkg); saveSet("dual_pkgs", d)
        } else {
            val d = dualSet(); d.add(pkg); saveSet("dual_pkgs", d)
            val s = secretSet(); s.remove(pkg); saveSet("secret_pkgs", s)
        }
        Toast.makeText(this, "Ditambahin", Toast.LENGTH_SHORT).show()
    }

    private fun removeFrom(pkg: String) {
        val d = dualSet(); d.remove(pkg); saveSet("dual_pkgs", d)
        val s = secretSet(); s.remove(pkg); saveSet("secret_pkgs", s)
        refresh()
    }

    private fun manageApp(entry: AppEntry) {
        val inSecret = secretSet().contains(entry.pkg)
        val moveLabel = if (inSecret) "Pindah ke Dual Space" else "Sembunyiin ke Secret Zone"
        val options = arrayOf("Buka app", moveLabel, "Hapus dari ruang", "Batal")
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchApp(entry.pkg)
                    1 -> { addTo(entry.pkg, if (inSecret) Section.DUAL else Section.SECRET); refresh() }
                    2 -> removeFrom(entry.pkg)
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
            label.text = entry.label
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
