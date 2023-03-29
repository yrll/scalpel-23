package org.sng.main.common;

import org.checkerframework.checker.units.qual.Prefix;

import com.google.gson.annotations.SerializedName;

public class StaticRoute {
    private transient Prefix _ipPrefix;

    private String deviceName;

    @SerializedName("ipPrefix")
    private String ipString;

    @SerializedName("vpnName")
    private String vpnName;

    @SerializedName("outInf")
    private Interface outInf;

    public String getOutInfName() {
        return outInf.getInfName();
    }
}
