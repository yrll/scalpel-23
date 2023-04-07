package org.sng.main.diagnosis;

import java.util.List;

public class VpnInstance {
    
    public enum IpFamily{
        IPV4("ipv4-family"),
        IPV6("ipv6-family");

        String name;
        IpFamily(String name){
            this.name = name;
        }

        String getName() {
            return name;
        }
    }
    
    String vpnName;
    IpFamily ipFamily;
    String RD;
    List<String> exportRTList;
    List<String> importRTList;
    
    // 其他属性暂不考虑

}
