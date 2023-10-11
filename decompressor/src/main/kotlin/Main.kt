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

    if(args.size != 1)
    {
        println("Usage: earextract archive.ear or earextract file.pex")
        println("WARNING: EAR archives not currently working correctly.")
        return
    }

    val file = File(args[0])

    if(!file.exists()) {
        println("File doesn't exist!")
        return
    }

    val byteStream = ByteStream(file.readBytes().toUByteArray())

    if(args[0].endsWith(".PEX", true)) {
        DataHandler.extractPex(byteStream, file.name)
    }
    else if(args[0].endsWith(".EAR", true))
    {
        DataHandler.extractEar(byteStream, file.name)
    }


}