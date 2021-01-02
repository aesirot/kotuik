package common

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.ApiContext
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

class Telega private constructor(options: DefaultBotOptions?) : TelegramLongPollingBot(options) {
    private val logger: Logger = LoggerFactory.getLogger(Telega::class.java)

    object Holder {
        private lateinit var telega: Telega;

        fun get(): Telega {
            if (this::telega.isInitialized) {
                return telega;
            }

            synchronized(this) {
                if (this::telega.isInitialized) {
                    return telega;
                }

                val botOptions = ApiContext.getInstance(DefaultBotOptions::class.java)
                //botOptions.proxyHost = "127.0.0.1"
                //botOptions.proxyPort = 9150
                // Select proxy type: [HTTP|SOCKS4|SOCKS5] (default: NO_PROXY)
                //botOptions.proxyType = DefaultBotOptions.ProxyType.SOCKS5
                telega = Telega(botOptions)

                return telega
            }
        }
    }

    override fun getBotUsername(): String = "aesirot_bot"
    override fun getBotToken(): String = "1195485802:AAH6ch6GVYKkUBWyAvBt6yqOWmeJoXHcz4k"

    override fun onUpdateReceived(p0: Update?) {
    }

    fun sendMessage(message: String) {
        try {
            execute(SendMessage(-1001282661928, message))
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }

}