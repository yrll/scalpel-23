package org.sng.main.common;

import com.google.gson.annotations.SerializedName;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.diagnosis.Generator.Protocol;

// 静态/直连 路由
public class StaticRoute {
    private transient Prefix ipPrefix;

    private String deviceName;

    @SerializedName("addressIp")
    private String ipString;

    @SerializedName("ipPrefix")
    private String ipPrefixString;

    @SerializedName("vpnName")
    private String vpnName;

    @SerializedName("outInf")
    private Interface outInf;

    private String nextHop;

    private int preference = Protocol.STATIC.getPreference();

    private int lineNumber;

    public void checkPrefix() {
        ipPrefix = Prefix.parse(ipString);
        outInf.checkIp();
    }

    public void setInterface(Interface inf) {
        this.outInf = inf;
    }

    public String getVpnName() {
        return vpnName;
    }

    public boolean ifDirectRoute() {
        if (nextHop!=null && !nextHop.equals("")) {
            return false;
        } else {
            return true;
        }
    }

    public int getPref() {
        return preference;
    }

    public Ip getNextHop() {
        return Prefix.parse(nextHop).getEndIp();
    }

    public String getNextHopString() {
        return nextHop;
    }

    public String getOutInfName() {
        return outInf.getInfName();
    }

    public StaticRoute(String deviceName, String ipString, String vpnName, Interface outInf) {
        this.deviceName = deviceName;
        this.ipString = ipString;
        this.ipPrefix = Prefix.parse(ipString);
        this.vpnName = vpnName;
        this.outInf = outInf;
    }

    public StaticRoute(String deviceName, String ipString) {
        this.deviceName = deviceName;
        this.ipString = ipString;
        this.ipPrefix = Prefix.parse(ipString);
    }

    public StaticRoute(String deviceName, String ipString, String nextHop) {
        this.deviceName = deviceName;
        this.ipString = ipString;
        this.ipPrefix = Prefix.parse(ipString);
        this.nextHop = nextHop;
    }

    public String[] getKeyWords() {
        ipPrefix = Prefix.parse(ipString);
        String ipString = ipPrefix.getFirstHostIp().toString();
        String length = String.valueOf(ipPrefix.getPrefixLength());
        return new String[]{"route-static", ipString, length};
    }

    public Prefix getPrefix() {
        if (ipPrefix!=null) {
            return ipPrefix;
        }
        if (ipString!=null && !ipString.equals("")) {
            return Prefix.parse(ipString);
        } else if (ipPrefixString!=null && !ipPrefixString.equals("")) {
            return Prefix.parse(ipPrefixString);
        }
        return null;
    }

    public String getPrefixString() {
        return ipString;
    }

    public void setPreference(int pref) {
        this.preference = pref;
    }

    public void setLineNum(int num) {
        this.lineNumber = num;
    }

    public Interface getInterface() {
        return outInf;
    }
}
