package org.sng.main.conditions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SelectionRoute implements Serializable {
    @SerializedName("nextHopIp")
    private List<String> _nextHopIps;


    @SerializedName("asPath")
    private List<Long> _asPath;


    @SerializedName("ipPrefix")
    private String _networkString;

    @SerializedName("vpnName")
    private String _vpnName;

    public SelectionRoute(Builder builder) {
        this._networkString = builder.network;
        this._asPath = builder.asPath;
        this._nextHopIps = builder.nextHopIps;

//        this._nextHopStrings = transIpListToString(_nextHopIps);
        this._vpnName = builder.vpnName;
    }


    // need rewrite
    public boolean ifMatch() {
        return false;
    }
    
    public static class Builder{
        private String network;

        private String vpnName;

        private List<String> nextHopIps;

        private List<Long> asPath;

        public Builder(String prefix) {
            this.network = prefix;
        }

        public Builder nextHop(List<String> nextHopIp) {
            this.nextHopIps = nextHopIp;
            return this;
        }

        public Builder vpnName(String name) {
            this.vpnName = name;
            return this;
        }

        public Builder asPath(List<Long> asPath) {
            this.asPath = asPath;
            return this;
        }

        public SelectionRoute build() {
            return new SelectionRoute(this);
        }
    }
}
