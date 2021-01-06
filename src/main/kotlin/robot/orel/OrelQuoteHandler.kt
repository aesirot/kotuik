package robot.orel

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.structures.QuoteEventInfo
import com.enfernuz.quik.lua.rpc.events.api.QluaEventHandler
import common.Connector

class OrelQuoteHandler(val orel: Orel): QluaEventHandler {
    override fun onQuote(quote: QuoteEventInfo) {
        orel.onQuote(quote.classCode, quote.secCode)
    }
}