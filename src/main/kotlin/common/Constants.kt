package common

import java.math.BigDecimal

object Constants {
    const val BCS_ACCOUNT = "L01-00000F00" //счет депо (?)
    const val BCS_CLIENT_CODE = "453176"
    const val BCS_FIRM = "NC0058900000"
    const val BCS_CASH_GROUP = "EQTV"

    const val CLASS_CODE_EQ = "TQBR"
    const val CLASS_CODE_BOND = "EQOB"

    const val SEC_CODE_KVADRA = "TGKD"

    const val PIK_BO_P02 = "RU000A0JXQ93"

    const val BUY = "B"
    const val SELL = "S"

    val DEFAULT_AGGRESSION = BigDecimal("0.2")

}