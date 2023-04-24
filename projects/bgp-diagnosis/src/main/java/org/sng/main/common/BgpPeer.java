package org.sng.main.common;

import java.io.Serializable;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Ip6;
import org.sng.datamodel.Prefix;

import com.google.gson.JsonObject;
import org.sng.main.localization.Violation;

public class BgpPeer implements Serializable{

    public enum BgpPeerType {
        IBGP,
        EBGP
    }

    private static String LOCAL_DEV_NAME = "localDevName";
    private static String PEER_DEV_NAME = "peerDevName";
    private static String LOCAL_IP = "localIp";
    private static String PEER_IP = "peerIp";
    private static String lOCAL_AS_NUM = "localAsNum";
    private static String LOCAL_VPN_NAME = "localVpnName";

    // eBGP attributes
    private static String PEER_AS_NUM = "peerAsNum";
    private static String EBGP_MAX_HOP = "ebgpMaxHopNum";
    // iBGP attributes
    private static String RR_CLIENT = "rrclient";
    
    private BgpPeerType _type;

    @SerializedName("localDevName")
    private String _localDevName;

    @SerializedName("peerDevName")
    private String _peerDevName;

    // 这里的string可以是ipv4的，也可以是ipv6的
    @SerializedName("localIp")
    private String _localIpString;
    @SerializedName("peerIp")
    private String _peerIpString;

    private Ip _localIp;
    private Ip _peerIp;

    // 有的underlay是ipv6承载
    private Ip6 _localIp6;
    private Ip6 _peerIp6;

    @SerializedName("localAsNum")
    private long _localAsNum;

    @SerializedName("localVpnName")
    private String _localVpnName;

    @SerializedName("peerAsNum")
    private long _peerAsNum;

    @SerializedName("ebgpMaxHopNum")
    private int _ebgpMaxHop;

    @SerializedName("validTtlHops")
    private int _validTtlHops;

    @SerializedName("rrclient")
    private boolean _ifPeerClient;

//    public Ip getLocalIp() {
//        return _localIp;
//    }
//
//    public Ip getPeerIp() {
//        return _peerIp;
//

    public String getLocalIpString() {
        return BgpTopology.transPrefixOrIpToIpString(_localIpString);
    }

    public String getPeerIpString() {
        return BgpTopology.transPrefixOrIpToIpString(_peerIpString);
    }

    public Ip6 getLocalIp6() {
        return _localIp6;
    }

    public Ip6 getPeerIp6() {
        return _peerIp6;
    }

    public BgpPeerType getBgpPeerType() {
        return _type;
    }

    public long getLocalAsNum() {
        if (_type.equals(BgpPeerType.IBGP)) {
            return _peerAsNum;
        }
        return _localAsNum;
    }

    public long getPeerAsNum() {
        return _peerAsNum;
    }

    public int getEBgpMaxHop() {
        return _ebgpMaxHop;
    }

    public String getLocalDevName() {
        return _localDevName;
    }

    public String getPeerDevName() {
        return _peerDevName;
    }

    public boolean isClient() {
        return _ifPeerClient;
    }

    public boolean ifIpv6Peer() {
        // 每次获取ip的时候都预先判断一下
        return _localIp6!=null || _peerIp6!=null;
    }

    public void setPeerType(BgpPeerType type) {
        this._type = type;
    }

    public boolean ifPeerBetween(String node1, String node2) {
        return _localDevName.equals(node1) && _peerDevName.equals(node2);
    }

    public BgpPeer(String localDevName, String peerDevName, String localIp,
                    String peerIp, long localAsNum, String localVpnName,
                    boolean ifClient, BgpPeerType type) {
        _localDevName = localDevName;
        _peerDevName = peerDevName;
        _localIpString = localIp;
        _peerIpString = peerIp;
        if (!localIp.contains(":")) {
            _localIp = Ip.parse(localIp);
        } else if (localIp.contains(":")) {
            _localIp6 = Ip6.parse(localIp);
        }
        if (!peerIp.contains(":")) {
            _peerIp = Ip.parse(peerIp);
        } else if (peerIp.contains(":")) {
            _peerIp6 = Ip6.parse(peerIp);
        }

        _localAsNum = localAsNum;
        _localVpnName = localVpnName;
        _ifPeerClient = ifClient;
        _type = type;
    }

    public BgpPeer(String localDevName, String peerDevName, String localIp,
                    String peerIp, long localAsNum, String localVpnName,
                    long peerAsNum, int ebgpMaxHop, BgpPeerType type) {
        _localDevName = localDevName;
        _peerDevName = peerDevName;
        _localIpString = localIp;
        _peerIpString = peerIp;
        if (Ip.tryParse(localIp).isPresent()) {
            _localIp = Ip.parse(localIp);
        } else if (Ip6.tryParse(localIp).isPresent()) {
            _localIp6 = Ip6.parse(localIp);
        }
        if (Ip.tryParse(peerIp).isPresent()) {
            _peerIp = Ip.parse(peerIp);
        } else if (Ip6.tryParse(peerIp).isPresent()) {
            _peerIp6 = Ip6.parse(peerIp);
        }
        _localAsNum = localAsNum;
        _localVpnName = localVpnName;
        _peerAsNum = peerAsNum;
        _ebgpMaxHop = ebgpMaxHop;
        _type = type;
    }

    public static BgpPeer deserialize(JsonObject object) {
        BgpPeer peer = new Gson().fromJson(object, BgpPeer.class);

        peer.setPeerType(BgpPeerType.IBGP);
        if (object.get(EBGP_MAX_HOP)!=null) {
            peer.setPeerType(BgpPeerType.EBGP);
        }
        return peer;
    }

//    private boolean isConsistentIp(BgpPeer peer) {
//        if (ifIpv6Peer()) {
//            return
//        }
//    }

    public boolean isConsistent(BgpPeer peer) {
        if (!peer.getBgpPeerType().equals(_type)) {
            return false;
        }
        switch(_type) {
            case IBGP: {
                return (_localDevName.equals(peer.getPeerDevName()) && getLocalIpString().equals(peer.getPeerIpString()));
            }
            case EBGP: {
                return (_localAsNum==peer.getPeerAsNum() && _localDevName.equals(peer.getPeerDevName()) && getLocalIpString().equals(peer.getPeerIpString()));
            }
            default: return false;
        }

    }

    public boolean isLocalDev(String node) {
        return _localDevName.equals(node);
    }

    public boolean isPeerDev(String node) {
        return _peerDevName.equals(node);
    }


}
