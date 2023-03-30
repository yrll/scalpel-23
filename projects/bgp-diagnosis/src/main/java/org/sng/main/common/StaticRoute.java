package org.sng.main.common;

import com.google.gson.annotations.SerializedName;
import org.sng.datamodel.Prefix;

public class StaticRoute {
    private transient Prefix ipPrefix;

    private String deviceName;

    @SerializedName("addressIp")
    private String ipString;

    @SerializedName("vpnName")
    private String vpnName;

    @SerializedName("outInf")
    private Interface outInf;

    private int preference;

    private int lineNumber;

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

    public String[] getKeyWords() {
        ipPrefix = Prefix.parse(ipString);
        String ipString = ipPrefix.getFirstHostIp().toString();
        String length = String.valueOf(ipPrefix.getPrefixLength());
        return new String[]{"route-static", ipString, length};
    }

    public Prefix getPrefix() {
        ipPrefix = Prefix.parse(ipString);
        return ipPrefix;
    }

    public void setPreference(int pref) {
        this.preference = pref;
    }

    public void setLineNum(int num) {
        this.lineNumber = num;
    }
}
