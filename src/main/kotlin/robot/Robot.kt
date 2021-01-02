package robot

interface Robot : Runnable {

    fun init()

    fun stopSignal()

    fun stop()

    fun start()

    fun name(): String

    fun setFinishCallback(function: (Robot) -> Unit)

    fun state(): Any?

    fun isRunning(): Boolean

    fun getParent(): String? {
        return null
    }

    fun setParent(parent: String) {
    }

}