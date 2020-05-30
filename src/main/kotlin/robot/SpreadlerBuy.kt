package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Util
import org.jetbrains.annotations.NotNull
import java.math.BigDecimal

class SpreadlerBuy constructor(classCode: String,
                               securityCode: String,
                               quantity: Int,
                               startPrice: BigDecimal,
                               maxPrice: BigDecimal,
                               maxShift: Int,
                               private val fullSpreadlerQuantity: Int,
                               private val aggressiveSpread: BigDecimal,
                               private val minSellSpread: BigDecimal) : PolzuchiiBuy(classCode, securityCode, quantity, startPrice, maxPrice, maxShift) {

    override fun calculatePrice(rpcClient: ZmqTcpQluaRpcClient, classCode: String, securityCode: String, orderPrice: BigDecimal): BigDecimal {
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, securityCode)
            val stakan = rpcClient.qlua_getQuoteLevel2(args2)

            val maxPriceCorrected: BigDecimal = maxPriceCorrected(stakan, orderPrice)

            //лучший bid последний, лучший offer первый
            var totalQty = 0
            for (i in Util.stakanCount(stakan.bidCount) - 1 downTo 0) {
                var price = BigDecimal(stakan.bids[i].price)
                totalQty += stakan.bids[i].quantity.toInt()

                if (price <= orderPrice) {
                    return orderPrice.min(maxPriceCorrected)
                }

                if (totalQty >= this.maxShift) {
                    if (price < maxPriceCorrected - aggressiveSpread && orderPrice < price) {
                        //агрессивно выходим вперед
                        return price + getStep()
                    }
                    if (totalQty >= this.restQuantity * 10) {
                        price += getStep() //перед большой заявкой
                    }
                    return price.min(maxPriceCorrected)
                }
            }

            return orderPrice
        }
    }

    private fun maxPriceCorrected(stakan: @NotNull GetQuoteLevel2.Result, orderPrice: BigDecimal): BigDecimal {
        val maxPriceCorrected: BigDecimal
        var bigSellPrice = bigSellPrice(stakan)
        if (bigSellPrice == null) {
            maxPriceCorrected = maxPrice
        } else {
            val shiftedFromBigSell = bigSellPrice - minSellSpread - getStep()
            maxPriceCorrected = maxPrice.min(shiftedFromBigSell)
            if (maxPriceCorrected.compareTo(maxPrice) != 0 && maxPriceCorrected < orderPrice) {
                log.info("maxPriceCorrected $maxPrice -> $maxPriceCorrected")
            }
        }
        return maxPriceCorrected
    }

    private fun bigSellPrice(stakan: @NotNull GetQuoteLevel2.Result): BigDecimal? {
        var totalQty = 0
        if (Util.stakanCount(stakan.offerCount) > 0) {
            for (i in 0..Util.stakanCount(stakan.offerCount) - 1) {
                var price = BigDecimal(stakan.offers[i].price)
                totalQty += stakan.offers[i].quantity.toInt()
                if (totalQty >= this.fullSpreadlerQuantity * 30) {
                    return price
                }
            }
        }
        return null
    }

    fun getStep(): BigDecimal {
        return BigDecimal("0.01")
    }
}