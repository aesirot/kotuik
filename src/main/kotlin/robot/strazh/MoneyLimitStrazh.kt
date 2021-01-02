package robot.strazh

import com.enfernuz.quik.lua.rpc.api.messages.GetMoneyEx
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.Constants
import org.slf4j.LoggerFactory
import robot.spreadler.SpreadlerRunner
import common.Telega
import java.math.BigDecimal
import java.time.LocalDate

object MoneyLimitStrazh {
    val log = LoggerFactory.getLogger(this::class.java)

    private val minTriggerLimit = HashMap<String, BigDecimal>()

    private var triggeredDay: LocalDate? = null
    private var triggeredCurrencies = ArrayList<String>()

    init {
        minTriggerLimit["SUR"] = BigDecimal("100000")
        minTriggerLimit["USD"] = BigDecimal("1800")
        minTriggerLimit["EUR"] = BigDecimal("500")
    }

    fun check() {
        if (triggeredDay != null && LocalDate.now().isAfter(triggeredDay)) {
            //очищаем на следующий день
            triggeredDay = null
            triggeredCurrencies.clear()
        }

        val rpcClient = Connector.get()
        for (entry in minTriggerLimit) {
            if (triggeredCurrencies.contains(entry.key)) {
                continue // срабатывает 1 раз в день (если запустили руками дальше, то не мешаем)
            }

            val currentBal = getCurrentBal(entry, rpcClient)
            if (currentBal < entry.value) {
                triggeredDay = LocalDate.now()
                triggeredCurrencies.add(entry.key)

                belowLimit(entry.key, entry.value, currentBal)
            }
        }
    }

    private fun getCurrentBal(entry: MutableMap.MutableEntry<String, BigDecimal>, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        synchronized(rpcClient) {
            val args = GetMoneyEx.Args(Constants.BCS_FIRM, Constants.BCS_CLIENT_CODE, Constants.BCS_CASH_GROUP, entry.key, 2) //t+2
            val moneyEx = rpcClient.qlua_getMoneyEx(args)!!

            return BigDecimal(moneyEx.currentBal)
        }
    }

    private fun belowLimit(currency: String, limit: BigDecimal, currentBal: BigDecimal) {
        val msg = "СТРАЖ ДЕНЕЖНОГО ЛИМИТА\nТЕКУЩИЙ БАЛАНС $currency = $currentBal МИНИМАЛЬНЫЙ $limit"
        log.error(msg)
        Telega.Holder.get().sendMessage(msg)

        SpreadlerRunner.stopBuy(currency)
    }
}