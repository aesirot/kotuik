package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Util
import java.math.BigDecimal

class SpreadlerSell(classCode: String,
                    securityCode: String,
                    quantity: Int,
                    startPrice: BigDecimal,
                    minPrice: BigDecimal,
                    maxShift: Int,
                    private val aggressiveSpread: BigDecimal) : PolzuchiiSell(classCode, securityCode, quantity, startPrice, minPrice, maxShift) {

    override fun calculatePrice(rpcClient: ZmqTcpQluaRpcClient, classCode: String, securityCode: String, orderPrice: StakanPrice): StakanPrice {
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, securityCode)

            val stakan = rpcClient.qlua_getQuoteLevel2(args2)

            //лучший bid последний, лучший offer первый
            var totalQty = 0
            for (i in 0..Util.stakanCount(stakan.offerCount) - 1) {
                var price = BigDecimal(stakan.offers[i].price)
                totalQty += stakan.offers[i].quantity.toInt()

                if (price > orderPrice.price) {
                    return orderPrice
                }

                if (totalQty >= this.maxShift) {
                    if (price > this.minPrice + aggressiveSpread  && totalQty > this.restQuantity) {
                        if (orderPrice.firstOnPrice) {
                            return orderPrice
                        } else {
                            //агрессивно выходим вперед
                            return StakanPrice(price - getStep(), true)
                        }
                    }
                    if (totalQty >= this.restQuantity * 10) {
                        price -= getStep() //перед большой заявкой
                    }
                    return StakanPrice(price.max(this.minPrice), false)
                }
            }

            return orderPrice
        }
    }

    fun getStep(): BigDecimal {
        return BigDecimal("0.01")
    }
}