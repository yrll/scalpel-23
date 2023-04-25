package org.sng.main.common;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections.ListUtils;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

import com.google.gson.Gson;

public class BgpRoute{
    public enum FromType{
        FROMORIGIN("FROMORIGIN"),
        FROMIBGP("FROMIBGP"),
        FROMEBGP("FROMEBGP");

        private String type;

        private FromType(String type) {
            this.type = type;
        }
    }

    public enum OriginType {
        EGP("EGP", 1),
        IGP("IGP", 2),
        INCOMPLETE("INCOMPLETE", 0);
      
        private final String _name;
      
        private final int _preference;
      
        OriginType(String originType, int preference) {
          _name = originType;
          _preference = preference;
        }

        public String getOriginTypeName() {
          return _name;
        }
      
        public int getPreference() {
          return _preference;
        }
      }

    // prov bgpRoute attrs
    private int id;
    private String deviceName;
    @SerializedName("ipPrefix")
    private String ipPrefixString;
    private String nextHopIp; 
    private String nextHopDevice;

    private String importType; // ? 
    private String originProtocol; 
    private int preferredValue; 
    private int localPreference;

    private String fromType; // ? 
    private List<Long> asPath;
    private OriginType origin;
    private String peerIp;
    private long routerId;  
    private int med;
    private List<Long> clusterList;
    // private String originalPreference;
    private List<String> curVpnList;


    // violated bgpRoute attrs
    private String toDeviceName; 
    private String fromDeviceName; 
    private String exRoutePolicy;
    private String imRoutePolicy;

    public String getDeviceName() {
        return deviceName;
    }

    public String getIpPrefixString() {
        return ipPrefixString;
    }

    public String getNextHopDevice() {
        return nextHopDevice;
    }

    public String getExRoutePolicy() {
        return exRoutePolicy;
    }

    public String getImportType() {
        return importType;
    }

    public String getOriginProtocol() {
        return originProtocol;
    }

    public List<String> getCurVpnList() {
        return curVpnList;
    }

    public Prefix getPrefix() {
        return Prefix.parse(ipPrefixString);
    }

    public static BgpRoute deserialize(String jsonStr) {
        return new Gson().fromJson(jsonStr, BgpRoute.class);
    }

    public String getLatestVpnName() {
        if (curVpnList !=null && curVpnList.size()>0) {
           return curVpnList.get(curVpnList.size()-1);
        } else {
            return null;
        }
        
    }

    public String getToDeviceName() {
        return toDeviceName;
    }

    public String getFromDeviceName() {
        return fromDeviceName;
    }

    public String getExportPolicyName() {
        return exRoutePolicy;
    }

    public String getImportPolicyName() {
        return imRoutePolicy;
    }

    public Ip getNextHopIp() {
        if (Prefix.tryParse(nextHopIp).isPresent()) {
            return Prefix.parse(nextHopIp).getEndIp();
        } else if (Ip.tryParse(nextHopIp).isPresent()) {
            return Ip.parse(nextHopIp);
        } else {
            return Ip.ZERO;
        }
    }

    public String getNextHopIpString() {
        return nextHopIp;
    }

    public Ip getPeerIp() {
        if (Prefix.tryParse(peerIp).isPresent()) {
            return Prefix.parse(peerIp).getEndIp();
        } else if (Ip.tryParse(peerIp).isPresent()) {
            return Ip.parse(peerIp);
        } else {
            return Ip.ZERO;
        }
    }

    public String getPeerIpString() {
        return peerIp;
    }

    public String getNextHopDev() {
        return nextHopDevice;
    }

    public boolean ifTwoStringEqual(String str1, String str2) {
        if (str1!=null && str2!=null) {
            return str1.equals(str2);
        } else if (str1!=null || str2!=null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof BgpRoute) {
            BgpRoute bgpRoute = (BgpRoute) object;
            return ifTwoStringEqual(bgpRoute.getDeviceName(), deviceName) &&
                    ifTwoStringEqual(bgpRoute.getFromDeviceName(), fromDeviceName) &&
                    ifTwoStringEqual(bgpRoute.getToDeviceName(), toDeviceName) &&
                    ifTwoStringEqual(bgpRoute.getPeerIpString(), peerIp) &&
                    ifTwoStringEqual(bgpRoute.getExportPolicyName(), exRoutePolicy) &&
                    ifTwoStringEqual(bgpRoute.getImportPolicyName(), importType) &&
                    ifTwoStringEqual(bgpRoute.getImportType(), importType) &&
                    ifTwoStringEqual(bgpRoute.getNextHopDev(), nextHopDevice) &&
                    ifTwoStringEqual(bgpRoute.getOriginProtocol(), originProtocol) &&
                    ListUtils.isEqualList(bgpRoute.getCurVpnList(), curVpnList);
        }
        return false;
    }

}
