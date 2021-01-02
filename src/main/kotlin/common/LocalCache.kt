package common

import model.Bond
import java.util.concurrent.ConcurrentHashMap

object LocalCache {
    private val productCodeCache = ConcurrentHashMap<String, Bond>()

    fun getBond(code: String): Bond {
        if (!productCodeCache.contains(code)) {
            val bond = DBService.getBond(code)
            productCodeCache[code] = bond
        }

        return productCodeCache[code]!!
    }

    fun update(bond: Bond) {
        productCodeCache[bond.code] = bond
    }

    fun clear() {
        productCodeCache.clear()
    }
}