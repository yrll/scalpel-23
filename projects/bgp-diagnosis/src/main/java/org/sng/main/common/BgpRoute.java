package org.sng.main.common;

import java.util.List;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

import com.google.gson.Gson;

public class BgpRoute {
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
    private String ipPrefix; 
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
    private List<String> curVpnName;


    // violated bgpRoute attrs
    private String toDeviceName; 
    private String fromDeviceName; 
    private String exRoutePolicy;
    private String imRoutePolicy;

    public Prefix getPrefix() {
        return Prefix.parse(ipPrefix);
    }

    public static BgpRoute deserialize(String jsonStr) {
        return new Gson().fromJson(jsonStr, BgpRoute.class);
    }

    public String getLatestVpnName() {
        if (curVpnName!=null) {
           return curVpnName.get(curVpnName.size()-1); 
        } else {
            return null;
        }
        
    }

    public String getToDevName() {
        return toDeviceName;
    }

    public String getFromDevName() {
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

}
