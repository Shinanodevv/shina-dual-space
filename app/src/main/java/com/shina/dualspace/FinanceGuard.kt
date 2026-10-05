package com.shina.dualspace

object FinanceGuard {
    // Well-known Indonesian banking / e-wallet packages. Used only to warn the
    // user before cloning — banks may treat a fresh Work Profile session as a
    // high-risk signal. This is a warning list, not a block list.
    private val knownFinancePackages = setOf(
        "com.bca.mybca.omni.android",
        "com.bca",
        "id.co.bca.mybca.omni.android",
        "id.co.bri.brimo",
        "com.bankmandiri.livin",
        "id.bni.mbanking",
        "com.jenius.btpn",
        "id.dana",
        "ovo.id",
        "id.ovo.android",
        "com.gojek.gopay",
        "com.shopeepay.wallet",
        "linkaja.id",
        "id.linkaja.wallet",
        "com.paytren.app",
        "id.co.cimbniaga.mobile",
        "com.cimbniaga.mobile",
        "id.permata.mobile",
        "com.dbs.id.ptbankdbss",
        "id.co.mybank.digitalbanking"
    )

    private val financeKeywords = listOf(
        "bank", "bca", "bri", "bni", "mandiri", "dana", "ovo", "gopay",
        "e-wallet", "ewallet", "wallet", "pay", "jenius", "livin", "brimo"
    )

    fun isFinanceApp(pkg: String, label: String = ""): Boolean {
        val p = pkg.lowercase()
        if (knownFinancePackages.contains(pkg)) return true
        if (p.contains("bank") || p.contains(".bca") || p.contains("dana") || p.contains("ovo")) return true
        val l = label.lowercase()
        return financeKeywords.any { l.contains(it) } && (l.contains("bank") || l.contains("wallet") || l.contains("pay"))
    }
}
