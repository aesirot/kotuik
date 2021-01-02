package backtest.orel

import model.Bond
import bond.CalcYield
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.google.common.collect.Lists
import common.StakanProvider
import java.math.BigDecimal

class StakanCSVSimulator : StakanProvider() {
    override fun stakan(classCode: String, secCode: String): GetQuoteLevel2.Result {
        val bar = QuoteCSVService.getOrPrev(secCode, OrelFlightSimulator.currentTime)
        if (bar == null) {
            return GetQuoteLevel2.Result("0", "0", Lists.newArrayList(), Lists.newArrayList())
        }

        val bid = GetQuoteLevel2.QuoteEntry(bar.low.toPlainString(), "1")
        val ask = GetQuoteLevel2.QuoteEntry(bar.high.toPlainString(), "1")

        return GetQuoteLevel2.Result("1", "1", Lists.newArrayList(bid), Lists.newArrayList(ask))
    }

    override fun nkd(bond: Bond): BigDecimal {
        return CalcYield.calcAccrual(bond, OrelFlightSimulator.currentTime.toLocalDate())
    }
}