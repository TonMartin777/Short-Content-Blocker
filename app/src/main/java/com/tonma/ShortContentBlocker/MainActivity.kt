package com.tonma.ShortContentBlocker

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPref: SharedPreferences

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val savedLockDate = sharedPref.getString("lock_date", "")

        if (savedLockDate == todayDate) {
            android.widget.Toast.makeText(this, "Límites aplicados. Vuelve mañana.", android.widget.Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }
        setContentView(R.layout.activity_main)

        val switchInstagramMain = findViewById<MaterialSwitch>(R.id.switch_instagram_main)
        val layoutInstagramOptions = findViewById<LinearLayout>(R.id.layout_instagram_options)
        val etInstagramGlobalTime = findViewById<EditText>(R.id.et_instagram_global_time)
        val switchReelsLimit = findViewById<MaterialSwitch>(R.id.switch_reels_limit)
        val etReelsTime = findViewById<EditText>(R.id.et_reels_time)
        val btnApply = findViewById<MaterialButton>(R.id.btn_apply)

        val isInstagramLimited = sharedPref.getBoolean("limit_instagram_main", false)
        switchInstagramMain.isChecked = isInstagramLimited
        layoutInstagramOptions.visibility = if (isInstagramLimited) View.VISIBLE else View.GONE

        etInstagramGlobalTime.setText(sharedPref.getInt("instagram_global_minutes", 0).toString())
        switchReelsLimit.isChecked = sharedPref.getBoolean("limit_reels_enabled", false)
        etReelsTime.setText(sharedPref.getInt("reels_minutes", 0).toString())

        switchInstagramMain.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("limit_instagram_main", isChecked).apply()
            layoutInstagramOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        switchReelsLimit.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("limit_reels_enabled", isChecked).apply()
        }

        btnApply.setOnClickListener {
            val instagramGlobalMinutes = etInstagramGlobalTime.text.toString().toIntOrNull() ?: 0
            val reelsMinutes = etReelsTime.text.toString().toIntOrNull() ?: 0

            if (reelsMinutes > instagramGlobalMinutes && instagramGlobalMinutes > 0) {
                Toast.makeText(this, "El límite de Reels no puede superar el límite global", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

            sharedPref.edit()
                .putInt("instagram_global_minutes", instagramGlobalMinutes)
                .putInt("reels_minutes", reelsMinutes)
                .putString("lock_date", todayDate) // <-- LA CLAVE DEL BLOQUEO DIARIO
                .apply()

            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()

            // Cierre total y expulsión
            finish()
        }
    }
}