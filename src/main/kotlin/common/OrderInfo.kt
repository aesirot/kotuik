package common

import java.math.BigDecimal

class OrderInfo(val orderNum: String, val securityCode: String, val quantity: Int, val price: BigDecimal) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OrderInfo
        if (orderNum != other.orderNum) return false

        return true
    }

    override fun hashCode(): Int {
        return orderNum.hashCode()
    }
}