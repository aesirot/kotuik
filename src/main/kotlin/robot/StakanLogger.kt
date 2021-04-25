package robot

import bond.CurveHolder
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import common.ConnectorRpc2
import common.HibernateUtil
import common.StakanSubscriber
import model.BidAskLog
import model.Bond
import model.SecAttr
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime

class StakanLogger : AbstractLoopRobot() {
    private val log = LoggerFactory.getLogger(this::class.java)!!

    private lateinit var bonds: ArrayList<Bond>

    override fun name(): String {
        return "StakanLogger"
    }

    override fun init() {
        super.init()

        bonds = ArrayList()
        val curve = CurveHolder.createCurveSystema()
        bonds.addAll(curve.bonds)
        val curveOFZ = CurveHolder.curveOFZ()
        bonds.addAll(curveOFZ.bonds)

        for (bond in bonds) {
            StakanSubscriber.subscribe(bond.getAttrM(SecAttr.MoexClass), bond.code)
        }
    }

    override fun execute() {
        val now = LocalDateTime.now()

        if (now.hour == 10 && now.minute < 1) {
            return
        }

        if (now.hour == 18 && now.minute > 40) {
            return
        }

        val rpcClient = ConnectorRpc2.get()
        for (bond in bonds) {
            val stakan: GetQuoteLevel2.Result
            synchronized(rpcClient) {
                val args2 = GetQuoteLevel2.Args(bond.getAttrM(SecAttr.MoexClass), bond.code)
                stakan = rpcClient.qlua_getQuoteLevel2(args2)
            }

            if (stakan.bids.size == 0 || stakan.offers.size == 0) {
                continue
            }

            //лучший bid последний, лучший offer первый
            val log = BidAskLog()
            log.dtm = now
            log.code = bond.code
            log.bid = BigDecimal(stakan.bids[stakan.bids.size - 1].price)
            log.ask = BigDecimal(stakan.offers[0].price)

            val session = HibernateUtil.getSessionFactory().openSession()
            val transaction = session.beginTransaction()
            session.save(log)
            transaction.commit()
            session.close()
        }
    }

    override fun setFinishCallback(function: (Robot) -> Unit) {
    }
}

