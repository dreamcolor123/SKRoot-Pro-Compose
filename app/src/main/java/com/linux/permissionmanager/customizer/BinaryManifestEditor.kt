package com.linux.permissionmanager.customizer

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal structured Android binary XML editor.
 *
 * It parses element/attribute chunks and only changes the manifest package,
 * application label and application-id-derived authorities/permissions. String
 * pool indices and every XML node remain stable, so component/JNI class names
 * are deliberately untouched.
 */
object BinaryManifestEditor {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val TYPE_STRING = 0x03
    private const val UTF8_FLAG = 0x00000100

    data class Result(
        val bytes: ByteArray,
        val originalPackage: String,
        val changedValues: Int,
    )

    fun rewrite(source: ByteArray, newPackage: String, newLabel: String): Result {
        require(u16(source, 0) == RES_XML_TYPE) { "AndroidManifest.xml 不是二进制 AXML" }
        val declaredSize = u32(source, 4)
        require(declaredSize in 8..source.size) { "AXML 文件头损坏" }

        var offset = u16(source, 2)
        var poolOffset = -1
        var pool: StringPool? = null
        while (offset + 8 <= declaredSize) {
            val type = u16(source, offset)
            val size = u32(source, offset + 4)
            require(size >= 8 && offset + size <= declaredSize) { "AXML chunk 损坏: $offset" }
            if (type == RES_STRING_POOL_TYPE) {
                poolOffset = offset
                pool = StringPool.parse(source, offset)
                break
            }
            offset += size
        }
        val stringPool = requireNotNull(pool) { "AXML 缺少 string pool" }
        val appendedStrings = linkedMapOf<String, Int>()
        val intPatches = mutableListOf<Pair<Int, Int>>()
        val bytePatches = mutableListOf<Pair<Int, Byte>>()
        var changedAttributes = 0
        var originalPackage: String? = null

        fun intern(value: String): Int = appendedStrings.getOrPut(value) {
            stringPool.strings.size + appendedStrings.size
        }

        offset = poolOffset + stringPool.originalSize
        while (offset + 8 <= declaredSize) {
            val type = u16(source, offset)
            val size = u32(source, offset + 4)
            require(size >= 8 && offset + size <= declaredSize) { "AXML node 损坏: $offset" }
            if (type == RES_XML_START_ELEMENT_TYPE) {
                val elementName = stringPool.string(i32(source, offset + 20))
                val attributeStart = u16(source, offset + 24)
                val attributeSize = u16(source, offset + 26)
                val attributeCount = u16(source, offset + 28)
                require(attributeSize >= 20) { "AXML attribute size 异常" }
                val attributes = offset + 16 + attributeStart
                repeat(attributeCount) { index ->
                    val attr = attributes + index * attributeSize
                    require(attr + 20 <= offset + size) { "AXML attribute 越界" }
                    val nameIndex = i32(source, attr + 4)
                    val rawIndex = i32(source, attr + 8)
                    val valueType = source[attr + 15].toInt() and 0xff
                    val typedIndex = i32(source, attr + 16)
                    val name = stringPool.string(nameIndex)

                    fun replaceValue(replacement: String) {
                        val newIndex = intern(replacement)
                        // Point only this attribute at an appended value. Keeping
                        // the original pool entry untouched avoids changing an
                        // unrelated attribute which happened to share the same
                        // deduplicated string index.
                        intPatches += (attr + 8) to newIndex
                        bytePatches += (attr + 15) to TYPE_STRING.toByte()
                        intPatches += (attr + 16) to newIndex
                        changedAttributes++
                    }

                    if (elementName == "application" && name == "label") {
                        // AAPT may encode the template label as either a raw
                        // string or a resource reference. Normalize this one
                        // attribute to the requested literal label.
                        replaceValue(newLabel)
                        return@repeat
                    }

                    val valueIndex = when {
                        rawIndex >= 0 -> rawIndex
                        valueType == TYPE_STRING && typedIndex >= 0 -> typedIndex
                        else -> -1
                    }
                    if (valueIndex < 0) return@repeat
                    val value = stringPool.string(valueIndex)
                    when {
                        elementName == "manifest" && name == "package" -> {
                            originalPackage = value
                            replaceValue(newPackage)
                        }
                        name == "authorities" && originalPackage != null -> {
                            val oldPackage = originalPackage!!
                            val rewritten = value.split(';').joinToString(";") { authority ->
                                if (authority == oldPackage || authority.startsWith("$oldPackage.")) {
                                    newPackage + authority.removePrefix(oldPackage)
                                } else authority
                            }
                            if (rewritten != value) replaceValue(rewritten)
                        }
                        (elementName == "permission" || elementName == "uses-permission") &&
                            name == "name" && originalPackage != null &&
                            (value == originalPackage!! || value.startsWith(originalPackage!! + ".")) -> {
                            replaceValue(newPackage + value.removePrefix(originalPackage!!))
                        }
                    }
                }
            }
            offset += size
        }

        val oldPackage = requireNotNull(originalPackage) { "Manifest 缺少根 package 属性" }
        require(changedAttributes > 0) { "Manifest 没有可修改字段" }
        val rebuiltPool = stringPool.rebuild(appendedStrings.keys.toList())
        val tailOffset = poolOffset + stringPool.originalSize
        val output = ByteArray(source.size - stringPool.originalSize + rebuiltPool.size)
        source.copyInto(output, 0, 0, poolOffset)
        rebuiltPool.copyInto(output, poolOffset)
        source.copyInto(output, poolOffset + rebuiltPool.size, tailOffset, source.size)
        val tailShift = rebuiltPool.size - stringPool.originalSize
        intPatches.forEach { (position, value) -> putU32(output, position + tailShift, value) }
        bytePatches.forEach { (position, value) -> output[position + tailShift] = value }
        putU32(output, 4, output.size)
        return Result(output, oldPackage, changedAttributes)
    }

