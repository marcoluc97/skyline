@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalUnsignedTypes::class)

package emu.skyline.utils

import java.nio.ByteBuffer
import kotlin.Array
import kotlin.BooleanArray
import kotlin.ByteArray
import kotlin.Char
import kotlin.CharArray
import kotlin.DoubleArray
import kotlin.FloatArray
import kotlin.IntArray
import kotlin.LongArray
import kotlin.ShortArray
import kotlin.reflect.*
import kotlin.reflect.full.*
import java.lang.reflect.Array as javaArray

typealias bool = Boolean
typealias u8 = UByte
typealias u16 = UShort
typealias u32 = UInt
typealias u64 = ULong
typealias float = Float
typealias double = Double
typealias boolArray = BooleanArray
typealias u8Array = UByteArray
typealias u16Array = UShortArray
typealias u32Array = UIntArray
typealias u64Array = ULongArray
typealias floatArray = FloatArray
typealias doubleArray = DoubleArray

/**
 * Interface for serializing data classes to and from a ByteBuffer.
 */
interface ByteBufferSerializable {
    /**
     * Object for representing padding in the byte buffer.
     */
    object ByteBufferPadding
    /**
     * Used to hold information about the size of the object for later reuse.
     * @param kClass The class of the object.
     * @param size The size of the object.
     * @param length The number of elements in an array object
     * @param elementClass The class of the elements in an array object.
     */
    private data class ClassAndSize(val kClass : KClass<*>, val size : Int, val length : Int = 1, val elementClass : KClass<*>? = null)
    /**
     * Holds information about the constructor of the classes for serialization
     * @param bytes The bytes the object should use when serialized.
     * @param classesAndSizes The classes and sizes of the arguments in the constructor
     * @param properties The properties matching the constructor fields in order, used for setting.
     */
    private data class ByteBufferSerializationData(val bytes : Int, val classesAndSizes : Array<ClassAndSize>, val properties : Array<KProperty1<*, *>>)

    /**
     * Annotations required for arrays to mark the length.
     * @param length The length of the array.
     */
    annotation class ByteBufferSerializableArray(val length : Int)
    /**
     * Annotations required for padding to mark the number of bytes they use.
     * @param bytes The number of bytes the padding uses.
     */
    annotation class ByteBufferSerializablePadding(val bytes : Int)
    /**
     * Exceptions thrown when serialization fails.
     */
    class WrongBufferSizeException(val kClass : KClass<*>, val expected : Int, val given : Int, cause : Throwable = Throwable()) : Exception("Serialization of ${kClass.simpleName} expected $expected bytes, but only $given were given", cause)
    class NotADataClassException(val kClass : KClass<*>, cause : Throwable = Throwable()) : Exception("${kClass.simpleName} is not a data class and can't be serialized", cause)
    class NotByteBufferSerializableException(val kClass : KClass<*>, cause : Throwable = Throwable()) : Exception("${kClass.simpleName} is not Serializable", cause)
    class NoArraySizeException(val param : KParameter, cause : Throwable = Throwable()) : Exception("Array parameter ${param.name} isn't annotated as a ByteBufferSerializableArray", cause)
    class NoPaddingSizeException(val param : KParameter, cause : Throwable = Throwable()) : Exception("Padding parameter ${param.name} isn't annotated as a ByteBufferSerializablePadding", cause)
    class InvalidStateException(val kProperty : KProperty1<*, *>, val expected_length : Int, val found_length : Int, cause : Throwable = Throwable()) : Exception("Property ${kProperty.name} expected to hold an array of length $expected_length, but an array of $found_length was found", cause)
    class SerializationSizeMismatch(val kClass : KClass<*>, val expected : Int, val used: Int, cause : Throwable = Throwable()) : Exception("Serialization of ${kClass.simpleName} expected to use $expected bytes, but only $used were used", cause)

