import com.enfernuz.quik.lua.rpc.api.messages.GetQuoteLevel2
import com.enfernuz.quik.lua.rpc.api.zmq.ZmqTcpQluaRpcClient
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.Thread.sleep

class StakanLogger(val rpcClient: ZmqTcpQluaRpcClient) : Runnable {

    override fun run() {
        while (true) {
            val args2 = GetQuoteLevel2.Args("TQBR", "TGKD");
            val result = rpcClient.qlua_getQuoteLevel2(args2);

            if (result == null) {
                println("Удалённая процедура 'message' выполнилась с ошибкой.")
            } else {
                println(result)
//                val quoteEntry = result.bids[0]
 //               quoteEntry.price;
            }

            val mapper = ObjectMapper()
            val f: String = "a"
            val kClass = GetQuoteLevel2.Result::class
            val r = mapper.readValue<GetQuoteLevel2.Result>(f, kClass.java)

            sleep(5000)
        }
    }
}

