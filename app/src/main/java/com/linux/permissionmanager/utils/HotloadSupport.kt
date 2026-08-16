package com.linux.permissionmanager.utils

enum class EnvironmentInstallMode(val nativeValue: String) {
    BOOT("Boot"),
    HOTLOAD_REBOOT("HotLoadReboot"),
    HOTLOAD_NO_REBOOT("HotLoadNoReboot"),
}

object HotloadSupport {
    const val CVE_2026_43499 = "CVE-2026-43499"
    const val GHOSTLOCK_LIBRARY = "libcve2026_43499_ghostlock.so"
    const val DEFAULT_SCRIPT_TIMEOUT_SECONDS = 70L
    const val CVE_SCRIPT_TIMEOUT_SECONDS = 180L

    fun isCve2026Method(method: String): Boolean =
        method.trim().equals(CVE_2026_43499, ignoreCase = true)

    fun installMode(hotload: Boolean, method: String): EnvironmentInstallMode = when {
        !hotload -> EnvironmentInstallMode.BOOT
        isCve2026Method(method) -> EnvironmentInstallMode.HOTLOAD_NO_REBOOT
        else -> EnvironmentInstallMode.HOTLOAD_REBOOT
    }

    fun scriptTimeoutSeconds(method: String): Long =
        if (isCve2026Method(method)) CVE_SCRIPT_TIMEOUT_SECONDS else DEFAULT_SCRIPT_TIMEOUT_SECONDS

    fun prepareScript(method: String, nativeLibraryDir: String, payload: String): String {
        if (!isCve2026Method(method)) return payload
        val executable = "$nativeLibraryDir/$GHOSTLOCK_LIBRARY"
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("SCRIPT_PATH=\$(realpath \"\$0\")")
            appendLine("echo \"[DEBUG] SCRIPT_PATH=[\$SCRIPT_PATH]\"")
            appendLine("echo \"[DEBUG] size=\$(wc -c < \"\$SCRIPT_PATH\")\"")
            appendLine("if [ \"\$(id -u)\" -ne 0 ]; then")
            appendLine("    echo \"[+] Currently not in root privileges, requesting root authorization to rerun the script...\"")
            appendLine("    echo \"[DEBUG] argv1 pre-check: \$(stat -c '%s' \"\$SCRIPT_PATH\" 2>&1)\"")
            appendLine("    ${shellQuote(executable)} \"\$SCRIPT_PATH\" \"\$SCRIPT_PATH\"")
            appendLine("    exit 0")
            appendLine("fi")
            append(payload)
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
