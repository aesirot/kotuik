package com.enfernuz.quik.lua.rpc.api.messages.datasource;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

import com.enfernuz.quik.lua.rpc.api.RemoteProcedure;
import com.enfernuz.quik.lua.rpc.api.RpcArgs;
import com.enfernuz.quik.lua.rpc.api.RpcResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.MoreObjects;

import java.util.List;
import java.util.Objects;

public final class Bars implements RemoteProcedure {

    @JsonPropertyOrder({"datasource_uuid", "count"})
    public static class Args implements RpcArgs<Bars> {

        @JsonProperty("datasource_uuid")
        @NotNull
        private Object datasourceUuid;

        @JsonProperty("count")
        @DecimalMin("0")
        private Integer count;

        public Args() {
        }

        public Args(Object datasourceUuid, Integer count) {
            super();
            this.datasourceUuid = datasourceUuid;
            this.count = count;
        }

        @JsonProperty("datasource_uuid")
        public Object getDatasourceUuid() {
            return datasourceUuid;
        }

        @JsonProperty("datasource_uuid")
        public void setDatasourceUuid(Object datasourceUuid) {
            this.datasourceUuid = datasourceUuid;
        }

        @JsonProperty("count")
        public Integer getCount() {
            return count;
        }

        @JsonProperty("count")
        public void setCount(Integer count) {
            this.count = count;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"bars"})
    public static class Result implements RpcResult<Bars> {

        @JsonProperty("bars")
        @Valid
        @NotNull
        private List<Bar> bars = null;

        public Result() {
        }

        public Result(List<Bar> bars) {
            super();
            this.bars = bars;
        }

        @JsonProperty("bars")
        public List<Bar> getBars() {
            return bars;
        }

        @JsonProperty("bars")
        public void setBars(List<Bar> bars) {
            this.bars = bars;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("bars", bars)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result result = (Result) o;
            return Objects.equals(bars, result.bars);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bars);
        }
    }
}
