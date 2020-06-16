package common

import com.enfernuz.quik.lua.rpc.api.messages.SendTransaction
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.google.common.collect.Lists
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.absoluteValue

object Orders {
    val log = LoggerFactory.getLogger(this::class.java)

    var testMode = false

    val currentOrders = ArrayList<OrderInfo>()
    private var lastOrderIdx = -1
    private val limitSpeedQueue = LinkedList<LocalDateTime>()
    private var lastOrderDay = LocalDate.now()

    private fun generateTransId(): Long {
        limitSpeed()
        return System.currentTimeMillis().toInt().absoluteValue.toLong()
        //return System.currentTimeMillis()
    }

    fun cancelOrderRPC(classCode: String, securityCode: String, orderId: Long, strategy: String, rpcClient: ZmqTcpQluaRpcClient) {
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

    fun cancelOrderDLL(classCode: String, securityCode: String, orderId: Long, strategy: String, rpcClient: ZmqTcpQluaRpcClient) {
        log.info("$strategy kill order $orderId")
        if (testMode) {
            return
        }

        val transId = generateTransId()
        val map = HashMap<String, String>()
        map["ACTION"] = "KILL_ORDER"
        map["CLASSCODE"] = classCode
        map["SECCODE"] = securityCode
        map["ORDER_KEY"] = orderId.toString()
        map["TRANS_ID"] = transId.toString()

        val transaction = map.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val resultCode = NativeLongByReference()
        val extendedErrorCode = NativeLongByReference()
        val orderNum = DoubleByReference()
        val resultMessage = ByteArray(255)
        val errorMessage = ByteArray(255)
        Trans2Quik.get().TRANS2QUIK_SEND_SYNC_TRANSACTION(transaction,
                resultCode,
                IntByReference(transId.toInt()),
                orderNum,
                resultMessage,
                resultMessage.size,
                extendedErrorCode,
                errorMessage,
                errorMessage.size
        )

        val resultMessageStr = Trans2Quik.parseString(resultMessage)
        val errorMessageStr = Trans2Quik.parseString(errorMessage)

        if (3 != resultCode.value.toInt() || 0 != extendedErrorCode.value.toInt() || !errorMessageStr.isEmpty()) {
            val message = "Failed to cancel transaction ${resultCode.value}: $resultMessageStr ${extendedErrorCode.value}: $errorMessageStr"
            throw Exception(message)
        }
        log.info("$strategy killed order $orderId")
    }

    fun buyOrderRPC(classCode: String, securityCode: String, quantity: Int,
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

    fun buyOrderDLL(classCode: String, securityCode: String, quantity: Int,
                    price: BigDecimal, rpcClient: ZmqTcpQluaRpcClient, strategy: String): Long {
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

        val transaction = map.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val resultCode = NativeLongByReference()
        val extendedErrorCode = NativeLongByReference()
        val orderNumReference = DoubleByReference()
        val resultMessage = ByteArray(255)
        val errorMessage = ByteArray(255)
        Trans2Quik.get().TRANS2QUIK_SEND_SYNC_TRANSACTION(transaction,
                resultCode,
                IntByReference(transId.toInt()),
                orderNumReference,
                resultMessage,
                resultMessage.size,
                extendedErrorCode,
                errorMessage,
                errorMessage.size
        )

        val resultMessageStr = Trans2Quik.parseString(resultMessage)
        val errorMessageStr = Trans2Quik.parseString(errorMessage)

        val orderNum = orderNumReference.getPointer().getLong(0L)
        if (3 == resultCode.value.toInt() && 0 == extendedErrorCode.value.toInt() && errorMessageStr.isEmpty()) {
            log.info("$strategy buy order $securityCode price $price qty $quantity (order $orderNum) $resultMessageStr")
            return orderNum
        } else {
            val message = "Failed transaction ${resultCode.value}: $resultMessageStr ${extendedErrorCode.value}: $errorMessageStr"
            throw Exception(message)
        }
    }

    private fun findCurrentOrderNum(rpcClient: ZmqTcpQluaRpcClient, transId: Long, securityCode: String): Long {
        //нужно найти orderNum , чтобы была возможность его отменить
        //другие способы найти - подписка на onTransReply - это может и быстрее (?), но
        //встает вопрос параллельной работы нескольких процессов + все равно синхронное ожидание
        val now = LocalDate.now()
        if (now.isAfter(lastOrderDay)) {
            lastOrderIdx = -1
            lastOrderDay = now
        }

        synchronized(rpcClient) {
            var idx = lastOrderIdx + 1
            val start = System.currentTimeMillis()
            var attempts = 0
            //while (System.currentTimeMillis() < start + 5000) {
            while (System.currentTimeMillis() < start + 15000) { // реальный случай, может проблема wifi
                val item = rpcClient.qlua_getItem("Orders", idx)
                attempts++
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

            log.error("attempts $attempts")
            logOrdersNotFound(rpcClient)
        }

        throw Exception("Timeout to find order using trans_id=$transId")
    }

    private fun logOrdersNotFound(rpcClient: ZmqTcpQluaRpcClient) {
        log.error("current idx $lastOrderIdx")
        val last = rpcClient.qlua_getItem("Orders", lastOrderIdx)
        log.error("$last")
        val lastP1 = rpcClient.qlua_getItem("Orders", lastOrderIdx + 1)
        if (lastP1 != null) {
            log.error("$lastP1")
        } else {
            log.error("lastP1 null")
        }
        val lastP2 = rpcClient.qlua_getItem("Orders", lastOrderIdx + 2)
        if (lastP2 != null) {
            log.error("$lastP2")
        } else {
            log.error("lastP2 null")
        }
    }

    fun sellOrderRPC(classCode: String, securityCode: String, quantity: Int,
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

    fun sellOrderDLL(classCode: String, securityCode: String, quantity: Int,
                     price: BigDecimal, rpcClient: ZmqTcpQluaRpcClient, strategy: String, retry: Boolean = false): Long {
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

        val transaction = map.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val resultCode = NativeLongByReference()
        val extendedErrorCode = NativeLongByReference()
        val orderNumReference = DoubleByReference()
        val resultMessage = ByteArray(255)
        val errorMessage = ByteArray(255)
        Trans2Quik.get().TRANS2QUIK_SEND_SYNC_TRANSACTION(transaction,
                resultCode,
                IntByReference(transId.toInt()),
                orderNumReference,
                resultMessage,
                resultMessage.size,
                extendedErrorCode,
                errorMessage,
                errorMessage.size
        )

        val resultMessageStr = Trans2Quik.parseString(resultMessage)
        val errorMessageStr = Trans2Quik.parseString(errorMessage)

        val orderNum = orderNumReference.getPointer().getLong(0L)
        if (6 == resultCode.value.toInt() && !retry) {
            //Данный инструмент запрещен для операции шорт
            //скорее всего квик не успел пересчитать лимиты, пробуем еще раз
            log.error("Retry failed transaction ${resultCode.value}: $resultMessageStr ${extendedErrorCode.value}: $errorMessageStr")
            Thread.sleep(2000)
            return sellOrderDLL(classCode, securityCode, quantity, price, rpcClient, strategy, true)
        } else if (3 == resultCode.value.toInt() && 0 == extendedErrorCode.value.toInt() && errorMessageStr.isEmpty()) {
            log.info("$strategy sell order $securityCode price $price qty $quantity (order $orderNum) $resultMessageStr")
            return orderNum
        } else {
            val message = "Failed transaction ${resultCode.value}: $resultMessageStr ${extendedErrorCode.value}: $errorMessageStr"
            throw Exception(message)
        }
    }

    /**
     * Quik не принимает больше 30 транзакций в секунду
     * + (есть инфа) БКС берет по 2 рубля за транзакцию, при превышении 20 в секунду
     * На срочке вроде есть еще коммиссия за "холостые" заявки, при превышении 20 000 в день, но это пока за рамками
     *
     * Quik на своей стороне (видимо) как-то группирует. Или считает странно. Т.к. когда ограничение было 20 -
     * он иногда ругался "превышено 30 транзакций в секунду". Поэтому текущее ограничение 10.
     */
    private fun limitSpeed() {
        synchronized(this) {
            var t: LocalDateTime
            while (true) {
                t = LocalDateTime.now()
                val secondAgo = t.minus(1, ChronoUnit.SECONDS)
                while (limitSpeedQueue.size > 0 && limitSpeedQueue.peek() < secondAgo) {
                    limitSpeedQueue.poll()
                }
                if (limitSpeedQueue.size < 10) {
                    break
                }

                log.info("превышена скорость 10 транзакций в секунду, притормаживаем")
                Thread.sleep(100)
            }
            limitSpeedQueue.add(t)
        }
    }
}

