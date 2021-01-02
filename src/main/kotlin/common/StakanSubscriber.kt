package common

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.messages.SubscribeLevel2Quotes

object StakanSubscriber {
    val subscribed = HashSet<String>()

    open fun subscribe(classCode: String, secCode: String) {
        if (subscribed.contains(secCode)) {
            return
        }

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            if (!subscribed.contains(secCode)) {
                val args = SubscribeLevel2Quotes.Args(classCode, secCode)
                rpcClient.qlua_SubscribeLevelIIQuotes(args)

                Thread.sleep(500)

                subscribed.add(secCode)
            }
        }
    }
}