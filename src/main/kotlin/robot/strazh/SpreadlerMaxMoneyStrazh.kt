package robot.strazh

import com.enfernuz.quik.lua.rpc.api.messages.GetDepoEx
import common.Connector
import common.Constants
import common.Util
import org.slf4j.LoggerFactory
import pnl.PnL
import robot.spreadler.SpreadlerConfigurator
import java.lang.Integer.min
import java.math.BigDecimal

object SpreadlerMaxMoneyStrazh {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val maxTriggerLimit = HashMap<String, BigDecimal>()

    init {
        maxTriggerLimit["SUR"] = BigDecimal("1250000")
        maxTriggerLimit["USD"] = BigDecimal("7000")
        maxTriggerLimit["EUR"] = BigDecimal("3000")
    }

    fun check() {
        val rpcClient = Connector.get()

        val balance = HashMap<String, BigDecimal>()
        for (entry in maxTriggerLimit) {
            balance.put(entry.key, BigDecimal.ZERO)
        }

        synchronized(rpcClient) {
            for (spreadler in SpreadlerConfigurator.config.spreadlers) {
                val fullPrice = PnL.fullPrice(spreadler, rpcClient)
                val args = GetDepoEx.Args(Constants.BCS_FIRM, Constants.BCS_CLIENT_CODE, spreadler.securityCode, Constants.BCS_ACCOUNT, 2) //t+2
                var currentPosition = rpcClient.qlua_getDepoEx(args)?.currentBal ?: 0
                currentPosition = min(spreadler.quantity, currentPosition)
                val currency = Util.currency(spreadler)

                val position = fullPrice * BigDecimal(currentPosition)

                if (balance.containsKey(currency)) {
                    balance[currency] = balance[currency]!! + position
                }
            }
        }

        for (entry in balance) {
            if (entry.value > maxTriggerLimit[entry.key]) {
                log.error("Сожрали ${entry.key} больше лимита ${maxTriggerLimit[entry.key]}")
            }
        }
    }

}