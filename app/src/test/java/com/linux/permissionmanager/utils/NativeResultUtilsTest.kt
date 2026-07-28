package com.linux.permissionmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResultUtilsTest {
    @Test
    fun recognizesNativeRootKeyError() {
        assertTrue(looksLikeRootKeyFailure("start_module_web_ui_server_async: ERR_MODULE_ROOT_KEY, port:0"))
        assertTrue(looksLikeRootKeyFailure("permission denied"))
        assertFalse(looksLikeRootKeyFailure("ERR_SKROOT_ENV_NOT_INSTALL"))
    }

    @Test
    fun extractsValidWebUiPort() {
        val result = "start_module_web_ui_server_async: OK, port:38127"
        assertEquals(38127, moduleWebUiPort(result))
        assertEquals("http://127.0.0.1:38127/", moduleWebUiUrl(result))
    }

    @Test
    fun rejectsMissingOrInvalidPorts() {
        assertNull(moduleWebUiPort("port:0"))
        assertNull(moduleWebUiPort("port:65536"))
        assertNull(moduleWebUiPort("ERR_MODULE_WEB_UI_LOADER_NOT_EXIST"))
    }
}
