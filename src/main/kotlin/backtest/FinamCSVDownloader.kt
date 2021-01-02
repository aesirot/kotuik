package backtest

import bond.CurveHolder
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.system.exitProcess


fun main() {

    FinamCSVDownloader.init()

    val curveOFZ = CurveHolder.curveOFZ()
    for (bond in curveOFZ.bonds) {
        FinamCSVDownloader.download(bond.code)
    }
    val curveAFK = CurveHolder.createCurveSystema()
    for (bond in curveOFZ.bonds) {
        FinamCSVDownloader.download(bond.code)
    }

    exitProcess(0)
}

object FinamCSVDownloader {

    val finamIdMap = HashMap<String, String>()

    fun init() {
        val text = read()
        val p = Pattern.compile("value=\"(\\d+)\">([^<]*)</a>")

        val m = p.matcher(text)
        while (m.find()) {
            finamIdMap[m.group(2)] = m.group(1)
        }
    }

    private fun read(): String {
        val stream = this.javaClass.getResourceAsStream("finamids.txt")
        stream.use {
            val reader = BufferedReader(InputStreamReader(stream))
            reader.use {
                return reader.readText()
            }
        }
    }


    fun download(secCode: String) {
        val file = File("data/csv/$secCode.csv")
        if (file.exists()) {
            return
        }

        println(secCode)

        val finamId = finamIdMap[findId(secCode)]

        //val token = "03AGdBq275YzWNP5zniAd-8qBBGFAWURyUqJCmDaSt5oZgOWnIBq4tsdKy6ephIa2uXYXcD2X6yp52GJ8zE6rctsuI2Qc9f5rLtVYDWeUmzMnVX5npEh0ahwsSEI7XewbBCflU4TLxH_vMhKWcSl0eHmFWXY0-pQfJqGvIHjcupqVjnRxdPc8sSblBLuyKJgLDCGXzzF-Vg60tOKBi-NTyFEphrbGHFsyP9PS1RqgwKLdxtWBfSa34ZjJ-tSBrataXShSXpTFWJdVp3vE5qIY4ZsAS0tnpQ4cJTsvf0kgi6cd8Js9ZLKvRoER2yD4ugcBjNDijLMkiPttrVA7jEtRsTPMDGUcuNDV9yQzQiuJ1MGJUylF673b0u4rSFBe3Prs2NyrulZwt07aHThgFYB1-QCwx28o5-STvlso5IKrdfzIptWxT9fXo_KrqnYTOrPBOQPZl5sibyx_k"
        val token = "03AGdBq275YzWNP5zniAd-8qBBGFAWURyUqJCmDaSt5oZgOWnIBq4tsdKy6ephIa2uXYXcD2X6yp52GJ8zE6rctsuI2Qc9f5rLtVYDWeUmzMnVX5npEh0ahwsSEI7XewbBCflU4TLxH_vMhKWcSl0eHmFWXY0-pQfJqGvIHjcupqVjnRxdPc8sSblBLuyKJgLDCGXzzF-Vg60tOKBi-NTyFEphrbGHFsyP9PS1RqgwKLdxtWBfSa34ZjJ-tSBrataXShSXpTFWJdVp3vE5qIY4ZsAS0tnpQ4cJTsvf0kgi6cd8Js9ZLKvRoER2yD4ugcBjNDijLMkiPttrVA7jEtRsTPMDGUcuNDV9yQzQiuJ1MGJUylF673b0u4rSFBe3Prs2NyrulZwt07aHThgFYB1-QCwx28o5-STvlso5IKrdfzIptWxT9fXo_KrqnYTOrPBOQPZl5sibyx_k"
        //val em = "492891"
        val em = "453557"
        val period = "2" //1 min

        val urlStr = "http://export.finam.ru/export9.out?market=2&em=${finamId}&token=${token}&code=${secCode}&apply=0&df=1&mf=0&yf=2019&from=01.01.2019&dt=21&mt=7&yt=2020&to=21.08.2020&p=${period}&f=${secCode}_200101_200821&e=.csv&cn=$secCode&dtf=1&tmf=1&MSOR=0&mstime=on&mstimever=1&sep=3&sep2=1&datf=1&at=1"
        println(urlStr)
        val url = URL(urlStr)
        var inputStream: InputStream? = null


        url.openStream().use {
            val br = BufferedReader(InputStreamReader(it), 1024*1024)
            val content = br.readText()
            FileUtils.write(file, content, StandardCharsets.UTF_8)
        }
    }

    private fun findId(secCode: String): String {
        if (secCode.startsWith("SU")) {
            return "ОФЗ "+secCode.substring(2,7)
        }
        if (secCode == "") {return ""}
        TODO("Not yet implemented")
    }

}