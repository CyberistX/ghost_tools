import classes.ByteStream
import classes.DataHandler
import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {

    var banner = "    ____                  __  __           __   _                  _ __ \n" +
            "   / __ \\____  ____ ___  / / / /___ ______/ /__(_)___  ____ _     (_) /_\n" +
            "  / /_/ / __ \\/ __ `__ \\/ /_/ / __ `/ ___/ //_/ / __ \\/ __ `/    / / __/\n" +
            " / _, _/ /_/ / / / / / / __  / /_/ / /__/ ,< / / / / / /_/ /  _ / / /_  \n" +
            "/_/ |_|\\____/_/ /_/ /_/_/ /_/\\__,_/\\___/_/|_/_/_/ /_/\\__, /  (_)_/\\__/  \n" +
            "                                                    /____/              "
    println(banner)
    println()
    println("PEX/EAR DECOMPRESSOR FOR GHOST IN THE SHELL (PSX)")
    println("WARNING: EAR DECOMPRESSION NOT CURRENTLY WORKING!")
    println()
    println()

    if(args.size < 2)
    {
        printUsage()
        return
    }

    if(args[0] == "c") {

        val file1 = File(args[1])
        val file2 = File(args[2])

        if(!file1.exists() ) {
            println("File doesn't exist! ${file1.name}")
            return
        }
        else if(!file1.name.endsWith(".hdr", true))
        {
            println("First input needs to be a .hdr file! ${file1.name}")
            printUsage()
            return
        }

        else if(!file2.exists() ) {
            println("File doesn't exist! ${file2.name}")
            return
        }
        else if(!file2.name.endsWith(".dec", true))
        {
            println("Second input needs to be a .dec file! ${file1.name}")
            printUsage()
            return
        }

        val fileName = file1.name.removeSuffix(".hdr")

        val headerBytes = file1.readBytes()
        val decompressedBytes = DataHandler.compress(file2.readBytes().toUByteArray())

        File(fileName).writeBytes(headerBytes + decompressedBytes)

    }
    else if(args[0] == "d") {

        val file = File(args[1])

        if(!file.exists()) {
            println("File doesn't exist!")
            return
        }

        val byteStream = ByteStream(file.readBytes().toUByteArray())
        DataHandler.extractPex(byteStream, file.name)

        /*
        if(args[0].endsWith(".PEX", true)) {
            DataHandler.extractPex(byteStream, file.name)
        }
        else if(args[0].endsWith(".EAR", true))
        {
            DataHandler.extractEar(byteStream, file.name)
        }
        */
    }
    else {
        printUsage()
        return
    }
}

fun printUsage() {
    println("decompressor d file.pex")
    println("\t decompress pex file, creates a .dec file and a .hdr file")
    println("decompressor c file.pex.hdr file.pex.dex")
    println("\t compress pex file, needs a .hdr file and a .dec file")
}