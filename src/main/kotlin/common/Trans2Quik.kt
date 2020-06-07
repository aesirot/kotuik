package common

import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.enfernuz.quik.lua.rpc.config.ClientConfiguration
import com.enfernuz.quik.lua.rpc.config.JsonClientConfigurationReader
import org.jtrans2quik.loader.Trans2QuikLibraryLoader
import org.jtrans2quik.wrapper.Trans2QuikLibrary
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

object Trans2Quik {
    const val TRANS2QUIK_SUCCESS = 0
    const val TRANS2QUIK_FAILED = 1
    const val TRANS2QUIK_QUIK_TERMINAL_NOT_FOUND = 2
    const val TRANS2QUIK_DLL_VERSION_NOT_SUPPORTED = 3
    const val TRANS2QUIK_ALREADY_CONNECTED_TO_QUIK = 4
    const val TRANS2QUIK_WRONG_SYNTAX = 5
    const val TRANS2QUIK_QUIK_NOT_CONNECTED = 6
    const val TRANS2QUIK_DLL_NOT_CONNECTED = 7
    const val TRANS2QUIK_QUIK_CONNECTED = 8
    const val TRANS2QUIK_QUIK_DISCONNECTED = 9
    const val TRANS2QUIK_DLL_CONNECTED = 10
    const val TRANS2QUIK_DLL_DISCONNECTED = 11
    const val TRANS2QUIK_MEMORY_ALLOCATION_ERROR = 12
    const val TRANS2QUIK_WRONG_CONNECTION_HANDLE = 13
    const val TRANS2QUIK_WRONG_INPUT_PARAMS = 14

    private const val path = "C:\\BCS_Work\\QUIK_BCS"
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private lateinit var library: Trans2QuikLibrary

    fun get(): Trans2QuikLibrary {
        if (this::library.isInitialized) {
            return library
        }

        synchronized(this) {
            if (this::library.isInitialized) {
                return library;
            }

            connect()
        }

        return library
    }

    private fun connect() {
        val library = Trans2QuikLibraryLoader.LIBRARY

        val errorCode = com.sun.jna.ptr.NativeLongByReference()
        val buffer = ByteArray(255)

        val returnCode = library.TRANS2QUIK_CONNECT(path, errorCode, buffer, buffer.size)

        if (returnCode.toInt() == TRANS2QUIK_SUCCESS || returnCode.toInt() == TRANS2QUIK_ALREADY_CONNECTED_TO_QUIK) {
            this.library = library
        } else {
            val message = "Trans2Quik cant connect: $returnCode"
            logger.error(message)
            throw Exception(message)
        }
    }

    fun parseString(bytes: ByteArray) =
            bytes.toString(Charset.forName("cp1251")).trim('\u0000')

}