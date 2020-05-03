package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.Constants
import common.Orders
import common.Util.stakanCount
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun main() {
    Orders.testMode = true

    val sell = PolzuchiiSell(Constants.CLASS_CODE_EQ, Constants.SEC_CODE_KVADRA, 1,
            BigDecimal(0.002800), BigDecimal(0.002600), 200)
    val thread = Thread(sell)
    thread.name = "PolzuchiiSell $securityCode"
    thread.start()

    System.`in`.read()

    sell.stop = true
    sell.lock.withLock {
        sell.lockCondition.signal()
    }
    thread.join()
    Connector.get().close()
}

open class PolzuchiiSell(private val classCode: String,
                         private val securityCode: String,
                         private val quantity: Int,
                         private val startPrice: BigDecimal,
                         val minPrice: BigDecimal,
                         val maxShift: Int) : Runnable, InterruptableStrategy {
    companion object {
        const val STRATEGY = "POLZS"
    }

    val log = LoggerFactory.getLogger(this::class.java)
    override val lock = ReentrantLock()
    override val lockCondition = lock.newCondition()
    override var stop = false
    override var success = false
    var updateCallback: (PolzuchiiSell) -> Unit = {}

    var orderPrice = startPrice
    var restQuantity = quantity

    override fun run() {
        val rpcClient = Connector.get()
        log.info("PolzuchiiSell $securityCode start")

        var orderId = 0L

        try {
            while (!stop) {
                restQuantity = restOrderAmount(rpcClient, orderId, restQuantity)
                updateCallback.invoke(this)
                if (restQuantity == 0) {
                    orderId = 0
                    success = true
                    log.info("PolzuciiSell $securityCode SUCCESS")
                    break
                }

                val calculatedPrice = calculatePrice(rpcClient, classCode, securityCode, orderPrice)

                if (orderId == 0L || calculatedPrice.compareTo(orderPrice) != 0) {
                    if (orderId != 0L) {
                        Orders.cancelOrder(classCode, securityCode, orderId, STRATEGY, rpcClient)
                    }
                    orderPrice = calculatedPrice
                    orderId = Orders.sellOrder(classCode, securityCode, restQuantity, calculatedPrice, rpcClient, STRATEGY)
                }

                if (!stop) { //если за время постановки ордера пришла команда на остановку
                    lock.withLock {
                        lockCondition.await(20, TimeUnit.SECONDS)
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e.message, e)
        }

        if (orderId != 0L) {
            try {
                Orders.cancelOrder(classCode, securityCode, orderId, STRATEGY, rpcClient)
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }

        log.info("PolzuchiiSell $securityCode exit")
    }

    private fun restOrderAmount(rpcClient: ZmqTcpQluaRpcClient, orderId: Long, restQuantity: Int): Int {
        if (Orders.testMode) {
            return restQuantity
        }

        if (orderId == 0L) {
            return restQuantity
        }

        synchronized(rpcClient) {
            val orderInfo = rpcClient.qlua_getOrderByNumber(classCode, orderId)
            if (orderInfo.isError) {
                throw Exception("Order $orderId state unknown error")
            }

            if (orderInfo.order.balance.toInt() < restQuantity) {
                val soldQty = restQuantity - orderInfo.order.balance.toInt()
                val message = "SELL $securityCode pr ${orderInfo.order.price} qt $soldQty"
                log.info(message)
                Telega.Holder.get().sendMessage(message)
                return orderInfo.order.balance.toInt()
            }

            return restQuantity
        }
    }

    protected open fun calculatePrice(rpcClient: ZmqTcpQluaRpcClient, classCode: String, securityCode: String, orderPrice: BigDecimal): BigDecimal {
        val args2 = GetQuoteLevel2.Args(classCode, securityCode)
        val stakan = rpcClient.qlua_getQuoteLevel2(args2)

        //лучший bid последний, лучший offer первый
        var totalQty = 0
        for (i in 0..stakanCount(stakan.offerCount) - 1) {
            val price = BigDecimal(stakan.offers[i].price)
            totalQty += stakan.offers[i].quantity.toInt()

            if (price > orderPrice) {
                return orderPrice
            }

            if (totalQty >= this.maxShift) {
                return price.max(this.minPrice)
            }
        }

        return orderPrice
    }


}