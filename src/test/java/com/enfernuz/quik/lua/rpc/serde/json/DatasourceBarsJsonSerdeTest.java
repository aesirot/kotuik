package com.enfernuz.quik.lua.rpc.serde.json;

import com.enfernuz.quik.lua.rpc.api.messages.datasource.Bar;
import com.enfernuz.quik.lua.rpc.api.messages.datasource.Bars;
import com.enfernuz.quik.lua.rpc.api.messages.datasource.C;
import com.enfernuz.quik.lua.rpc.api.structures.DataSourceTime;
import com.enfernuz.quik.lua.rpc.api.structures.DateTimeEntry;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(Enclosed.class)
public class DatasourceBarsJsonSerdeTest {

    public static class RpcArgsSerializationTest extends AbstractJsonRpcArgsSerializationTest<Bars.Args> {

        @Override
        public Bars.Args getArgsObject() {
            return new Bars.Args("36e255d0-3356-4418-a2be-3024fff9ea7f", 2);
        }

        @Override
        public String getArgsJsonPath() {
            return "json/datasource/Bars/args.json";
        }
    }

    public static class RpcResultDeserializationTest extends AbstractRpcResultJsonDeserializationTest<Bars.Result> {

        @Override
        public String getJsonPath() {
            return "json/datasource/Bars/result.json";
        }

        @Override
        public Bars.Result getExpectedObject() {

            Bars.Result result = new Bars.Result();
            final DataSourceTime time = DataSourceTime.builder()
                    .year(1)
                    .month(2)
                    .day(3)
                    .weekDay(4)
                    .hour(5)
                    .min(6)
                    .sec(7)
                    .ms(8)
                    .count(9)
                    .build();
            List<Bar> bars = new ArrayList<>();
            bars.add(new Bar("2","3","4","1", "1", time));
            result.setBars(bars);
            return result;
        }
    }
}
