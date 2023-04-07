package org.sng.main.common;

import java.io.Serializable;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;

import com.google.gson.JsonObject;

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
    private String _localDevName;
    private String _peerDevName;
    private Ip _localIp;
    private Ip _peerIp;
    private long _localAsNum;
    private String _localVpnName;

    private long _peerAsNum;
    private int _ebgpMaxHop;

    private boolean _ifPeerClient;

    public Ip getLocalIp() {
        return _localIp;
    }

    public Ip getPeerIp() {
        return _peerIp;
    }

    public BgpPeerType getBgpPeerType() {
        return _type;
    }

    public long getLocalAsNum() {
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

    public boolean ifPeerBetween(String node1, String node2) {
        return _localDevName.equals(node1) && _peerDevName.equals(node2);
    }

    public BgpPeer(String localDevName, String peerDevName, Ip localIp,
                    Ip peerIp, long localAsNum, String localVpnName, 
                    boolean ifClient, BgpPeerType type) {
        _localDevName = localDevName;
        _peerDevName = peerDevName;
        _localIp = localIp;
        _peerIp = peerIp;
        _localAsNum = localAsNum;
        _localVpnName = localVpnName;
        _ifPeerClient = ifClient;
        _type = type;
    }

    public BgpPeer(String localDevName, String peerDevName, Ip localIp,
                    Ip peerIp, long localAsNum, String localVpnName, 
                    long peerAsNum, int ebgpMaxHop, BgpPeerType type) {
        _localDevName = localDevName;
        _peerDevName = peerDevName;
        _localIp = localIp;
        _peerIp = peerIp;
        _localAsNum = localAsNum;
        _localVpnName = localVpnName;
        _peerAsNum = peerAsNum;
        _ebgpMaxHop = ebgpMaxHop;
        _type = type;
    }

    public static BgpPeer deserialize(JsonObject object) {
        BgpPeerType type = BgpPeerType.EBGP;

        if (object.get(EBGP_MAX_HOP)==null) {
            type = BgpPeerType.IBGP;
            return new BgpPeer(object.get(LOCAL_DEV_NAME).getAsString(), 
                            object.get(PEER_DEV_NAME).getAsString(), 
                            Prefix.parse(object.get(LOCAL_IP).getAsString()).getEndIp(), 
                            Prefix.parse(object.get(PEER_IP).getAsString()).getEndIp(), 
                            Long.valueOf(object.get(PEER_AS_NUM).getAsString()),
                            object.get(LOCAL_VPN_NAME).toString(),
                            Boolean.parseBoolean(object.get(RR_CLIENT).toString()),
                            type);
        
        } else {
            // type = BgpPeerType.IBGP;
            return new BgpPeer(object.get(LOCAL_DEV_NAME).getAsString(), 
                            object.get(PEER_DEV_NAME).getAsString(), 
                            Prefix.parse(object.get(LOCAL_IP).getAsString()).getEndIp(), 
                            Prefix.parse(object.get(PEER_IP).getAsString()).getEndIp(), 
                            Long.valueOf(object.get(lOCAL_AS_NUM).getAsString()),
                            object.get(LOCAL_VPN_NAME).toString(),
                            Long.valueOf(object.get(PEER_AS_NUM).getAsString()),
                            Integer.valueOf(object.get(EBGP_MAX_HOP).getAsString()),
                            type);

        }

        
    }

    public boolean isConsistent(BgpPeer peer) {
        if (!peer.getBgpPeerType().equals(_type)) {
            return false;
        }
        switch(_type) {
            case IBGP: {
                return (_localAsNum==peer.getLocalAsNum() && _localDevName.equals(peer.getPeerDevName()) && _localIp.equals(peer.getPeerIp()));
            }
            case EBGP: {
                return (_peerAsNum==peer.getLocalAsNum() && _localDevName.equals(peer.getPeerDevName()) && _localIp.equals(peer.getPeerIp()));
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