    /**
     * companion object for caching information about the serialization of classes.
     */
    companion object {
        /*
         * The cache of serialization data.
         */
        private val precalcData = HashMap<KClass<*>, ByteBufferSerializationData>()
        /**
         * Get the serialization data for a class.
         * @param kClass The class to get the serialization data for.
         * @return The serialization data for the class.
         */
        private fun getSerializationData(kClass : KClass<*>) : ByteBufferSerializationData {
            val prev = precalcData[kClass]
            // If the class is already in the cache, return it, otherwise calculate it.
            if (prev != null) {
                return prev
            }
            // If the class doesn't implement ByteBufferSerializable, throw an exception.
            if (!kClass.isSubclassOf(ByteBufferSerializable::class)) {
                throw  (NotByteBufferSerializableException(kClass))
            }
            //If the class isn't a data class, throw an exception.
            if (!kClass.isData) {
                throw (NotADataClassException(kClass))
            }
            //The parameters of the constructor, used to get all the serialization data.
            val constructorParams = kClass.primaryConstructor!!.parameters
            //the properties of the class, used to set the values of the object, need to be sorted.
            val classProperties = kClass.declaredMemberProperties

            //sorted properties, sorted according to the order in the constructor parameters.
            val properties = Array<KProperty1<*, *>>(constructorParams.size) { index -> classProperties.first { property -> property.name == (constructorParams[index].name) && constructorParams[index].name != null} }
            //the number of bytes used for the serialization.
            var bytes = 0
            //the array of serialization data for the constructor parameters.
            val classesAndSizes = Array(constructorParams.size) { index ->
                //the current parameter.
                val param = constructorParams[index]
                //the classifier of the parameter.
                val classifier = param.type.classifier!!.starProjectedType.classifier
                //the length of the array, 1 if the parameter isn't an array.
                var length = 1
                var elementClass : KClass<*>? = null
                //get the size and length of the parameter.
                val size = when (classifier) {
                    bool::class -> {
                        1
                    }
                    u8::class -> {
                        1
                    }
                    u16::class -> {
                        2
                    }
                    Char::class -> {
                        2
                    }
                    u32::class -> {
                        4
                    }
                    u64::class -> {
                        8
                    }
                    float::class -> {
                        4
                    }
                    double::class -> {
                        8
                    }
                    //for array parameters, get the length of the array from the annotation and the class of the array elements.
                    boolArray::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        1
                    }
                    u8Array::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        1
                    }
                    u16Array::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        2
                    }
                    CharArray::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        2
                    }
                    u32Array::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        4
                    }
                    u64Array::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        8
                    }
                    floatArray::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        4
                    }
                    doubleArray::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        8
                    }
                    Array::class -> {
                        length = param.findAnnotation<ByteBufferSerializableArray>()?.length ?: throw(NoArraySizeException(param))
                        elementClass = param.type.arguments[0].type!!.classifier as KClass<*>
                        try {
                            getSerializationData(elementClass).bytes
                        } catch (e : Exception) {
                            throw NotByteBufferSerializableException(kClass, e)
                        }
                    }
                    //for padding, get the size of the padding from the annotation.
                    ByteBufferPadding::class -> {
                        param.findAnnotation<ByteBufferSerializablePadding>()?.bytes ?: throw(NoPaddingSizeException(param))
                    }
                    //get data recursively for [ByteBufferSerializable] classes, throw an exception otherwise.
                    else -> {
                        try {
                            getSerializationData(classifier as KClass<*>).bytes
                        } catch (e : Exception) {
                            throw NotByteBufferSerializableException(kClass, e)
                        }
                    }
                }
                //update the total size of the class.
                bytes += size * length
                ClassAndSize(classifier as KClass<*>, size, length, elementClass)
            }
            val result = ByteBufferSerializationData(bytes, classesAndSizes, properties)
            //cache the result.
            precalcData[kClass] = result
            return result
        }

        /**
         * Creates an object of the kClass Class from a ByteBuffer.
         * @param kClass the class of the object to create.
         * @param buff the ByteBuffer to read from.
         * @param ignoreRemaining if true, and exception will not be thrown if the buffer size exceeds the bytes needed to create the object.
         * @return the Object created from the ByteBuffer.
         */
        fun createFromByteBuffer(kClass : KClass<*>, buff : ByteBuffer, ignoreRemaining : Boolean = false) : ByteBufferSerializable {
            //get the serialization data for the class.
            val serializationData = getSerializationData(kClass)
            //get the start position in the buffer, to check for remaining bytes and check actually used bytes after serialization.
            val startPos = buff.position()
            //throw an exception if the buffer is too small or too big and ignoreRemaining is false.
            if (serializationData.bytes > buff.remaining() || (!ignoreRemaining && buff.remaining() > serializationData.bytes)) {
                throw WrongBufferSizeException(kClass, serializationData.bytes, buff.remaining())
            }
            val classesAndSizes = serializationData.classesAndSizes
            //fill the constructor parameters with the data from the buffer.
            val args = Array(classesAndSizes.size) { index ->
                val size = classesAndSizes[index].size
                val length = classesAndSizes[index].length
                val elementClass = classesAndSizes[index].elementClass
                when (val paramClass = classesAndSizes[index].kClass) {
                    bool::class -> {
                        buff.get() != 0.toByte()
                    }
                    u8::class -> {
                        buff.get().toUByte()
                    }
                    u16::class -> {
                        buff.short.toUShort()
                    }
                    Char::class -> {
                        buff.char
                    }
                    u32::class -> {
                        buff.int.toUInt()
                    }
                    u64::class -> {
                        buff.long.toULong()
                    }
                    float::class -> {
                        buff.float
                    }
                    double::class -> {
                        buff.double
                    }
                    boolArray::class -> {
                        val temp = BooleanArray(length)
                        for (i in temp.indices) {
                            temp[i] = buff.get() != 0.toByte()
                        }
                        temp
                    }
                    //for primitive Array types, use the bulk get methods.
                    u8Array::class -> {
                        val temp = u8Array(length)
                        buff.get(temp.asByteArray())
                        temp
                    }
                    u16Array::class -> {
                        val temp = u16Array(length)
                        buff.asShortBuffer().get(temp.asShortArray())
                        buff.position(buff.position() + length * size)
                        temp
                    }
                    CharArray::class -> {
                        val temp = CharArray(length)
                        buff.asCharBuffer().get(temp)
                        buff.position(buff.position() + length * size)
                        temp

                    }
                    u32Array::class -> {
                        val temp = u32Array(length)
                        buff.asIntBuffer().get(temp.asIntArray())
                        buff.position(buff.position() + length * size)
                        temp
                    }
                    u64Array::class -> {
                        val temp = u64Array(length)
                        buff.asLongBuffer().get(temp.asLongArray())
                        buff.position(buff.position() + length * size)
                        temp
                    }
                    floatArray::class -> {
                        val temp = floatArray(length)
                        buff.asFloatBuffer().get(temp)
                        buff.position(buff.position() + length * size)
                        temp
                    }
                    doubleArray::class -> {
                        val temp = doubleArray(length)
                        buff.asDoubleBuffer().get(temp)
                        buff.position(buff.position() + length * size)
                        temp
                    }
                    Array::class -> {
                        //use Java reflection to create the array.
                        val array = javaArray.newInstance(elementClass!!.java, length)
                        //construct elements of the array.
                        for (i in IntRange(0, length - 1)) {
                            javaArray.set(array, i, createFromByteBuffer(elementClass, buff, true))
                        }
                        (Array::class.createType(listOf(KTypeProjection.invariant(elementClass.starProjectedType))).classifier as KClass<*>).cast(array)
                    }
                    //for padding, skip the bytes and use the [ByByteBufferPadding] object.
                    ByteBufferPadding::class -> {
                        buff.position(buff.position() + size)
                        ByteBufferPadding
                    }
                    else -> {
                        //recursively call createFromByteBuffer to construct the sub objects.
                        paramClass.cast(createFromByteBuffer(paramClass, buff, true))
                    }
                }

            }
            //check that the used bytes are equal to the expected size of the object.
            if (buff.position() - startPos != serializationData.bytes) {
                throw SerializationSizeMismatch(kClass, serializationData.bytes, buff.position() - startPos)
            }
            //construct the object and return it.
            return kClass.primaryConstructor!!.call(*args) as ByteBufferSerializable
        }
    }
    /**
     * Sets this object's state from a ByteBuffer.
     * @param buff the buffer to read from.
     * @param ignoreRemaining if true, an exception will not be thrown if the buffer would have remaining bytes after serialization.
     */
    fun setFromByteBuffer(buff : ByteBuffer, ignoreRemaining : Boolean = false) {
        //get the serialization data for this object's class.
        val serializationData = getSerializationData(this::class)
        //get the start position in the buffer, to check for remaining bytes and check actually used bytes after serialization.
        val startPos = buff.position()
        //throw an exception if the buffer is too small or too big and ignoreRemaining is false.
        if (serializationData.bytes > buff.remaining() || (!ignoreRemaining && buff.remaining() > serializationData.bytes)) {
            throw WrongBufferSizeException(this::class, serializationData.bytes, buff.remaining())
        }
        val properties = serializationData.properties
        //set each of the properties using the data from the buffer.
        serializationData.classesAndSizes.forEachIndexed { index, classAndSize ->
            val property = properties[index]
            val length = classAndSize.length
            val size = classAndSize.size
            val elementClass = classAndSize.elementClass
            when (classAndSize.kClass) {
                bool::class -> {
                    val temp = buff.get() != 0.toByte()
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                u8::class -> {
                    val temp = buff.get().toUByte()
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                u16::class -> {
                    val temp = buff.short.toUShort()
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                Char::class -> {
                    val temp = buff.char
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                u32::class -> {
                    val temp = buff.int.toUInt()
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                u64::class -> {
                    val temp = buff.long.toULong()
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                float::class -> {
                    val temp = buff.float
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                double::class -> {
                    val temp = buff.double
                    (property as? KMutableProperty1<*, *>)?.setter?.call(this, temp)
                }
                boolArray::class -> {
                    val temp = property.getter.call(this) as boolArray
                    if (temp.size == length) {
                        for (i in temp.indices) {
                            temp[i] = buff.get() != 0.toByte()
                        }
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                //for primitive arrays, use the bulk get methods to get the data.
                u8Array::class -> {
                    val temp = property.getter.call(this) as u8Array
                    if (temp.size == length) {
                        buff.get(temp.asByteArray())
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                u16Array::class -> {
                    val temp = property.getter.call(this) as u16Array
                    if (temp.size == length) {
                        buff.asShortBuffer().get(temp.asShortArray())
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                CharArray::class -> {
                    val temp = property.getter.call(this) as CharArray
                    if (temp.size == length) {
                        buff.asCharBuffer().get(temp)
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                u32Array::class -> {
                    val temp = property.getter.call(this) as u32Array
                    if (temp.size == length) {
                        buff.asIntBuffer().get(temp.asIntArray())
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                u64Array::class -> {
                    val temp = property.getter.call(this) as u64Array
                    if (temp.size == length) {
                        buff.asLongBuffer().get(temp.asLongArray())
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                floatArray::class -> {
                    val temp = property.getter.call(this) as floatArray
                    if (temp.size == length) {
                        buff.asFloatBuffer().get(temp)
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                doubleArray::class -> {
                    val temp = property.getter.call(this) as doubleArray
                    if (temp.size == length) {
                        buff.asDoubleBuffer().get(temp)
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                //set each of the array's elements individually.
                Array::class -> {
                    val temp = property.getter.call(this) as Array<*>
                    if (temp.size == length) {
                        temp.forEach { curr -> (elementClass!!.cast(curr) as ByteBufferSerializable).setFromByteBuffer(buff, true) }
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                //for padding, just skip the bytes.
                ByteBufferPadding::class -> {
                    buff.position(buff.position() + size)

                }
                else -> {
                    (property.getter.call(this) as ByteBufferSerializable).setFromByteBuffer(buff, true)
                }

            }
        }
        //check that the used bytes are equal to the expected size of the object.
        if (buff.position() - startPos != serializationData.bytes) {
            throw SerializationSizeMismatch(this::class, serializationData.bytes, buff.position() - startPos)
        }
    }
    /*
    * Write this object to a ByteBuffer.
    * @param buff The buffer to write to.
    * @param ignoreRemaining If true, an exception will not be thrown if the buffer would have remaining bytes after writing the object.
     */
    fun writeToByteBuffer(buff : ByteBuffer, ignoreRemaining : Boolean = false) {
        //get the serialization data for this object's class.
        val serializationData = getSerializationData(this::class)
        //get the start position in the buffer, to check for remaining bytes and check actually used bytes after serialization.
        val startPos = buff.position()
        //throw an exception if the buffer is too small or too big and ignoreRemaining is false.
        if (serializationData.bytes > buff.remaining() || (!ignoreRemaining && buff.remaining() > serializationData.bytes)) {
            throw WrongBufferSizeException(this::class, serializationData.bytes, buff.remaining())
        }
        val properties = serializationData.properties
        //write each of the properties' data to the buffer.
        serializationData.classesAndSizes.forEachIndexed { index, classAndSize ->
            val property = properties[index]
            val length = classAndSize.length
            val size = classAndSize.size
            val elementClass = classAndSize.elementClass
            when (classAndSize.kClass) {
                bool::class -> {
                    buff.put(if (property.getter.call(this) as Boolean) 1 else 0)
                }
                u8::class -> {
                    buff.put((property.getter.call(this) as u8).toByte())
                }
                u16::class -> {
                    buff.putShort((property.getter.call(this) as u16).toShort())
                }
                Char::class -> {
                    buff.putChar(property.getter.call(this) as Char)
                }
                u32::class -> {
                    buff.putInt((property.getter.call(this) as u32).toInt())
                }
                u64::class -> {
                    buff.putLong((property.getter.call(this) as u64).toLong())
                }
                float::class -> {
                    buff.putFloat(property.getter.call(this) as float)
                }
                double::class -> {
                    buff.putDouble(property.getter.call(this) as double)
                }
                boolArray::class -> {
                    val temp = property.getter.call(this) as boolArray
                    if (temp.size == length) {
                        temp.forEach { curr -> buff.put(if (curr) 1 else 0) }
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                //for primitive arrays, use the bulk write methods.
                u8Array::class -> {
                    val temp = property.getter.call(this) as u8Array
                    if (temp.size == length) {
                        buff.put(temp.asByteArray())
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                u16Array::class -> {
                    val temp = property.getter.call(this) as u16Array
                    if (temp.size == length) {
                        buff.asShortBuffer().put(temp.asShortArray())
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                CharArray::class -> {
                    val temp = property.getter.call(this) as CharArray
                    if (temp.size == length) {
                        buff.asCharBuffer().put(temp)
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                u32Array::class -> {
                    val temp = property.getter.call(this) as u32Array
                    if (temp.size == length) {
                        buff.asIntBuffer().put(temp.asIntArray())
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                u64Array::class -> {
                    val temp = property.getter.call(this) as u64Array
                    if (temp.size == length) {
                        buff.asLongBuffer().put(temp.asLongArray())
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                FloatArray::class -> {
                    val temp = property.getter.call(this) as FloatArray
                    if (temp.size == length) {
                        buff.asFloatBuffer().put(temp)
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                DoubleArray::class -> {
                    val temp = property.getter.call(this) as DoubleArray
                    if (temp.size == length) {
                        buff.asDoubleBuffer().put(temp)
                        buff.position(buff.position() + length * size)
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                //write each of the array's elements individually.
                Array::class -> {
                    val temp = property.getter.call(this) as Array<*>
                    if (temp.size == length) {
                        temp.forEach { curr -> (elementClass!!.cast(curr) as ByteBufferSerializable).writeToByteBuffer(buff, true) }
                    } else {
                        throw InvalidStateException(property, length, temp.size)
                    }
                }
                //for padding, just skip the bytes.
                ByteBufferPadding::class -> {
                    buff.position(buff.position() + size)
                }

                else -> {
                    (property.getter.call(this) as ByteBufferSerializable).writeToByteBuffer(buff, true)
                }
            }
        }
        //check that the used bytes are equal to the expected size of the object.
        if (buff.position() - startPos != serializationData.bytes) {
            throw SerializationSizeMismatch(this::class, serializationData.bytes, buff.position() - startPos)
        }
    }
}
