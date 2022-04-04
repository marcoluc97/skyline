@file:OptIn(ExperimentalUnsignedTypes::class)

package emu.skyline.applet.swkbd

import emu.skyline.utils.*
import kotlin.experimental.and

data class KeyboardMode(
    var mode : u32 = 0u
) : ByteBufferSerializable

data class InvalidCharFlag(
    var flags : u8 = 0u
) : ByteBufferSerializable{
    var space : Boolean
        get() : Boolean {
            return ((flags and 0b00000001u) != 0u.toUByte())
        }
        set(value) {
                flags = flags and 0b11111110u or (if (value) 0b00000001u else 0u)
            }
    var atMark : Boolean
        get() : Boolean {
            return ((flags and 0b00000010u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b11111101u or (if (value) 0b00000010u else 0u)
        }
    var percent : Boolean
        get() : Boolean {
            return ((flags and 0b00000100u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b11111011u or (if (value) 0b00000100u else 0u)
        }
    var slash : Boolean
        get() : Boolean {
            return ((flags and 0b00001000u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b11110111u or (if (value) 0b00001000u else 0u)
        }
    var backSlash : Boolean
        get() : Boolean {
            return ((flags and 0b00010000u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b11101111u or (if (value) 0b00010000u else 0u)
        }
    var numeric : Boolean
        get() : Boolean {
            return ((flags and 0b00100000u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b11011111u or (if (value) 0b00100000u else 0u)
        }
    var outsideOfDownloadCode : Boolean
        get() : Boolean {
            return ((flags and 0b01000000u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b10111111u or (if (value) 0b01000000u else 0u)
        }
    var outsideOfMiiNickName : Boolean
        get() : Boolean {
            return ((flags and 0b01000000u) != 0u.toUByte())
        }
        set(value) {
            flags = flags and 0b10111111u or (if (value) 0b01000000u else 0u)
        }
}

data class InitialCursorPos(
    var pos : u32 = 0u
) : ByteBufferSerializable

data class PasswordMode(
    var mode : u32 = 0u
) : ByteBufferSerializable

data class InputFormMode(
    var mode : u32 = 0u
) : ByteBufferSerializable

data class DictionaryLang(
    var lang : u16 = 0u
) : ByteBufferSerializable

data class DictionaryInfo(
    var offset : u32 = 0u,
    var size : u16 = 0u,
    var dictionaryLang : DictionaryLang = DictionaryLang()
) : ByteBufferSerializable

data class SoftwareKeyboardConfig(
    val keyboardMode : KeyboardMode = KeyboardMode(),
    @param:ByteBufferSerializable.ByteBufferSerializableArray(0x9) val okText : CharArray = CharArray(0x9),
    var leftOptionalSymbolKey : Char = '\u0000',
    var rightOptionalSymbolKey : Char  = '\u0000',
    var isPredictionEnabled : bool = false,
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0x1) val _pad0_ : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
    val invalidCharsFlags : InvalidCharFlag = InvalidCharFlag(),
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0x3) val _pad1_ : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
    val initialCursorPos : InitialCursorPos = InitialCursorPos(),
    @param:ByteBufferSerializable.ByteBufferSerializableArray(0x41) val headerText : CharArray = CharArray(0x41),
    @param:ByteBufferSerializable.ByteBufferSerializableArray(0x81) val subText : CharArray = CharArray(0x81),
    @param:ByteBufferSerializable.ByteBufferSerializableArray(0x101) val guideText : CharArray = CharArray(0x101),
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0x2) val _pad2_ : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
    var textMaxLength : u32 = 0u,
    var textMinLength : u32 = 0u,
    val passwordMode : PasswordMode = PasswordMode(),
    val inputFormMode : InputFormMode = InputFormMode(),
    var isUseNewLine : bool = false,
    var isUseUtf8 : bool = false,
    var isUseBlurBackground : bool = false,
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0x1) val _pad3_ : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
    var initialStringOffset : u32 = 0u,
    var initialStringLength : u32 = 0u,
    var userDictionaryOffset : u32 = 0u,
    var userDictionaryNum : u32 = 0u,
    var isUseTextCheck : bool = false,
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0x3) val reserved0 : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
    @param:ByteBufferSerializable.ByteBufferSerializableArray(0x8) val separateTextPos : u32Array = u32Array(0x8),
    @param:ByteBufferSerializable.ByteBufferSerializableArray(0x18) val customizedDicInfoList : Array<DictionaryInfo> = Array(0x18){ DictionaryInfo() },
    var customizedDicCount : u8 = 0u,
    var isCancelButtonDisabled : bool = false,
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0xD) val reserved1 : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
    var trigger : u8 = 0u,
    @param:ByteBufferSerializable.ByteBufferSerializablePadding(0x4) val reserved2 : ByteBufferSerializable.ByteBufferPadding = ByteBufferSerializable.ByteBufferPadding,
) : ByteBufferSerializable