select sec_code, sum(realized_pnl)
from trade
group by sec_code