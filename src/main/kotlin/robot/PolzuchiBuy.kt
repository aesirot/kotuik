package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.Constants
import common.OrderInfo
import common.Orders
import common.Util.stakanCount
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun main() {
    // Orders.testMode = true

    val buyRunner = PolzuchiiBuy(Constants.CLASS_CODE_EQ, Constants.SEC_CODE_KVADRA, 1,
            BigDecimal("0.002700"), BigDecimal("0.002740"), 200)
    val thread = Thread(buyRunner)
    thread.name = "PolzuchiiBuy $securityCode"
    thread.start()

    System.`in`.read()

    buyRunner.stop = true
    buyRunner.lock.withLock {
        buyRunner.lockCondition.signal()
    }
    thread.join()
    Connector.get().close()
}

open class PolzuchiiBuy(private val classCode: String,
                        private val securityCode: String,
                        private val quantity: Int,
                        private val startPrice: BigDecimal,
                        val maxPrice: BigDecimal,
                        val maxShift: Int) : Runnable, InterruptableStrategy {
    companion object {
        const val STRATEGY = "POLZB"
    }

    val log = LoggerFactory.getLogger(this::class.java)
    override val lock = ReentrantLock()
    override val lockCondition = lock.newCondition()
    override var stop = false
    override var success = false
    var updateCallback: (PolzuchiiBuy) -> Unit = {}

    var orderPrice = startPrice
    var restQuantity = quantity

    var orderInfo: OrderInfo? = null

    override fun run() {
        val rpcClient = Connector.get()
        log.info("PolzuchiiBuy $securityCode start")

        var orderId = 0L
        restQuantity = quantity

        try {
            while (!stop) {
                val calculatedPrice = calculatePrice(rpcClient, classCode, securityCode, orderPrice)

                if (orderId == 0L || calculatedPrice.compareTo(orderPrice) != 0) {
                    if (orderId != 0L) {
                        Orders.cancelOrder(classCode, securityCode, orderId, STRATEGY, rpcClient)
                    }
                    orderPrice = calculatedPrice
                    orderId = Orders.buyOrder(classCode, securityCode, restQuantity, calculatedPrice, rpcClient, STRATEGY)
                }

                if (!stop) { //если за время постановки ордера пришла команда на остановку
                    lock.withLock {
                        lockCondition.await(1, TimeUnit.MINUTES)
                    }
                }

                restQuantity = restOrderAmount(rpcClient, orderId, restQuantity)
                updateCallback.invoke(this)
                if (restQuantity == 0) {
                    orderId = 0
                    success = true
                    log.info("PolzuchiiBuy $securityCode SUCCESS")
                    break
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

        log.info("PolzuchiiBuy $securityCode exit")
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
                val boughtQty = restQuantity - orderInfo.order.balance.toInt()
                val message = "BUY $securityCode pr ${orderInfo.order.price} qt $boughtQty"
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

        for (i in stakanCount(stakan.bidCount) - 1 downTo 0) {
            val price = BigDecimal(stakan.bids[i].price)
            totalQty += stakan.bids[i].quantity.toInt()

            if (price < orderPrice) {
                return orderPrice
            }

            if (totalQty >= this.maxShift) {
                return price.min(this.maxPrice)
            }
        }

        return orderPrice
    }


}