###### Доки
{ "classCode": "TQBR", "securityCode": "TGKD", "id": "2", "maxBuyPrice": 100, "quantity": 10, "maxShift": 20, "minSellSpread": 0.2, "buyStage": true, "buyPrice": 100, "restQuantity": 5, "updated": "05.04.2020 19:11:10"}

###### Спредлер

* Для еврооблигаций - комиссия 0.125% менеджер обещал узнать о снижении

###### Планы
* новый день - индекс заявок с 0 !!!
* Со спредлером еврооблигаций - можно начать как в апреле погасят PGIL
* Написать Цериху на налоговый вычет по PGIL
* "данный инструмент запрещен для операции шорт" - повторно ставить заявку на продажу 
(может это просто таймаут? и оно уйдет в trans2quik)
* проверка и переход на trans2quik
* продумать мелкого разрывчика пятерошника

###### Облако
потенциально
https://ruvds.com/ru-rub/pricing#order

###### Добавление сервисов
http://www.jsonschema2pojo.org/ генерация pojo

###### Подключение
* Необходимо!
* Попробовать по ipv6
* Узнать про получение статического адреса ipv6

#### Журнал
###### Итоги апреля
* За апрель прибыль 5800р (без учета каких-то купонов)
* По апрельскому отчету комиссия ~0.035% c оборота. Я так понял, что это с учетом ком ММВБ
* В мае план 10к (хотя и выходные, но я увеличил масштаб)
* По майскому отчету надо определить комиссию
* В апреле неудачно зашел в СберП - убыток ~50к еле закрылся купонами от других бумаг.(итог месяца -0.2%) 
* На этом фоне прибыль робота ни о чем
###### Итоги мая
* Прибыль 12к
* дал роботу 400к дополнительного объема (уже 1М)
* оборот в конце месяца возрос где-то до 800к
* Выходные + 2 дня, когда страж ммвб остановил куплю и было почти 0
* наступил в Нефгазхол4 - там частичное погашение. Но может быть дефолт. 
Торги уже неделю не происходят (с даты отсечки, погашение 01.06).
Плюс там уже было 2 реструктуризации. Ну и цена. 
Я ошибся, думая, что там текущий номинал 900 и цена около того. Оказалось цена 89 отн текущего. 
А дисконт 10% о чем-то говорит. Ну и цены на нефть, ковид, могут, могут меня подвести :-(
* субъективно робот все более стабилен, в начале торгов бывает, что робот решает переставить заявку
а она уже выполнена, получается двойная покупка. Видел 1 раз, продал руками. Но это требует пригляда :( 
* на рынке все ровно, особенно после заявления цб о потенциале снижения ставки на 1%
* мне нравится как работает отступ перед большими заявками и катапульта при больших продажах - спасает от застревания

###### Июнь
* план прибыли 15к
* Нефгазхол4 - частичное погашение было! Но дальше робот сразу застрял в убытке. Да и не хочу я в этой бумаге торчать :-(
* перейти на trans2quik (скорость должна вырости, разошью синхронизацию rpcClient)
* в первых числах робот сожрал весь лимит (RGBI просел на 1), пришлось его 2 дня на ручном режиме держать в продажах 
(на покупку только то, что продалось). Остаток отрос до 400к. И даже это были хорошие дни. 
* дневной оборот очень близок к 1М. Больше 900. Хочется прибавить еще 5 бумаг, чтобы перевалить рубеж 1М

###### Сентябрь
* После добавления евро средний оборот стабильно больше 1М (~1,2М)
* Дневная прибыль ~1к (перестал считать точно)
* Прибыль медленно деградирует, но думаю стабилизируется
* вывел деньги из Цериха, счет больше 10М, через 30 минут набрал менеджер Вячеслав, предлагал новую карту


###### Ноябрь
* Поиск выгодных предложений
* построение графика доходности

###### Декабрь
* начал мониторить ОФЗ - пока ничего интересного
* запустил еврооблигации в евро
* планы
* кривая АФК (коды расчета, эмитент?)
* евро еврооблигации инструменты с низкой ликвидностью