    private data class StringPool(
        val strings: List<String>,
        val utf8: Boolean,
        val styleOffsets: IntArray,
        val styles: ByteArray,
        val originalSize: Int,
    ) {
        fun string(index: Int): String {
            require(index in strings.indices) { "AXML string index 越界: $index" }
            return strings[index]
        }

        fun rebuild(appended: List<String>): ByteArray {
            val values = strings + appended
            val encoded = values.map { if (utf8) encodeUtf8(it) else encodeUtf16(it) }
            val rebuiltStyleOffsets = if (styleOffsets.isEmpty()) {
                styleOffsets
            } else {
                styleOffsets + IntArray(appended.size) { -1 }
            }
            val headerSize = 28
            val offsetsSize = values.size * 4 + rebuiltStyleOffsets.size * 4
            val stringsStart = headerSize + offsetsSize
            val stringOffsets = IntArray(values.size)
            val stringData = ByteArrayOutputStream()
            encoded.forEachIndexed { index, bytes ->
                stringOffsets[index] = stringData.size()
                stringData.write(bytes)
            }
            while (stringData.size() % 4 != 0) stringData.write(0)
            val stylesStart = if (rebuiltStyleOffsets.isEmpty()) 0 else stringsStart + stringData.size()
            val totalSize = stringsStart + stringData.size() + styles.size
            val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
            out.putShort(RES_STRING_POOL_TYPE.toShort())
            out.putShort(headerSize.toShort())
            out.putInt(totalSize)
            out.putInt(values.size)
            out.putInt(rebuiltStyleOffsets.size)
            out.putInt(if (utf8) UTF8_FLAG else 0)
            out.putInt(stringsStart)
            out.putInt(stylesStart)
            stringOffsets.forEach(out::putInt)
            rebuiltStyleOffsets.forEach(out::putInt)
            out.put(stringData.toByteArray())
            out.put(styles)
            return out.array()
        }

        companion object {
            fun parse(bytes: ByteArray, offset: Int): StringPool {
                val headerSize = u16(bytes, offset + 2)
                val size = u32(bytes, offset + 4)
                val stringCount = u32(bytes, offset + 8)
                val styleCount = u32(bytes, offset + 12)
                val flags = u32(bytes, offset + 16)
                val stringsStart = u32(bytes, offset + 20)
                val stylesStart = u32(bytes, offset + 24)
                require(headerSize >= 28 && size >= headerSize) { "String pool 头损坏" }
                val utf8 = flags and UTF8_FLAG != 0
                val stringOffsets = IntArray(stringCount) { i32(bytes, offset + headerSize + it * 4) }
                val styleBase = offset + headerSize + stringCount * 4
                val styleOffsets = IntArray(styleCount) { i32(bytes, styleBase + it * 4) }
                val stringBase = offset + stringsStart
                val strings = stringOffsets.map { relative ->
                    if (utf8) decodeUtf8(bytes, stringBase + relative) else decodeUtf16(bytes, stringBase + relative)
                }
                val styles = if (stylesStart == 0) ByteArray(0) else {
                    bytes.copyOfRange(offset + stylesStart, offset + size)
                }
                return StringPool(strings, utf8, styleOffsets, styles, size)
            }
        }
    }

    private fun decodeUtf8(bytes: ByteArray, offset: Int): String {
        var cursor = offset
        val (_, afterUtf16) = readLength8(bytes, cursor)
        cursor = afterUtf16
        val (byteLength, afterBytes) = readLength8(bytes, cursor)
        cursor = afterBytes
        require(cursor + byteLength <= bytes.size) { "UTF-8 string 越界" }
        return bytes.copyOfRange(cursor, cursor + byteLength).toString(Charsets.UTF_8)
    }

    private fun decodeUtf16(bytes: ByteArray, offset: Int): String {
        val (length, cursor) = readLength16(bytes, offset)
        val byteLength = length * 2
        require(cursor + byteLength <= bytes.size) { "UTF-16 string 越界" }
        return bytes.copyOfRange(cursor, cursor + byteLength).toString(Charsets.UTF_16LE)
    }

    private fun encodeUtf8(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        writeLength8(out, value.length)
        writeLength8(out, raw.size)
        out.write(raw)
        out.write(0)
        return out.toByteArray()
    }

    private fun encodeUtf16(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_16LE)
        val out = ByteArrayOutputStream()
        writeLength16(out, value.length)
        out.write(raw)
        out.write(0)
        out.write(0)
        return out.toByteArray()
    }

    private fun readLength8(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        val first = bytes[offset].toInt() and 0xff
        return if (first and 0x80 == 0) first to (offset + 1)
        else (((first and 0x7f) shl 8) or (bytes[offset + 1].toInt() and 0xff)) to (offset + 2)
    }

    private fun readLength16(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        val first = u16(bytes, offset)
        return if (first and 0x8000 == 0) first to (offset + 2)
        else (((first and 0x7fff) shl 16) or u16(bytes, offset + 2)) to (offset + 4)
    }

    private fun writeLength8(out: ByteArrayOutputStream, length: Int) {
        require(length <= 0x7fff) { "AXML UTF-8 string 过长" }
        if (length >= 0x80) out.write((length shr 8) or 0x80)
        out.write(length and 0xff)
    }

    private fun writeLength16(out: ByteArrayOutputStream, length: Int) {
        if (length >= 0x8000) {
            writeU16(out, (length shr 16) or 0x8000)
        }
        writeU16(out, length and 0xffff)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Int = i32(bytes, offset)

    private fun i32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }
}
