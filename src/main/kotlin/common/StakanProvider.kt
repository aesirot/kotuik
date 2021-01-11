package common

import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import model.Bond
import model.SecAttr
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

open class StakanProvider {

    var nkdCache = ConcurrentHashMap<String, BigDecimal>()

    open fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
        StakanSubscriber.subscribe(classCode, secCode)

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, secCode)
            return rpcClient.qlua_getQuoteLevel2(args2)
        }
    }

    open fun nkd(bond: Bond, settleDate: LocalDate): BigDecimal {
        val key = getNKDKey(bond.code, settleDate)

        return nkdCache.computeIfAbsent(key) {
            val rpcClient = Connector.get()
            synchronized(rpcClient) {
                val args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "ACCRUEDINT")
                val ex = rpcClient.qlua_getParamEx(args)
                return@computeIfAbsent BigDecimal(ex.paramValue)
            }
        }
    }

    private fun getNKDKey(code: String, settleDate: LocalDate) : String {
        return "$code:$settleDate"
    }

}