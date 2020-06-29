select trade_datetime, direction, quantity, position, realized_PnL, buy_Amount, sell_Amount
from trade
where sec_code = 'RU000A0ZYFC6'
order by trade_datetime