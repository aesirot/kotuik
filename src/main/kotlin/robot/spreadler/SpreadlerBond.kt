package robot.spreadler

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
import robot.InterruptableStrategy
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
                //val startBuyPrice = (maxBuyPrice * BigDecimal("0.99")).setScale(2, RoundingMode.UP);
                val startBuyPrice = maxBuyPrice - BigDecimal.ONE

                val buy = SpreadlerBuy(classCode, securityCode, restQuantity, startBuyPrice,
                        maxBuyPrice, maxShift, this.quantity,
                        aggressiveSpread ?: DEFAULT_AGGRESSION, minSellSpread)
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
            val currentBal = rpcClient.qlua_getDepoEx(args)?.currentBal ?: 0

            if (currentBal < restQuantity) {
                log.error("Not enough $securityCode - currentBal=${currentBal}, restQuantity=$restQuantity")
                return false
            }
            return true
        }
    }

    fun syncWithLimit() {
        log = LoggerFactory.getLogger(this::class.java)
        val rpcClient = Connector.get()
        synchronized(rpcClient) {
            val args = GetDepoEx.Args(BCS_FIRM, BCS_CLIENT_CODE, securityCode, BCS_ACCOUNT, 2) //t+2
            val currentBal = rpcClient.qlua_getDepoEx(args)?.currentBal ?: 0

            if (currentBal > this.quantity + 1) {
                log.info("$id - skip sync, current balance $currentBal . May be position were here")
                return
            }

            if (buyStage) {
                if (quantity - restQuantity == currentBal) {
                    return
                } else if (currentBal > quantity - restQuantity) {
                    log.info("SYNC REST QUANTITY. buy=$buyStage Current bal=$currentBal, restQuantity=$restQuantity")
                    restQuantity = quantity - currentBal
                    if (restQuantity == 0) {
                        this.buyStage = false
                        this.restQuantity = quantity
                    }
                    save()
                }
            } else {
                if (restQuantity == currentBal) {
                    return
                } else if (restQuantity > currentBal) {
                    log.info("SYNC REST QUANTITY. buy=$buyStage Current bal=$currentBal, restQuantity=$restQuantity")
                    if (currentBal == 0) {
                        this.buyStage = true
                        this.restQuantity = quantity
                    } else {
                        this.restQuantity = currentBal
                    }
                    save()
                }
            }
        }
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