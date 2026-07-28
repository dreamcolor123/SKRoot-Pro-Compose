package com.linux.permissionmanager.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCustomizerInputStateTest {
    @Test
    fun identicalViewModelTextPreservesImeCompositionAndSelection() {
        val pasted = TextFieldValue(
            text = "com.example.pasted",
            selection = TextRange(4, 11),
            composition = TextRange(0, 18),
        )

        val synchronized = synchronizeImeTextFieldValue(pasted, "com.example.pasted")

        assertSame(pasted, synchronized)
        assertEquals(TextRange(4, 11), synchronized.selection)
        assertEquals(TextRange(0, 18), synchronized.composition)
    }

    @Test
    fun genuinelyChangedExternalTextReplacesBufferAtEnd() {
        val composing = TextFieldValue(
            text = "old.value",
            selection = TextRange(3),
            composition = TextRange(0, 3),
        )

        val synchronized = synchronizeImeTextFieldValue(composing, "com.updated")

        assertEquals("com.updated", synchronized.text)
        assertEquals(TextRange(11), synchronized.selection)
        assertNull(synchronized.composition)
    }
}
