package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.Constants
import common.Orders
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


fun main() {
    Orders.testMode = true

    val lozniiBuy = LozniiSell(Constants.CLASS_CODE_EQ, Constants.SEC_CODE_KVADRA, 6, 10, 400)
    val thread = Thread(lozniiBuy)
    thread.start()

    System.`in`.read();

    lozniiBuy.stop = true;
    lozniiBuy.lockCondition.signal()
    thread.join()
}

class LozniiSell(val classCode: String, val securityCode: String, val scale: Int, val quantity: Int, val shift: Int) : Runnable {
    companion object {
        const val STRATEGY = "LOZS"
    }

    val log = LoggerFactory.getLogger(this::class.java)
    val lock = ReentrantLock()
    val lockCondition = lock.newCondition()

    var stop = false

    override fun run() {
        val rpcClient = Connector.get()
        log.info("LozniiSell $securityCode start")

        var orderId = 0L
        var orderPrice = BigDecimal.ZERO

        try {
            while (!stop) {
                val r = checkOrderUnexecuted(orderId, rpcClient)
                if (!r) {
                    break
                }

                val price = getZashishPrice(rpcClient, classCode, securityCode)

                if (price != orderPrice) {
                    if (orderId != 0L) {
                        Orders.cancelOrderRPC(classCode, securityCode, orderId, STRATEGY, rpcClient)
                    }
                    orderPrice = price
                    orderId = Orders.sellOrderRPC(classCode, securityCode, quantity, price, rpcClient, STRATEGY)
                }

                lock.withLock {
                    lockCondition.await(20, TimeUnit.SECONDS)
                }
            }
        } catch (e: Exception) {
            log.error(e.message, e)
        }

        if (orderId != 0L) {
            try {
                Orders.cancelOrderRPC(classCode, securityCode, orderId, STRATEGY, rpcClient)
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }

        log.info("LozniiSell $securityCode exit")
        rpcClient.close()
    }

    private fun checkOrderUnexecuted(orderId: Long, rpcClient: ZmqTcpQluaRpcClient): Boolean {
        if (orderId == 0L) {
            return true;
        }
        val orderInfo = rpcClient.qlua_getOrderByNumber(classCode, orderId)
        if (orderInfo.isError) {
            log.error("Order $orderId state unknown error")
            return false
        }
        if (orderInfo.order.balance.toInt() < orderInfo.order.qty) {
            log.error("Order $orderId EXECUTED qty $orderInfo.order.qty, balance $orderInfo.order.balance")
            return false
        }

        return true
    }

    private fun getZashishPrice(rpcClient: ZmqTcpQluaRpcClient, classCode: String, securityCode: String): BigDecimal {
        val args2 = GetQuoteLevel2.Args(classCode, securityCode);
        val result = rpcClient.qlua_getQuoteLevel2(args2);

        //лучший bid последний, лучший offer первый
        var i = 0;
        var lastPrice = BigDecimal.ZERO
        var totalQty = 0;
        while (i < result.offers.size && totalQty < shift) {
            totalQty += result.offers[i].quantity.toInt()
            lastPrice = BigDecimal(result.offers[i].price)
            i++
        }

        //нужно число хоть чуть больше и округленное (чтобы не прыгало от любой мелкой заявки)
        return lastPrice.add(BigDecimal("0.0000001")).setScale(scale, RoundingMode.UP)
    }


}