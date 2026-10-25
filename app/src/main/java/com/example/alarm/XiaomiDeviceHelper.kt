package com.example.alarm

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object XiaomiDeviceHelper {

    private const val TAG = "XiaomiDeviceHelper"

    /**
     * Identifica se o dispositivo é de fabricação Xiaomi, Redmi, Poco ou opera sobre MIUI / HyperOS.
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        return manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") ||
                brand.contains("xiaomi") ||
                brand.contains("redmi") ||
                brand.contains("poco") ||
                fingerprint.contains("xiaomi") ||
                fingerprint.contains("miui") ||
                isMiuiOrHyperOs()
    }

    private fun isMiuiOrHyperOs(): Boolean {
        try {
            val propClass = Class.forName("android.os.SystemProperties")
            val getMethod = propClass.getMethod("get", String::class.java)
            val miuiVersion = getMethod.invoke(null, "ro.miui.ui.version.name") as? String
            val miuiCode = getMethod.invoke(null, "ro.miui.ui.version.code") as? String
            val hyperOs = getMethod.invoke(null, "ro.mi.os.version.name") as? String
            val hasMiui = !miuiVersion.isNullOrBlank()
            val hasMiuiCode = !miuiCode.isNullOrBlank()
            val hasHyperOs = !hyperOs.isNullOrBlank()
            return hasMiui || hasMiuiCode || hasHyperOs
        } catch (_: Throwable) {
            return false
        }
    }

    /**
     * Abre a tela nativa de 'Início Automático' (Autostart) da Xiaomi / MIUI.
     * Esta permissão é fundamental para a Xiaomi não matar os alarmes ao fechar/arrastar o app nos recentes.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
        )

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (t: Throwable) {
                Log.d(TAG, "Falha ao abrir intent de autostart candidato", t)
            }
        }
        return openAppDetails(context)
    }

    /**
     * Abre a tela de 'Outras Permissões' da Xiaomi para autorizar:
     * - 'Mostrar na tela de bloqueio'
     * - 'Exibir janelas pop-up ao executar em segundo plano'
     */
    fun openMiuiPermissions(context: Context): Boolean {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            }
        )

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (t: Throwable) {
                Log.d(TAG, "Falha ao abrir permissões da MIUI", t)
            }
        }
        return openAppDetails(context)
    }

    /**
     * Abre a solicitação para ignorar otimização de bateria ('Sem restrições').
     */
    @SuppressLint("BatteryLife")
    fun openBatterySaverSettings(context: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return true
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Falha ao solicitar REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", t)
        }

        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Throwable) {}

        return openAppDetails(context)
    }

    /**
     * Abre a tela de detalhes do aplicativo nas Configurações do Android.
     */
    fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao abrir detalhes do app", t)
            false
        }
    }
}
