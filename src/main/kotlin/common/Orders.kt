package common

import com.enfernuz.quik.lua.rpc.api.messages.SendTransaction
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.math.absoluteValue

object Orders {
    val log = LoggerFactory.getLogger(this::class.java)

    var testMode = false

    val currentOrders = ArrayList<OrderInfo>()
    private var lastOrderIdx = -1

    private fun generateTransId(): Long {
        return System.currentTimeMillis().toInt().absoluteValue.toLong()
        //return System.currentTimeMillis()
    }

    fun cancelOrder(classCode: String, securityCode: String, orderId: Long, strategy: String, rpcClient: ZmqTcpQluaRpcClient) {
        log.info("$strategy kill order $orderId")
        if (testMode) {
            return
        }

        synchronized(rpcClient) {
            val transId = generateTransId()
            val map = HashMap<String, String>()
            map["ACTION"] = "KILL_ORDER"
            map["CLASSCODE"] = classCode
            map["SECCODE"] = securityCode
            map["ORDER_KEY"] = orderId.toString()
            map["TRANS_ID"] = transId.toString()

            val r = rpcClient.qlua_sendTransaction(SendTransaction.Args(map))
            if (r != "") {
                throw Exception("CANT CANCEL ORDER $orderId: $r")
            }
        }
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() < start + 3000) {
            synchronized(rpcClient) {
                val orderByNumber = rpcClient.qlua_getOrderByNumber(classCode, orderId)
                val cancelBit = orderByNumber.order.flags.and(2)
                if (cancelBit > 0) {
                    return
                }
            }
            Thread.sleep(100)
        }
    }

    fun buyOrder(classCode: String, securityCode: String, quantity: Int,
                 price: BigDecimal, rpcClient: ZmqTcpQluaRpcClient, strategy: String): Long {
        synchronized(rpcClient) {
            val transId = generateTransId()
            log.info("$strategy buy order $securityCode price $price qty $quantity")
            if (testMode) {
                return 1
            }

            val map = HashMap<String, String>()
            map.put("OPERATION", Constants.BUY)
            map.put("CLASSCODE", classCode)
            map.put("SECCODE", securityCode)
            map.put("QUANTITY", quantity.toString())
            map.put("PRICE", price.toString())
            map.put("TRANS_ID", transId.toString())
            map.put("ACCOUNT", Constants.BCS_ACCOUNT)
            map.put("CLIENT_CODE", Constants.BCS_CLIENT_CODE)
            map.put("COMMENT", strategy)
            map.put("ACTION", "NEW_ORDER")

            val r = rpcClient.qlua_sendTransaction(SendTransaction.Args(map))

            if (r != "") {
                throw Exception("BUY ORDER ERROR $transId: $r")
            }

            val orderNum = findCurrentOrderNum(rpcClient, transId, securityCode)
            log.info("$strategy buy order $securityCode price $price qty $quantity (order $orderNum)")
            return orderNum
        }
    }

    private fun findCurrentOrderNum(rpcClient: ZmqTcpQluaRpcClient, transId: Long, securityCode: String): Long {
        //нужно найти orderNum , чтобы была возможность его отменить
        //другие способы найти - подписка на onTransReply - это может и быстрее (?), но
        //встает вопрос параллельной работы нескольких процессов + все равно синхронное ожидание
        synchronized(rpcClient) {
            var idx = lastOrderIdx + 1 //пока  убрать инкремент, т.к. иногда бы
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() < start + 5000) {
                val item = rpcClient.qlua_getItem("Orders", idx)
                if (item != null) {
                    val orderNum = item["order_num"]
                    val orderTransId = item["trans_id"]
                    val orderSecCode = item["sec_code"]
                    if (securityCode != orderSecCode) {
                        idx++ //to skip manual orders
                        continue
                    }

                    if ((orderTransId == null || orderTransId.isEmpty() || orderTransId == "0")) {
                        continue
                    }

                    if (transId.toString() == orderTransId) {
                        lastOrderIdx = idx
                        return orderNum!!.toLong()
                    } else {
                        idx++
                    }
                }
                Thread.sleep(10)
            }
        }

        throw Exception("Timeout to find order using trans_id=$transId")
    }

    fun sellOrder(classCode: String, securityCode: String, quantity: Int,
                  price: BigDecimal, rpcClient: ZmqTcpQluaRpcClient, strategy: String): Long {
        synchronized(rpcClient) {
            val transId = generateTransId()
            log.info("$strategy sell order $securityCode price $price qty $quantity (id $transId)")
            if (testMode) {
                return 1
            }

            val map = HashMap<String, String>()
            map.put("OPERATION", Constants.SELL)
            map.put("CLASSCODE", classCode)
            map.put("SECCODE", securityCode)
            map.put("QUANTITY", quantity.toString())
            map.put("PRICE", price.toString())
            map.put("TRANS_ID", transId.toString())
            map.put("ACCOUNT", Constants.BCS_ACCOUNT)
            map.put("CLIENT_CODE", Constants.BCS_CLIENT_CODE)
            map.put("COMMENT", strategy)
            map.put("ACTION", "NEW_ORDER")

            val r = rpcClient.qlua_sendTransaction(SendTransaction.Args(map))

            if (r != "") {
                throw Exception("SELL ORDER ERROR $transId: $r")
            }

            val orderNum = findCurrentOrderNum(rpcClient, transId, securityCode)
            log.info("$strategy sell order $securityCode price $price qty $quantity (order $orderNum)")
            return orderNum
        }
    }
}

