select trade_id, sec_code, currency, trade_datetime, direction, quantity, quantity*price*0.01*0.03*10 fee, fee_amount
, realized_PnL, position, sell_amount-buy_amount
from trade
where currency in ('SUR') --and direction = 'S' and realized_PnL != 0
and sec_code='RU000A100HU7'
order by trade_datetime