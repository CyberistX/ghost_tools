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

        /*
            Imposta le variabili iniziali. Prima legge il size decompresso, e poi il primo control byte,
            dopodichè copia il primo byte sul flusso di output
         */

        val outSize = bytes.readInt().toInt()
        val outBuffer = UByteArray(outSize)

        var controlByte = bytes.readByte()
        var bitIndex = 7
        var outIndex = 0


        outBuffer[outIndex] = bytes.readByte()
        outIndex++

        while(true)
        {

            if(bitIndex <= 0) {
                bitIndex = 8
                controlByte = bytes.readByte()
            }

            bitIndex--
            var controlBit = (controlByte.toInt() shr bitIndex) and 0x1

            /*
                Se il control bit è zero si tratta di un byte decompresso
             */
            if(controlBit == 0) {
                outBuffer[outIndex] = bytes.readByte()
                outIndex++
                continue
            }

            /*
                Altrimenti, byte compresso
             */
            else {

                if(bitIndex <= 0) {
                    bitIndex = 8
                    controlByte = bytes.readByte()
                }

                bitIndex--
                controlBit = (controlByte.toInt() shr  bitIndex) and 0x1

                var dataOffset = 0xFFFFFF00U

                /*
                    Se il successivo control bit è zero, l'offset dei dati è più piccolo di un byte
                 */
                if(controlBit  == 0) {

                    val byte = bytes.readByte()
                    //println("Reading data offset from next byte.")

                    if(byte.toInt() == 0)
                        break
                    else
                        dataOffset = dataOffset or byte.toUInt()
                }

                /*
                    Altrimenti l'offset dei dati viene espresso con un byte e mezzo.
                    I 4 bit meno significativi vengono salvati nel flusso di controllo
                 */
                else {

                    dataOffset = dataOffset or bytes.readByte().toUInt()

                    for(i in 0..3) {

                        if(bitIndex <= 0) {
                            bitIndex = 8
                            controlByte = bytes.readByte()
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
                }

                /*
                    Il data size è espresso tramite un flusso di bit. Si legge un bit,
                    se è 1 il data size diventa (datasize << 1) xor bit_successivo,
                    altrimenti si chiude il flusso.
                 */
                bitIndex--
                controlBit = (controlByte.toInt() shr  bitIndex) and 0x1
                var dataSize = 1

                while (controlBit == 1) {

                    if(bitIndex <= 0) {
                        bitIndex = 8
                        controlByte = bytes.readByte()
                    }
                    
                    bitIndex--
                    controlBit = (controlByte.toInt() shr  bitIndex) and 0x1
                    dataSize = dataSize shl 0x1
                    dataSize = dataSize or controlBit

                    if(bitIndex <= 0) {
                        bitIndex = 8
                        controlByte = bytes.readByte()
                    }

                    bitIndex--
                    controlBit = (controlByte.toInt() shr  bitIndex) and 0x1

                }

                /*
                    Infine si copiano i byte compressi nel flusso di output
                 */
                while (dataSize >= 0) {
                    outBuffer[outIndex] = outBuffer[(outIndex.toUInt() + dataOffset).toInt()]
                    outIndex++
                    dataSize--
                }

            }
        }

        return outBuffer.toByteArray()
    }

    fun compress(src: UByteArray) : ByteArray {
        val output = mutableListOf<UByte>()

        /*
            Prima di tutto scrive la dimensione del file decompresso
         */
        val size = src.size
        val bytes = ubyteArrayOf( ((size shr 24) and 0xFF).toUByte(),
            ((size shr 16) and 0xFF).toUByte(),
            ((size shr 8) and 0xFF).toUByte(),
            (size and 0xFF).toUByte())
        output.addAll(bytes)

        /*
            Imposta:
                - L'attuale control byte a 0
                - L'offset del control byte nel flusso di destinazione a 0
                - L'index del prossimo bit del control byte a 6 (un bit lo saltiamo)
                - Il puntatore nel flusso di destinazione a 1 (a zero infatti c'è il control byte)
            Dopodiche:
                - Salva il control byte per allocarne lo spazio
                - Aggiunge un byte (il primo è sempre non compresso) dalla sorgente alla destinazione
                - Incrementa il puntatore sorgente
         */
        var controlByte = 0x0U
        var controlOffset = 0
        var bitIndex = 6
        var srcIndex = 0

        output.add(controlByte.toUByte())
        output.add(src[srcIndex])
        srcIndex++

        while(srcIndex < src.size) {
            val match = findMatch(src, srcIndex)

            if(match.length != 0)
            {
                /*
                    Byte Compresso. Primo bit a 1
                 */

                if(bitIndex < 0) {
                    output[controlOffset] = controlByte.toUByte()
                    controlByte = 0U
                    bitIndex = 7
                    controlOffset = output.size
                    output.add(controlByte.toUByte())
                }

                controlByte = controlByte or (1U shl bitIndex)
                bitIndex--

                if( match.offset > -0x100) {
                    /*
                        Secondo bit a 0 se offset > - 0x100, e prossimo byte offset & 0xFF
                     */


                    if (bitIndex < 0) {
                        output[controlOffset] = controlByte.toUByte()
                        controlByte = 0U
                        bitIndex = 7
                        controlOffset = output.size
                        output.add(controlByte.toUByte())
                    }
                    bitIndex--
                    output.add((match.offset and 0xFF).toUByte())
                }
                else {
                    /*
                        Altrimenti secondo bit a 1, offset lungo di cui i 4 byte meno
                        significativi vengono salvati nel flusso di controllo
                     */


                    if (bitIndex < 0) {
                        output[controlOffset] = controlByte.toUByte()
                        controlByte = 0U
                        bitIndex = 7
                        controlOffset = output.size
                        output.add(controlByte.toUByte())
                    }

                    controlByte = controlByte or (1U shl bitIndex)
                    bitIndex--
                    output.add( ((( (match.offset + 0xFF) shr 4) and 0xFF).toUByte()))

                    /*
                        Scrittura dei byte rimanenti nel control flow
                     */
                    val nextCtrlBits = (match.offset + 0xFF) and 0xF
                    for(i in 3 downTo 0) {
                        if (bitIndex < 0) {
                            output[controlOffset] = controlByte.toUByte()
                            controlByte = 0U
                            bitIndex = 7
                            controlOffset = output.size
                            output.add(controlByte.toUByte())
                        }

                        val bit = (nextCtrlBits shr i) and 0x1
                        controlByte = controlByte or (bit.toUInt() shl bitIndex)
                        bitIndex--
                    }
                }

                /*
                    Scrittura data size: per ogni bit oltre al primo si aggiungono due control bit
                    il primo settato a 1 e il secondo settato al bit corrispondente del dataSize
                    L'ultimo bit è 0
                 */


                val dataSize = match.length - 1
                var firstBit = 0

                if(dataSize > 1) {

                    for (i in 31 downTo 0) {
                        if ((dataSize shr i) and 0x1 == 0x1) {
                            firstBit = i
                            break
                        }
                    }

                    for (i in firstBit - 1 downTo 0) {

                        if (bitIndex < 0) {
                            output[controlOffset] = controlByte.toUByte()
                            controlByte = 0U
                            bitIndex = 7
                            controlOffset = output.size
                            output.add(controlByte.toUByte())
                        }

                        controlByte = controlByte or (1U shl bitIndex)
                        bitIndex--

                        if (bitIndex < 0) {
                            output[controlOffset] = controlByte.toUByte()
                            controlByte = 0U
                            bitIndex = 7
                            controlOffset = output.size
                            output.add(controlByte.toUByte())
                        }

                        val sizeBit = (dataSize shr i) and 0x1
                        controlByte = controlByte or (sizeBit.toUInt() shl bitIndex)
                        bitIndex--

                    }

                }

                if (bitIndex < 0) {
                    output[controlOffset] = controlByte.toUByte()
                    controlByte = 0U
                    bitIndex = 7
                    controlOffset = output.size
                    output.add(controlByte.toUByte())
                }
                bitIndex--
                srcIndex += match.length

            }

            else {
                /*
                    Byte non compresso, control bit a zero e copia dall'input
                 */

                if(bitIndex < 0) {
                    output[controlOffset] = controlByte.toUByte()
                    controlByte = 0U
                    bitIndex = 7
                    controlOffset = output.size
                    output.add(controlByte.toUByte())
                }

                bitIndex--
                output.add(src[srcIndex])
                srcIndex++
            }
        }

        /*
        Chiude il flusso impostando gli ultimi control bit a 10 e aggiungendo un byte nullo
         */

        if(bitIndex < 0) {
            output[controlOffset] = controlByte.toUByte()
            controlByte = 0U
            bitIndex = 7
            controlOffset = output.size
            output.add(controlByte.toUByte())
        }

        controlByte = controlByte or (1 shl bitIndex).toUInt()
        bitIndex--

        if(bitIndex < 0) {
            output[controlOffset] = controlByte.toUByte()
            controlByte = 0U
            bitIndex = 7
            controlOffset = output.size
            output.add(controlByte.toUByte())
        }

        output[controlOffset] = controlByte.toUByte()
        output.add((0).toUByte())


        return output.toUByteArray().toByteArray()
    }

    /*
        Ritorna il più grande match valido (almeno 2 byte)
     */
    private fun findMatch(srcBuffer: UByteArray, currentPtr: Int) : MatchInfo{
        var i = -1
        var match = MatchInfo(0, 0)

        while (i >= -currentPtr && i > -4096) {
            if(srcBuffer[currentPtr + i] == srcBuffer[currentPtr]) {
                var j = 0
                while(currentPtr + j < srcBuffer.size && srcBuffer[currentPtr + i + j] == srcBuffer[currentPtr + j] ) {
                    j++
                }
                if(j > 1 && j > match.length)
                    match = MatchInfo(j, i)
            }
            i--
        }
        return match
    }

    internal data class MatchInfo (
        val length: Int,
        val offset: Int
    )

}