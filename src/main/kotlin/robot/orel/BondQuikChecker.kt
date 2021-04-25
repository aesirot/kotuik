package robot.orel

import bond.BusinessCalendar
import bond.CalcYield
import bond.CurveHolder
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.Telega
import model.Bond
import model.SecAttr
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object BondQuikChecker {
    var log = LoggerFactory.getLogger(this::class.java)

    val stopSet = HashSet<String>()

    fun flush() {
        stopSet.clear()
    }

    fun checkAll() {
        CurveHolder.curveOFZ().bonds.forEach { check(it) }
        CurveHolder.createCurveSystema().bonds.forEach { check(it) }
    }

    fun check(bond: Bond): Boolean {
        if (stopSet.contains(bond.code)) {
            return false
        }

        if (bond.getAttr(SecAttr.MoexClass) == null) {
            stopSet.add(bond.code)
            log.error("${bond.code} no MoexClass")
            return false
        }

        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val classCode = bond.getAttrM(SecAttr.MoexClass)
            val nkd = nkd(classCode, bond.code, rpcClient)
            val duration = duration(classCode, bond.code, rpcClient)

            //проверка статики
            val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)
            val calcNkd = CalcYield.calcAccrual(bond, settleDate)

            val nkdToPrice = (nkd * BigDecimal(100)).divide(bond.nominal, 12, RoundingMode.HALF_UP)

            val last = last(classCode, bond.code, rpcClient)
            if (last.compareTo(BigDecimal.ZERO) == 0) {
                stopSet.add(bond.code)
                val msg = "Не могу проверить статику ${bond.code} last == 0"
                log.error(msg)
                return true
            }

            val ytm = yield(classCode, bond.code, rpcClient)
            val calcYield = (CalcYield.effectiveYTM(bond, settleDate, last + nkdToPrice)
                    * BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP)

            if ((calcNkd - nkd).abs() > BigDecimal("0.01")) {
                stopSet.add(bond.code)
                val msg =
                    "Ошибка статики ${bond.code} нкд факт ${nkd.toPlainString()} ож ${calcNkd.toPlainString()}"
                log.error(msg)
                Telega.Holder.get().sendMessage(msg)
                return false
            }

            if ((calcYield-ytm).abs() > BigDecimal("0.01")) {
                stopSet.add(bond.code)
                val msg =
                    "Ошибка статики ${bond.code} доха факт ${ytm.toPlainString()} ож ${calcYield.toPlainString()}"
                log.error(msg)
                Telega.Holder.get().sendMessage(msg)
                return false
            }
        }

        return true
    }

    private fun nkd(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "ACCRUEDINT")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun duration(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "DURATION")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun last(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "LAST")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

    private fun yield(classCode: String, secCode: String, rpcClient: ZmqTcpQluaRpcClient): BigDecimal {
        val args = GetParamEx.Args(classCode, secCode, "YIELD")
        val ex = rpcClient.qlua_getParamEx(args)
        return BigDecimal(ex.paramValue)
    }

}