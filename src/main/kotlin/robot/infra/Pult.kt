package robot.infra

import bond.*
import com.enfernuz.quik.lua.rpc.api.messages.GetParamEx
import common.Connector
import common.DBService
import common.HibernateUtil
import model.Bond
import model.SecAttr
import org.slf4j.LoggerFactory
import robot.orel.BondQuikChecker
import robot.spreadler.SpreadlerRunner
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.system.exitProcess

class Pult {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun console() {
        while (true) {
            try {
                val line = readLine()?.trim()
                if (line != null) {
                    if (line == "exit") {
                        if (!confirm()) continue

                        log.info("полная остановка")
                        SpreadlerRunner.stopAll()
                        Zavod.stopAll()
                        Connector.close()

                        log.info("EXIT")
                        exitProcess(0)
                    } else if (line == "spreadler") {
                        SpreadlerRunner.console()
                    } else if (line == "cancel orders") {
                        val rpcClient = Connector.get()
                        synchronized(rpcClient) {
                            //TODO rpcClient.getP
                        }
                    } else if (line == "stop all") {
                        Zavod.stopAll()
                    } else if (line.startsWith("stop")) {
                        val id = line.split(" ")[1]
                        Zavod.stop(id)
                    } else if (line == "start all") {
                        Zavod.startAll()
                    } else if (line.startsWith("start")) {
                        val id = line.split(" ")[1]
                        Zavod.startRobot(id)
                    } else if (line.startsWith("delete")) {
                        if (!confirm()) continue

                        val id = line.split(" ")[1]
                        Zavod.stop(id)
                        DBService.deleteRobot(id)
                        Zavod.remove(id)
                    } else if (line == "spec") {
                        spec()
                    } else if (line == "show curve") {
                        showCurve()
                    } else if (line == "check-bond-stats") {
                        checkBondStats()
                    }
                }
            } catch (e: Exception) {
                log.error(e.message, e)
            }

        }
    }

    private fun showCurve() {
        val curveOFZ = CurveHolder.curveOFZ()
        CurveVisualization.visualize(curveOFZ)
    }

    private fun confirm(): Boolean {
        println("уверен (y)?")

        val answer = readLine()?.trim()
        return (answer != null && answer.toLowerCase() == "y")
    }

    private fun spec() {
        //do what you want
        try {
            BondQuikChecker.flush()
            BondQuikChecker.checkAll()

        } catch (e: Exception) {

        }
    }

    private fun checkBondStats() {
        try {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val bondQuery = session.createQuery("from Bond", Bond::class.java)
                val allBonds = bondQuery.list()
                    .filter { it.maturityDt > LocalDate.now() }

                val settleDate = BusinessCalendar.addDays(LocalDate.now(), 1)
                val rpcClient = Connector.get()

                for (bond in allBonds) {
                    if (bond.getAttr(SecAttr.MoexClass) == null) {
                        continue
                    }

                    synchronized(rpcClient) {
                        var args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "DURATION")
                        var ex = rpcClient.qlua_getParamEx(args)
                        val duration = BigDecimal(ex.paramValue)

                        args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "LAST")
                        ex = rpcClient.qlua_getParamEx(args)
                        val last = BigDecimal(ex.paramValue)

                        args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "BID")
                        ex = rpcClient.qlua_getParamEx(args)
                        val bid = BigDecimal(ex.paramValue)

                        args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "OFFER")
                        ex = rpcClient.qlua_getParamEx(args)
                        val offer = BigDecimal(ex.paramValue)

                        val price: BigDecimal
                        if (last.compareTo(BigDecimal.ZERO) != 0) {
                            price = last
                        } else {
                            price = (bid + offer).divide(BigDecimal(2), 6, RoundingMode.HALF_UP)
                        }

                        args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "YIELD")
                        ex = rpcClient.qlua_getParamEx(args)
                        val ytm = BigDecimal(ex.paramValue)

                        args = GetParamEx.Args(bond.getAttrM(SecAttr.MoexClass), bond.code, "ACCRUEDINT")
                        ex = rpcClient.qlua_getParamEx(args)
                        val accrual = BigDecimal(ex.paramValue)

                        val nkdToPrice = (accrual * BigDecimal(100)).divide(
                            bond.nominal, 12,
                            RoundingMode.HALF_UP
                        )

                        val calcYieldDouble =
                            (CalcYieldDouble.effectiveYTM(bond, settleDate, price + nkdToPrice) * BigDecimal(100))
                        val calcYield =
                            (CalcYield.effectiveYTM(bond, settleDate, price + nkdToPrice) * BigDecimal("100"))
                        if ((calcYield - calcYieldDouble).abs() >= BigDecimal("0.000001")) {
                            println("${bond.code} double $calcYieldDouble != bd ${calcYield.toPlainString()}")
                            println("earlyRedemptionDate ${bond.earlyRedemptionDate}")
                            val calcAccrual = CalcYieldDouble.calcAccrual(bond, settleDate)
                            println("НКД квик ${accrual.toPlainString()} расч $calcAccrual")
                        }
                        if ((ytm - calcYieldDouble).abs() >= BigDecimal("0.01")) {
                            println("${bond.code} calc $calcYieldDouble != quik ${ytm.toPlainString()}")
                            println("earlyRedemptionDate ${bond.earlyRedemptionDate}")
                            val calcAccrual = CalcYieldDouble.calcAccrual(bond, settleDate)
                            println("НКД квик ${accrual.toPlainString()} расч $calcAccrual")
                        }

                    }


                }
            }

        } catch (e: Exception) {
            log.error(e.message, e)
        }

    }
}