package org.sng.main.common;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

import com.google.gson.annotations.SerializedName;

public class Interface {
    private String deviceName;
    private String infName;
    private String vpnName;

    private transient Prefix infIpv4Ip;
    @SerializedName("infIpv4Ip")
    private String infIpv4IpString;

    private transient Ip infIpv4HostIp;
    @SerializedName("infIpv4HostIp")
    private String infIpv4HostIpString;

    public String getInfName() {
        return infName;
    }

}
