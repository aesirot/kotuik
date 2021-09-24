package robot.spreadler

import bond.CurveHolder
import integration.moex.MoexLoadTrades
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import pnl.PnL
import pnl.TradesFromQuik
import robot.infra.Zavod
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


fun main() {
    JobEndOfDay().manual()
}


class JobEndOfDay: Job {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(p0: JobExecutionContext?) {
        try {
            SpreadlerRunner.stopDay()
            Zavod.stopAll()

            val bonds = getAllBonds()
            MoexLoadTrades.loadAll(bonds)

            TradesFromQuik.load()
            PnL.calc()

            val today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
            PnL.sendResult(today, today.plusDays(1))
        } catch (e: Exception) {
            logger.error(e.message, e)
        }

/*
        synchronized(DBConnector) {
            DBConnector.close()
            HibernateUtil.shutdown()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val backupName = "./logs/_bk_kotuik${today.format(formatter)}.zip"
            Backup.execute(backupName, ".", "kotuik", false)
        }
*/
    }

    fun manual() {
        val bonds = getAllBonds()
        MoexLoadTrades.loadAll(bonds)

        TradesFromQuik.load()
        PnL.calc()

        val today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
        PnL.sendResult(today, today.plusDays(1))
    }

    private fun getAllBonds(): HashSet<String> {
        val secCodes = HashSet<String>()

        SpreadlerConfigurator.config.spreadlers.forEach { secCodes.add(it.securityCode) }
        CurveHolder.createCurveSystema().bonds.forEach { secCodes.add(it.code) }
        CurveHolder.curveOFZ().bonds.forEach { secCodes.add(it.code) }

        return secCodes
    }

}