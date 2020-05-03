create table trade(
    trade_id identity,
    class_code varchar(30),
    sec_code varchar(250),
    direction varchar(10),
    quantity integer,
    price number,
    currency varchar(20),
    amount number,
    trade_datetime timestamp,
    trans_id varchar2(30) null,
    quik_trade_id varchar2(30) null,
    order_num varchar2(30) null,
    position integer null,
    buy_amount number null,
    sell_amount number null,
    realized_pnl number null,
    fee_amount number null
    );

