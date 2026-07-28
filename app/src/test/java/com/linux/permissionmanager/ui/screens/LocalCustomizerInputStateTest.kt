package com.linux.permissionmanager.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.linux.permissionmanager.ui.DEFAULT_CUSTOM_PACKAGE_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCustomizerInputStateTest {
    @Test
    fun packageInputDropsClipboardStylesAndCommitsComposition() {
        val styledPaste = TextFieldValue(
            annotatedString = AnnotatedString(
                text = "com.example.pasted",
                spanStyles = listOf(
                    AnnotatedString.Range(
                        item = SpanStyle(color = Color.Transparent),
                        start = 0,
                        end = 18,
                    ),
                ),
            ),
            selection = TextRange(18),
            composition = TextRange(0, 18),
        )

        val sanitized = sanitizePackageTextFieldValue(styledPaste)

        assertEquals("com.example.pasted", sanitized.text)
        assertEquals(TextRange(18), sanitized.selection)
        assertNull(sanitized.composition)
        assertEquals(emptyList<AnnotatedString.Range<SpanStyle>>(), sanitized.annotatedString.spanStyles)
    }

    @Test
    fun defaultPackageNameIsShortAndNeutral() {
        assertEquals("com.example.pro", DEFAULT_CUSTOM_PACKAGE_NAME)
    }

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
