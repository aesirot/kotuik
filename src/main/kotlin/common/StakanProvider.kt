package common

import model.Bond
import model.SecAttr
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.messages.SubscribeLevel2Quotes
import java.math.BigDecimal

open class StakanProvider {

    open fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
        StakanSubscriber.subscribe(classCode, secCode)

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, secCode)
            return rpcClient.qlua_getQuoteLevel2(args2)
        }
    }

    open fun nkd(bond: Bond): BigDecimal {
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "ACCRUEDINT")
            val ex = rpcClient.qlua_getParamEx(args)
            return BigDecimal(ex.paramValue)
        }
    }

}