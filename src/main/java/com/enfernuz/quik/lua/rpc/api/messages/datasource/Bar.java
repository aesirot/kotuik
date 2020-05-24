package com.enfernuz.quik.lua.rpc.api.messages.datasource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.enfernuz.quik.lua.rpc.api.structures.DataSourceTime;
import com.enfernuz.quik.lua.rpc.api.structures.DateTimeEntry;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.MoreObjects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"open", "close", "high", "low", "volume", "datetime"})
public class Bar {

    @JsonProperty("open")
    @NotNull
    private String open;

    @JsonProperty("close")
    @NotNull
    private String close;

    @JsonProperty("high")
    @NotNull
    private String high;

    @JsonProperty("low")
    @NotNull
    private String low;

    @JsonProperty("volume")
    @NotNull
    private String volume;

    @JsonProperty("datetime")
    @NotNull
    private DataSourceTime datetime;

    /**
     * No args constructor for use in serialization
     */
    public Bar() {
    }

    public Bar(String open, String close, String high, String low, String volume, DataSourceTime datetime) {
        super();
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.datetime = datetime;
    }

    @JsonProperty("open")
    public String getOpen() {
        return open;
    }

    @JsonProperty("open")
    public void setOpen(String open) {
        this.open = open;
    }

    @JsonProperty("close")
    public String getClose() {
        return close;
    }

    @JsonProperty("close")
    public void setClose(String close) {
        this.close = close;
    }

    @JsonProperty("high")
    public String getHigh() {
        return high;
    }

    @JsonProperty("high")
    public void setHigh(String high) {
        this.high = high;
    }

    @JsonProperty("low")
    public String getLow() {
        return low;
    }

    @JsonProperty("low")
    public void setLow(String low) {
        this.low = low;
    }

    @JsonProperty("volume")
    public String getVolume() {
        return volume;
    }

    @JsonProperty("volume")
    public void setVolume(String volume) {
        this.volume = volume;
    }

    @JsonProperty("datetime")
    public DataSourceTime getDatetime() {
        return datetime;
    }

    @JsonProperty("datetime")
    public void setDatetime(DataSourceTime datetime) {
        this.datetime = datetime;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("open", open)
                .add("close", close)
                .add("high", high)
                .add("low", low)
                .add("volume", volume)
                .add("datetime", datetime)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bar bar = (Bar) o;
        return Objects.equals(open, bar.open) &&
                Objects.equals(close, bar.close) &&
                Objects.equals(high, bar.high) &&
                Objects.equals(low, bar.low) &&
                Objects.equals(volume, bar.volume) &&
                Objects.equals(datetime, bar.datetime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(open, close, high, low, volume, datetime);
    }
}