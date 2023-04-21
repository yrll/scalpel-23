package org.sng.main.diagnosis;

import java.lang.reflect.Array;
import java.util.*;

import org.sng.main.util.KeyWord;


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
    
    String node;
    String vpnName;
    IpFamily ipFamily;
    String tunnelPolicyName;
    String routerDistinguisher;
    List<String> exportRtList;
    List<String> importRtList;

    Map<Integer, String> cfgLinesMap;
    
    // 其他属性暂不考虑
    public VpnInstance(String node, String name) {
        this.node = node;
        vpnName = name;
        routerDistinguisher = "";
        exportRtList = new ArrayList<>();
        importRtList = new ArrayList<>();
        cfgLinesMap = new HashMap<>();
    }

    public void addConfigLine(int num, String line) {
        this.cfgLinesMap.put(num, line);
    }

    public Map<Integer, String> getCfgLinesMap() {
        return cfgLinesMap;
    }

    public void setIpFamily(String ipFamilyName) {
        if (ipFamilyName.contains(IpFamily.IPV4.getName())) {
            this.ipFamily = IpFamily.IPV4;
        } else if (ipFamilyName.contains(IpFamily.IPV6.getName())) {
            this.ipFamily = IpFamily.IPV6;
        }
    }

    public void setTnlPolicyName(String name) {
        tunnelPolicyName = name;
    }

    public void setRouterDistinguisher(String rdName) {
        routerDistinguisher = rdName;
    }

    public void addExportRtList(String[] rts) {
        
        for (String rt : rts) {
            if (rt.contains(":")) {
                exportRtList.add(rt);
            }
        }
    }

    public void addImportRtList(String[] rts) {
        
        for (String rt : rts) {
            if (rt.contains(":")) {
                importRtList.add(rt);
            }
        }
    }


    public boolean addRtList(String[] rts, String type) {
        if (type.contains("export")) {
            addExportRtList(rts);
        } else if (type.contains("import")) {
            addImportRtList(rts);
        } else {
            return false;
        }
        return true;
    }

    public boolean isValid() {
        return routerDistinguisher!=null && !routerDistinguisher.equals("");
    }

    public String getRouteDistinguisher() {
        return routerDistinguisher;
    }

    public List<String> getExList() {
        return exportRtList;
    }

    public List<String> getImList() {
        return importRtList;
    }

    public String getVpnName() {
        return vpnName;
    }

    public String getNode() {
        return node;
    }

    public boolean canCrossFrom(VpnInstance vpnInstance) {
        if (vpnName.equals(KeyWord.PUBLIC_VPN_NAME) && vpnInstance.getVpnName().equals(KeyWord.PUBLIC_VPN_NAME)) {
            return true;
        }
        if (vpnInstance.getRouteDistinguisher().equals(routerDistinguisher)) {
            if (!Collections.disjoint(vpnInstance.getExList(), importRtList)) {
                return true;
            }
        }
        return false;
    }

}
