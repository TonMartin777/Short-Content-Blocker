package com.tonma.ShortContentBlocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLimitService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    @Volatile private var activeForegroundPackage = ""
    @Volatile private var isCurrentlyInInstagram = false

    private var secondsSpentGlobal = 0
    private var secondsSpentReels = 0

    // 1. Detecta si se entra a un Reel a pantalla completa
    private fun isStrictReels(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isVisibleToUser) {
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            if (desc == "crear un reel" || desc == "create a reel" ||
                desc == "pausar vídeo" || desc == "pause video" ||
                desc == "reproducir vídeo" || desc == "play video" ||
                desc == "toca dos veces para reproducir o pausar" || desc == "double tap to play or pause" ||
                desc == "audio original" || desc == "original audio" ||
                desc == "usar audio" || desc == "use audio"
            ) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (isStrictReels(node.getChild(i))) return true
        }
        return false
    }

    // 2. Mantiene el estado en los comentarios de un Reel
    private fun isReelsSubMenu(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""

        if (text == "responder" || desc == "responder" ||
            text == "reply" || desc == "reply" ||
            text == "comentarios" || desc == "comentarios" ||
            text == "comments" || desc == "comments"
        ) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isReelsSubMenu(node.getChild(i))) return true
        }
        return false
    }

    // 3. EL CORTAFUEGOS: Detecta que estamos en el Feed, Perfil o Mensajes
    private fun isOutsideReelsContext(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isVisibleToUser) {
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val hint = if (android.os.Build.VERSION.SDK_INT >= 26) {
                node.hintText?.toString()?.lowercase()?.trim() ?: ""
            } else { "" }

            // Elementos de la barra global inferior y cabeceras
            if (desc == "inicio" || desc == "home" ||
                desc == "buscar y explorar" || desc == "search and explore" ||
                desc == "perfil" || desc == "profile" ||
                desc == "tu historia" || desc == "your story" ||
                desc == "videollamada" || desc == "video call" ||
                desc == "llamada de audio" || desc == "audio call" ||
                hint == "mensaje..." || hint == "message..."
            ) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (isOutsideReelsContext(node.getChild(i))) return true
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != null) {
                activeForegroundPackage = pkg
                if (activeForegroundPackage != "com.instagram.android" && activeForegroundPackage != "com.tonma.ShortContentBlocker") {
                    isCurrentlyInInstagram = false
                    stopTimer()
                }
            }
        }

        val sharedPref = getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedLockDate = sharedPref.getString("lock_date", "")

        if (activeForegroundPackage == "com.tonma.ShortContentBlocker") {
            if (savedLockDate == todayDate) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val limitInstagramMain = sharedPref.getBoolean("limit_instagram_main", false)
            val limitReelsEnabled = sharedPref.getBoolean("limit_reels_enabled", false)

            if (!limitInstagramMain && !limitReelsEnabled) return

            val savedSecondsDate = sharedPref.getString("date_of_seconds", "")

            if (todayDate != savedSecondsDate) {
                secondsSpentGlobal = 0
                secondsSpentReels = 0
                sharedPref.edit()
                    .putInt("seconds_spent_global_today", 0)
                    .putInt("seconds_spent_reels_today", 0)
                    .putString("date_of_seconds", todayDate)
                    .apply()
            } else {
                secondsSpentGlobal = sharedPref.getInt("seconds_spent_global_today", 0)
                secondsSpentReels = sharedPref.getInt("seconds_spent_reels_today", 0)
            }

            if (activeForegroundPackage == "com.instagram.android") {
                if (!isCurrentlyInInstagram) {
                    isCurrentlyInInstagram = true
                    startTimer(limitInstagramMain, limitReelsEnabled)
                }
            }
        }
    }

    private fun startTimer(limitInstagramMain: Boolean, limitReelsEnabled: Boolean) {
        if (timerJob?.isActive == true) return

        timerJob = serviceScope.launch {
            val sharedPref = applicationContext.getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)
            val globalLimitSeconds = sharedPref.getInt("instagram_global_minutes", 0) * 60
            val reelsLimitSeconds = sharedPref.getInt("reels_minutes", 0) * 60

            var nullTolerance = 0
            var inReelsContext = false
            var reelsTransitionTolerance = 0

            while (isActive) {
                delay(1000)

                val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!powerManager.isInteractive) {
                    isCurrentlyInInstagram = false
                    activeForegroundPackage = ""
                    break
                }

                val root = try { rootInActiveWindow } catch (e: Exception) { null }
                val rootPkg = root?.packageName?.toString()

                val strictReels = isStrictReels(root)
                val subMenuOpened = isReelsSubMenu(root)
                val outOfReels = isOutsideReelsContext(root) // Invocamos el cortafuegos
                root?.recycle()

                if (rootPkg != null && rootPkg != "com.instagram.android") {
                    Log.d("BLOQUEADOR", "Foco en otra app ($rootPkg). Deteniendo contadores.")
                    isCurrentlyInInstagram = false
                    activeForegroundPackage = rootPkg
                    break
                }

                if (rootPkg == null) {
                    nullTolerance++
                    if (nullTolerance >= 2) {
                        Log.d("BLOQUEADOR", "App en segundo plano confirmada. Deteniendo.")
                        isCurrentlyInInstagram = false
                        activeForegroundPackage = ""
                        break
                    }
                    continue
                }

                nullTolerance = 0

                // --- MÁQUINA DE ESTADOS BLINDADA ---
                if (strictReels) {
                    // 1. Si vemos el vídeo, estamos en Reels.
                    inReelsContext = true
                    reelsTransitionTolerance = 0
                } else if (outOfReels) {
                    // 2. CORTAFUEGOS: Si no hay vídeo y vemos la interfaz global, RUPTURA INMEDIATA.
                    inReelsContext = false
                    reelsTransitionTolerance = 0
                } else if (subMenuOpened && inReelsContext) {
                    // 3. Si vemos comentarios y no hemos activado el cortafuegos, seguimos dentro.
                    inReelsContext = true
                    reelsTransitionTolerance = 0
                } else {
                    // 4. Transiciones (animaciones, cargas de internet...)
                    if (inReelsContext) {
                        reelsTransitionTolerance++
                        Log.d("BLOQUEADOR", "Transición en Reels detectada. Tolerancia: $reelsTransitionTolerance / 2")
                        if (reelsTransitionTolerance >= 2) {
                            inReelsContext = false
                        }
                    }
                }

                var blockTriggered = false

                if (limitInstagramMain) {
                    secondsSpentGlobal++
                    sharedPref.edit().putInt("seconds_spent_global_today", secondsSpentGlobal).apply()
                    Log.d("BLOQUEADOR", "Tiempo Global: $secondsSpentGlobal / $globalLimitSeconds")
                    if (globalLimitSeconds > 0 && secondsSpentGlobal >= globalLimitSeconds) {
                        blockTriggered = true
                    }
                }

                if (limitReelsEnabled && inReelsContext) {
                    secondsSpentReels++
                    sharedPref.edit().putInt("seconds_spent_reels_today", secondsSpentReels).apply()
                    Log.d("BLOQUEADOR", "Tiempo Reels: $secondsSpentReels / $reelsLimitSeconds")
                    if (reelsLimitSeconds > 0 && secondsSpentReels >= reelsLimitSeconds) {
                        blockTriggered = true
                    }
                }

                if (blockTriggered) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(150)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    isCurrentlyInInstagram = false
                    activeForegroundPackage = ""
                    break
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}