package classes

import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
object DataHandler {

    fun extractEar(bytes: ByteStream, fileName: String)
    {
        val header = parseArchiveHeader(bytes)


        for(i in 0..<header.size - 1) {
            val fileBytes =
                if(header[i + 1] == 0xFFFFFFFFU)
                    bytes.readRemainingBytes()
                else bytes.readBytes((header[i + 1] - header[i]).toInt())

            val stream = ByteStream(fileBytes)
            val decompressed = decompress(stream)
            File("${fileName}_${header[i]}").writeBytes(decompressed)
        }
    }

    fun extractPex(bytes: ByteStream, fileName: String) {
        File("$fileName.hdr").writeBytes(bytes.readBytes(0x800).toByteArray())
        val data = decompress(bytes)
        File("$fileName.dec").writeBytes(data)
    }

    fun parseArchiveHeader(bytes: ByteStream): List<UInt> {

        val result = mutableListOf<UInt>()

        while (true) {
            val offset = bytes.readInt()
            result.add(offset)

            if(offset == 0xFFFFFFFFU)
                break
        }

        return result
    }

    fun decompress(bytes: ByteStream) : ByteArray {
        val outSize = bytes.readInt().toInt()
        val outBuffer = UByteArray(outSize)

        var controlByte = bytes.readByte()
        var bitIndex = 7
        var outIndex = 0


        outBuffer[outIndex] = bytes.readByte()
        //println("Wrote uncompressed byte ${Integer.toHexString(outBuffer[outIndex].toInt())} at ${Integer.toHexString(outIndex)}")
        outIndex++

        while(true)
        {

            if(bitIndex <= 0) {
                bitIndex = 8
                controlByte = bytes.readByte()
                //println("New Control: ${Integer.toHexString(controlByte.toInt())}")
            }

            bitIndex--
            var controlBit = (controlByte.toInt() shr bitIndex) and 0x1

            if(controlBit == 0) {
                outBuffer[outIndex] = bytes.readByte()
                //println("Wrote uncompressed byte ${Integer.toHexString(outBuffer[outIndex].toInt())} at ${Integer.toHexString(outIndex)}")
                outIndex++
                continue
            }
            else {

                if(bitIndex <= 0) {
                    bitIndex = 8
                    controlByte = bytes.readByte()
                    //println("New Control: ${Integer.toHexString(controlByte.toInt())}")
                }

                bitIndex--
                controlBit = (controlByte.toInt() shr  bitIndex) and 0x1

                var dataOffset = 0xFFFFFF00U

                if(controlBit  == 0) {

                    val byte = bytes.readByte()
                    //println("Reading data offset from next byte.")

                    if(byte.toInt() == 0)
                        break
                    else
                        dataOffset = dataOffset or byte.toUInt()
                }

                else {
                    //println("Reading data offset from next byte plus next 4 control bits")

                    dataOffset = dataOffset or bytes.readByte().toUInt()

                    for(i in 0..3) {

                        if(bitIndex <= 0) {
                            bitIndex = 8
                            controlByte = bytes.readByte()
                            //println("New Control: ${Integer.toHexString(controlByte.toInt())}")
                        }

                        bitIndex--
                        controlBit = (controlByte.toInt() shr  bitIndex) and 0x1
                        dataOffset = dataOffset shl 0x1
                        dataOffset = dataOffset or controlBit.toUInt()

                    }

                    dataOffset += 0xFFFFFF01U
                }

                if(bitIndex <= 0) {
                    bitIndex = 8
                    controlByte = bytes.readByte()
                    //println("New Control: ${Integer.toHexString(controlByte.toInt())}")
                }

                //println("Reading data size")
                bitIndex--
                controlBit = (controlByte.toInt() shr  bitIndex) and 0x1
                var dataSize = 1

                while (controlBit == 1) {

                    if(bitIndex <= 0) {
                        bitIndex = 8
                        controlByte = bytes.readByte()
                        //println("New Control: ${Integer.toHexString(controlByte.toInt())}")
                    }
                    
                    bitIndex--
                    controlBit = (controlByte.toInt() shr  bitIndex) and 0x1
                    dataSize = dataSize shl 0x1
                    dataSize = dataSize or controlBit

                    if(bitIndex <= 0) {
                        bitIndex = 8
                        controlByte = bytes.readByte()
                        //println("New Control: ${Integer.toHexString(controlByte.toInt())}")
                    }

                    bitIndex--
                    controlBit = (controlByte.toInt() shr  bitIndex) and 0x1

                }

                while (dataSize >= 0) {
                    outBuffer[outIndex] = outBuffer[(outIndex.toUInt() + dataOffset).toInt()]
                    //println("Wrote compressed byte ${Integer.toHexString(outBuffer[outIndex].toInt())} " +
                    //       "at ${Integer.toHexString(outIndex)} from ${Integer.toHexString(outIndex + dataOffset.toInt())}")
                    outIndex++
                    dataSize--
                }

            }
        }

        return outBuffer.toByteArray()
    }

}