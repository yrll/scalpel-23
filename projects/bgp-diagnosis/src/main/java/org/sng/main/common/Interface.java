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

    public Prefix getPrefix() {
        if (infIpv4Ip!=null) {
            return infIpv4Ip;
        } else if (infIpv4IpString!=null){
            if (infIpv4IpString.contains("/")) {
                return Prefix.parse(infIpv4IpString);
            }
            return Ip.parse(infIpv4HostIpString).toPrefix();
        }
        return null;
    }

    public void checkIp() {
        if (!hasPrefix()) {
            return;
        }
        infIpv4Ip = Prefix.parse(infIpv4IpString);
        infIpv4HostIp = Prefix.parse(infIpv4HostIpString).getEndIp();
    }

    public boolean hasPrefix() {
        return (infIpv4HostIpString!=null && infIpv4IpString!=null);
    }

    public String getDevName() {
        return deviceName;
    }

}
