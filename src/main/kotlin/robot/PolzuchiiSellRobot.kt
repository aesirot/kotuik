package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import common.Connector
import common.DBService
import common.Orders
import common.Util
import model.robot.PolzuchiiSellState
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PolzuchiiSellRobot(
    private val state: PolzuchiiSellState
) : Robot {

    private val log = LoggerFactory.getLogger(this::class.java)
    val lock = ReentrantLock()
    val lockCondition = lock.newCondition()
    var stop = false
    var success = false
    
    private var finishCallback: (Robot) -> Unit = {}

    var orderPrice = state.startPrice

    var name: String? = null
    private var _parent: String? = null

    private var thread: Thread? = null

    override fun run() {
        val rpcClient = Connector.get()
        log.info("PolzuchiiSell ${state.securityCode} start")

        var orderId = 0L

        try {
            while (!stop) {
                val calculatedPrice = calculatePrice(rpcClient, state.classCode, state.securityCode, orderPrice)

                if (orderId == 0L || calculatedPrice.compareTo(orderPrice) != 0) {
                    if (orderId != 0L) {
                        Orders.cancelOrderDLL(state.classCode, state.securityCode, orderId, name(), rpcClient)
                    }
                    orderPrice = calculatedPrice
                    orderId =
                        Orders.sellOrderDLL(state.classCode, state.securityCode, state.quantity, calculatedPrice, rpcClient, name())
                }

                if (!stop) { //если за время постановки ордера пришла команда на остановку
                    lock.withLock {
                        lockCondition.await(20, TimeUnit.SECONDS)
                    }
                }

                val restQuantity = restOrderAmount(rpcClient, orderId, state.quantity)

                if (restQuantity == 0) {
                    orderId = 0
                    success = true

                    log.info("PolzuchiSell ${name()} SUCCESS")
                    DBService.deleteRobot(name())

                    finishCallback.invoke(this)
                    break
                } else if (restQuantity != state.quantity) {
                    state.quantity = restQuantity

                    DBService.updateRobot(this)
                }
            }
        } catch (e: Exception) {
            log.error(e.message, e)
        }

        if (orderId != 0L) {
            try {
                Orders.cancelOrderDLL(state.classCode, state.securityCode, orderId, name()
                    , rpcClient)
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }

        log.info("PolzuchiiSell ${name()} exit")
    }

    private fun restOrderAmount(rpcClient: ZmqTcpQluaRpcClient, orderId: Long, restQuantity: Int): Int {
        if (Orders.testMode) {
            return restQuantity
        }

        if (orderId == 0L) {
            return restQuantity
        }

        synchronized(rpcClient) {
            val orderInfo = rpcClient.qlua_getOrderByNumber(state.classCode, orderId)
            if (orderInfo.isError) {
                throw Exception("Order $orderId state unknown error")
            }

            if (orderInfo.order.balance.toBigDecimal().toInt() < restQuantity) {
                val soldQty = restQuantity - orderInfo.order.balance.toBigDecimal().toInt()
                val message = "SELL ${name()} pr ${orderInfo.order.price} qt $soldQty"
                log.info(message)
                return orderInfo.order.balance.toBigDecimal().toInt()
            }

            return restQuantity
        }
    }

    private fun calculatePrice(
        rpcClient: ZmqTcpQluaRpcClient,
        classCode: String,
        securityCode: String,
        orderPrice: BigDecimal
    ): BigDecimal {
        val stakan: GetQuoteLevel2.Result
        synchronized(rpcClient) {
            val args2 = GetQuoteLevel2.Args(classCode, securityCode)
            stakan = rpcClient.qlua_getQuoteLevel2(args2)
        }

        //лучший bid последний, лучший offer первый
        var totalQty = 0
        for (i in 0..Util.stakanCount(stakan.offerCount) - 1) {
            val price = BigDecimal(stakan.offers[i].price)
            totalQty += stakan.offers[i].quantity.toInt()

            if (price > orderPrice) {
                return orderPrice
            }

            if (totalQty >= state.maxShift) {
                return price.max(state.minPrice)
            }
        }

        return orderPrice
    }

    override fun init() {
    }

    override fun stopSignal() {
        stop = true
    }

    override fun isRunning(): Boolean {
        if (thread != null) {
            return !stop
        }
        return false
    }

    override fun stop() {
        if (!isRunning()) {
            return
        }

        stop = true
        lock.withLock {
            lockCondition.signalAll()
        }

        thread!!.join()
        thread = null
    }

    @Synchronized
    override fun start() {
        if (thread != null) {
            return
        }
        stop = false
        thread = Thread(this, name())
        thread!!.start()
    }

    override fun name(): String {
        return name!!
    }

    override fun setFinishCallback(function: (Robot) -> Unit) {
         finishCallback = function
    }

    override fun state(): Any {
        return state
    }

    override fun getParent(): String? {
        return _parent
    }

    override fun setParent(parent: String) {
        _parent = parent
    }
}