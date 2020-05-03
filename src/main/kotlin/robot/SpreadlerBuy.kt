package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Util
import java.math.BigDecimal

class SpreadlerBuy(classCode: String,
                   securityCode: String,
                   quantity: Int,
                   startPrice: BigDecimal,
                   maxPrice: BigDecimal,
                   maxShift: Int,
                   private val aggressiveSpread: BigDecimal) : PolzuchiiBuy(classCode, securityCode, quantity, startPrice, maxPrice, maxShift) {

    override fun calculatePrice(rpcClient: ZmqTcpQluaRpcClient, classCode: String, securityCode: String, orderPrice: BigDecimal): BigDecimal {
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, securityCode)
            val stakan = rpcClient.qlua_getQuoteLevel2(args2)

            //лучший bid последний, лучший offer первый
            var totalQty = 0

            for (i in Util.stakanCount(stakan.bidCount) - 1 downTo 0) {
                var price = BigDecimal(stakan.bids[i].price)
                totalQty += stakan.bids[i].quantity.toInt()

                if (price <= orderPrice) {
                    return orderPrice
                }

                if (totalQty >= this.maxShift) {
                    if (price < this.maxPrice - aggressiveSpread && orderPrice < price) {
                        //агрессивно выходим вперед
                        return price + getStep()
                    }
                    if (totalQty >= this.restQuantity * 10) {
                        price += getStep() //перед большой заявкой
                    }
                    return price.min(this.maxPrice)
                }
            }

            return orderPrice
        }
    }

    fun getStep(): BigDecimal {
        return BigDecimal("0.01")
    }
}