package robot

import com.enfernuz.quik.lua.rpc.api.messages.GetDepoEx
import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.messages.SubscribeLevel2Quotes
import common.Connector
import common.Constants.BCS_ACCOUNT
import common.Constants.BCS_CLIENT_CODE
import common.Constants.BCS_FIRM
import common.Constants.DEFAULT_AGGRESSION
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import kotlin.concurrent.withLock

class SpreadlerBond(val classCode: String, val securityCode: String, val id: String,
                    var maxBuyPrice: BigDecimal, var quantity: Int, var maxShift: Int, val minSellSpread: BigDecimal) : Runnable {

    var buyStage = true
    var buyPrice = maxBuyPrice
    var restQuantity = quantity
    var updated: Date = Date()
    var aggressiveSpread: BigDecimal? = null

    @Transient
    var currentStrategy: InterruptableStrategy? = null

    @Transient
    private var stop = false

    @Transient
    lateinit var log: Logger

    override fun run() {
        log = LoggerFactory.getLogger(this::class.java)
        stop = false

        subscribeStakan()
        while (!stop) {
            if (buyStage) {
                if (!checkStakan()) {
                    return
                }
                //val startBuyPrice = (maxBuyPrice * BigDecimal("0.99")).setScale(2, RoundingMode.UP);
                val startBuyPrice = maxBuyPrice - BigDecimal.ONE

                val buy = SpreadlerBuy(classCode, securityCode, restQuantity, startBuyPrice,
                        maxBuyPrice, maxShift, aggressiveSpread ?: DEFAULT_AGGRESSION)
                buy.updateCallback = {
                    if (it.restQuantity != this.restQuantity || it.orderPrice != this.buyPrice) {
                        this.restQuantity = it.restQuantity
                        this.buyPrice = it.orderPrice
                        save()
                    }
                }

                synchronized(this) {
                    currentStrategy = buy
                    if (stop) {
                        return
                    }
                }
                buy.run()

                if (!buy.success) {
                    log.info("Buy returned without success")
                    return
                }

                restQuantity = quantity
                buyStage = false
                save()
            } else {
                if (!checkLimit()) {
                    return
                }
                //add 0.2% (0.002 price)
                val minSellPrice = buyPrice + minSellSpread
                val maxSellPrice = buyPrice + BigDecimal.ONE
                //val minSellPrice = (buy.orderPrice * BigDecimal("1.002")).setScale(3, RoundingMode.UP)
                //val maxSellPrice = (buy.orderPrice * BigDecimal("1.01")).setScale(3, RoundingMode.UP)

                val sell = SpreadlerSell(classCode, securityCode, restQuantity, maxSellPrice,
                        minSellPrice, maxShift, aggressiveSpread ?: DEFAULT_AGGRESSION,
                        BigDecimal("0.19"))
                sell.updateCallback = {
                    if (it.restQuantity != this.restQuantity) {
                        this.restQuantity = it.restQuantity
                        save()
                    }
                }

                synchronized(this) {
                    currentStrategy = sell

                    if (stop) {
                        return
                    }
                }
                sell.run()

                if (!sell.success) {
                    log.info("Buy returned without success")
                    return
                }

                restQuantity = quantity
                buyStage = true
                save()
            }
        }
    }

    private fun subscribeStakan() {
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val args = SubscribeLevel2Quotes.Args(classCode, securityCode)
            rpcClient.qlua_SubscribeLevelIIQuotes(args)
        }
        Thread.sleep(500)
    }

    private fun checkLimit(): Boolean {
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val args = GetDepoEx.Args(BCS_FIRM, BCS_CLIENT_CODE, securityCode, BCS_ACCOUNT, 2) //t+2
            val currentBal = rpcClient.qlua_getDepoEx(args)?.currentBal?:0

            if (currentBal < restQuantity) {
                log.error("Not enough $securityCode - currentBal=${currentBal}, restQuantity=$restQuantity")
                return false
            }
            return true
        }
    }

    private fun checkStakan(): Boolean {
        val args2 = GetQuoteLevel2.Args(classCode, securityCode)
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val stakan = rpcClient.qlua_getQuoteLevel2(args2)

            //лучший bid последний, лучший offer первый
            //val bestSellPrice = BigDecimal(stakan.offers[0].price)
            /*if (bestSellPrice < maxBuyPrice.add(BigDecimal("0.1"))) {
                log.error("maxBuyPrice $maxBuyPrice and current sell price $bestSellPrice are too close")
                return false
            }*/
        }
        return true
    }

    private fun save() {
        updated = Date()
        SpreadlerConfigurator.save()
    }

    @Synchronized
    fun stop() {
        stop = true
        currentStrategy?.stop = true
        currentStrategy?.lock?.withLock {
            currentStrategy!!.lockCondition.signalAll()
        }
    }

    fun getStep(): BigDecimal {
        return BigDecimal("0.01")
    }
}