package classes


@OptIn(ExperimentalUnsignedTypes::class)
class ByteStream(val bytes: UByteArray) {
    var ptr: Int = 0

    fun readByte(): UByte {

        if(ptr >= bytes.size)
            throw IndexOutOfBoundsException("Not enough bytes left")

        val result = bytes[ptr]
        ptr++
        return result
    }

    fun readBytes(size: Int): UByteArray {

        if(ptr + size >= bytes.size)
            throw IndexOutOfBoundsException("Not enough bytes left")

        val result = bytes.copyOfRange(ptr, ptr + size)
        ptr += size
        return result
    }

    fun readRemainingBytes(): UByteArray {

        if(ptr >= bytes.size)
            throw IndexOutOfBoundsException("Not enough bytes left")

        val result = bytes.copyOfRange(ptr, bytes.size)
        ptr = bytes.size
        return result
    }

    fun readShort(): UShort {

        if(ptr + 1 >= bytes.size)
            throw IndexOutOfBoundsException("Not enough bytes left")

        val result = (bytes[ptr].toUInt() shl 8) + bytes[ptr + 1]
        ptr += 2
        return result.toUShort()
    }

    fun readInt(): UInt {

        if(ptr + 3 >= bytes.size)
            throw IndexOutOfBoundsException("Not enough bytes left")

        val result = (bytes[ptr].toUInt() shl 24) +
                (bytes[ptr + 1].toUInt() shl 16) +
                (bytes[ptr + 2].toUInt() shl 8) +
                bytes[ptr + 3]
        ptr += 4
        return result
    }

    fun seek(offset: Int)
    {
        ptr = offset
    }
}