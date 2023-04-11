package org.sng.main.conditions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SelectionRoute implements Serializable {
    @Expose(serialize = false)
    private transient List<Ip> _nextHopIps;

    @SerializedName("nextHopIp")
    private List<String> _nextHopStrings;

    @SerializedName("asPath")
    private List<Long> _asPath;

    @Expose(serialize = false)
    private transient Prefix _network;

    @SerializedName("ipPrefix")
    private String _networkString;

    @SerializedName("vpnName")
    private String _vpnName;

    public SelectionRoute(Builder builder) {
        this._network = builder.network;
        this._asPath = builder.asPath;
        this._nextHopIps = builder.nextHopIps;

        this._networkString = _network.toString();
        this._nextHopStrings = transIpListToString(_nextHopIps);
        this._vpnName = builder.vpnName;
    }

    public List<String> transIpListToString(List<Ip> ips) {
        if (ips==null) {
            return null;
        }
        List<String> ipStrings = new ArrayList<>();
        ips.stream().forEach(t->ipStrings.add(t.toPrefix().toString()));
        return ipStrings;
    }

    // need rewrite
    public boolean ifMatch() {
        return false;
    }
    
    public static class Builder{
        private Prefix network;

        private String vpnName;

        private List<Ip> nextHopIps;

        private List<Long> asPath;

        public Builder(Prefix prefix) {
            this.network = prefix;
        }

        public Builder nextHop(List<Ip> nextHopIp) {
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
