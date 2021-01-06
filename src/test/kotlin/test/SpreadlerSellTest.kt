package test

import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.google.common.collect.Lists
import common.Constants
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import robot.spreadler.SpreadlerSell
import java.math.BigDecimal
import java.util.*


class SpreadlerSellTest() {

    //@Test(expected = Exception::class)
    @Test
    fun emptyStakan() {
        val result = GetQuoteLevel2.Result("0", "0", Collections.emptyList(), Collections.emptyList())
        val rpcClient = mock(ZmqTcpQluaRpcClient::class.java)
        Mockito.`when`(rpcClient.qlua_getQuoteLevel2(ArgumentMatchers.any()))
                .thenReturn(result)

        val sell = SpreadlerSell("code", "sec", 30, BigDecimal(200),
                BigDecimal(101), 20,
                Constants.DEFAULT_AGGRESSION, BigDecimal("0.19"))
        sell.calculatePrice(rpcClient, "", "", BigDecimal(200))
    }

    @Test
    fun simple() {
        val entry = GetQuoteLevel2.QuoteEntry("101", "10")
        val result = GetQuoteLevel2.Result("1", "0", Lists.newArrayList(entry), Collections.emptyList())
        val rpcClient = mock(ZmqTcpQluaRpcClient::class.java)
        Mockito.`when`(rpcClient.qlua_getQuoteLevel2(ArgumentMatchers.any()))
                .thenReturn(result)

        val sell = SpreadlerSell("code", "sec", 30, BigDecimal(200),
                BigDecimal(101), 20,
                Constants.DEFAULT_AGGRESSION, BigDecimal("0.19"))
        sell.calculatePrice(rpcClient, "", "", BigDecimal(200))
    }

}