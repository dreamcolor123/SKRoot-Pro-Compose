package com.linux.permissionmanager.utils

import java.util.Locale

/** Detect explicit key errors without treating an uninstalled environment as a bad key. */
fun looksLikeRootKeyFailure(message: String?): Boolean {
    val value = message?.lowercase(Locale.ROOT).orEmpty()
    if (value.isBlank()) return false
    return listOf(
        "err_module_root_key",
        "invalid root key",
        "invalid rootkey",
        "root key invalid",
        "wrong root key",
        "bad root key",
        "rootkey_invalid",
        "invalid_key",
        "invalid key",
        "permission denied",
        "unauthorized",
    ).any(value::contains)
}

private val moduleWebUiPortPattern = Regex("""(?i)\bport\s*:\s*(\d{1,5})\b""")

fun moduleWebUiPort(message: String?): Int? = moduleWebUiPortPattern
    .find(message.orEmpty())
    ?.groupValues
    ?.getOrNull(1)
    ?.toIntOrNull()
    ?.takeIf { it in 1..65535 }

fun moduleWebUiUrl(message: String?): String? = moduleWebUiPort(message)?.let { port ->
    "http://127.0.0.1:$port/"
}
