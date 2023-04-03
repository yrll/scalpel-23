package org.sng.main.localization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sng.main.BgpDiagnosis;
import org.sng.main.common.BgpPeer;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.BgpPeer.BgpPeerType;
import org.sng.main.forwardingtree.Generator;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

import com.google.common.net.PercentEscaper;

/*
 * Localize "violateIbgpPeer"/"violateEbgpPeer" errors 
 */
public class PeerLocalizer implements Localizer{
    private String localNode;
    private String remoteNode;
    private BgpPeer localPeer;
    private BgpPeer remotePeer;
    private String localCfgFilePath;
    private String remoteCfgFilePath;
    private Generator generator;
    private Violation violation;

    public enum PeerErrorType{
        PEER_IP_REACH_LOCAL,
        PEER_IP_REACH_REMOTE,

        ACL_FILTER_TCP_PORT,

        PEER_AS_NUMBER_INCONSISTENT_LOCAL,
        PEER_AS_NUMBER_INCONSISTENT_REMOTE,

        PEER_IP_INCONSISTENT_LOCAL,
        PEER_IP_INCONSISTENT_REMOTE,

        EBGP_MAX_HOP_LOCAL,
        EBGP_MAX_HOP_REMOTE,

        PEER_IGNORE_LOCAL,
        PEER_IGNORE_REMOTE,

        PEER_CONNECT_INTERFACE_LOCAL,
        PEER_CONNECT_INTERFACE_REMOTE,

        PEER_NOT_CONFIGURED_LOCAL,
        PEER_NOT_CONFIGURED_REMOTE,

        UNKOWN_LOCAL,
        UNKOWN_REMOTE;
    }

    public PeerLocalizer(String node1, String node2, Generator generator, Violation violation) {
        this.localNode = node1;
        this.remoteNode = node2;
        this.localCfgFilePath = BgpDiagnosis.cfgPathMap.get(node1);
        this.remoteCfgFilePath = BgpDiagnosis.cfgPathMap.get(node2);
        this.generator = generator;
        this.localPeer = generator.getBgpTopology().getBgpPeer(node1, node2);
        this.remotePeer = generator.getBgpTopology().getBgpPeer(node2, node1);
        this.violation = violation;
    }

    public List<PeerErrorType> getErrorTypes() {
        List<PeerErrorType> errList = new ArrayList<PeerErrorType>();
        // 逐项排查
        if (localPeer==null && remotePeer==null) {
            errList.add(PeerErrorType.PEER_NOT_CONFIGURED_LOCAL);
            errList.add(PeerErrorType.PEER_NOT_CONFIGURED_LOCAL);
            return errList;
        } else if (localPeer!=null && remotePeer!=null) {
            // 两边都配过peer, 是不一致的问题
            if (localPeer.isConsistent(remotePeer)) {
                // ip或者as-num不一致, 至少有一个错了, 顺着诊断一遍
                if (!localPeer.getLocalIp().equals(remotePeer.getPeerIp())) {
                    errList.add(PeerErrorType.PEER_IP_INCONSISTENT_LOCAL);
                } 
                if (!remotePeer.getLocalIp().equals(localPeer.getPeerIp())) {
                    errList.add(PeerErrorType.PEER_IP_INCONSISTENT_REMOTE);
                }
                if (localPeer.getLocalAsNum()!=remotePeer.getPeerAsNum()) {
                    errList.add(PeerErrorType.PEER_AS_NUMBER_INCONSISTENT_REMOTE);
                } 
                if (remotePeer.getLocalAsNum()!=localPeer.getPeerAsNum()) {
                    errList.add(PeerErrorType.PEER_AS_NUMBER_INCONSISTENT_LOCAL);
                }
            } else {
                // local和remote节点逐个排查【codes need improving】
                // localNode
                boolean isLocalConnectInterface = isConnectInterface(localNode);
                boolean isLocalIgnorePeer = isIgnorePeer(localNode);
                if (isLocalConnectInterface) {
                    errList.add(PeerErrorType.PEER_CONNECT_INTERFACE_LOCAL);
                }
                if (isLocalIgnorePeer) {
                    errList.add(PeerErrorType.PEER_IGNORE_LOCAL);
                }
                if (!isLocalConnectInterface && !isLocalIgnorePeer) {
                    if (localPeer.getBgpPeerType()==BgpPeerType.EBGP) {
                        int realHop = generator.hopNumberToReachIpUsingStatic(localNode, localPeer.getPeerIp());
                        if (realHop==0) {
                            errList.add(PeerErrorType.PEER_IP_REACH_LOCAL);
                        } else if (realHop < localPeer.getEBgpMaxHop()) {
                            errList.add(PeerErrorType.EBGP_MAX_HOP_LOCAL);
                        } else {
                            errList.add(PeerErrorType.UNKOWN_LOCAL);
                        }
                    } else {
                        errList.add(PeerErrorType.PEER_IP_REACH_LOCAL);
                    }
                }
                // remoteNode
                boolean isRemoteConnectInterface = isConnectInterface(remoteNode);
                boolean isRemoteIgnorePeer = isIgnorePeer(remoteNode);
                if (isRemoteConnectInterface) {
                    errList.add(PeerErrorType.PEER_CONNECT_INTERFACE_REMOTE);
                }
                if (isRemoteIgnorePeer) {
                    errList.add(PeerErrorType.PEER_IGNORE_REMOTE);
                }
                if (!isRemoteConnectInterface && !isRemoteIgnorePeer) {
                    if (remotePeer.getBgpPeerType()==BgpPeerType.EBGP) {
                        int realHop = generator.hopNumberToReachIpUsingStatic(remoteNode, remotePeer.getPeerIp());
                        if (realHop==0) {
                            errList.add(PeerErrorType.PEER_IP_REACH_REMOTE);
                        } else if (realHop < remotePeer.getEBgpMaxHop()) {
                            errList.add(PeerErrorType.EBGP_MAX_HOP_REMOTE);
                        } else {
                            errList.add(PeerErrorType.UNKOWN_REMOTE);
                        }
                    } else {
                        errList.add(PeerErrorType.PEER_IP_REACH_REMOTE);
                    }
                }
            }
        } else if (localPeer!=null) {
            errList.add(PeerErrorType.PEER_NOT_CONFIGURED_REMOTE);
        } else {
            errList.add(PeerErrorType.PEER_NOT_CONFIGURED_LOCAL);
        }
        return errList;
    }

    @Override
    public Map<Integer, String> getErrorConfigLines() {
        Map<Integer, String> lines = new HashMap<>();
        List<PeerErrorType> errorTypes = getErrorTypes();
        for (PeerErrorType err : errorTypes) {
            switch (err) {
                case PEER_AS_NUMBER_INCONSISTENT_LOCAL: {
                    String[] keyWords = {"peer", localPeer.getPeerIp().toString(), String.valueOf(localPeer.getPeerAsNum())};
                    lines.putAll(ConfigTaint.taint(localNode, keyWords));
                }
                case PEER_AS_NUMBER_INCONSISTENT_REMOTE: {
                    String[] keyWords = {"peer", remotePeer.getPeerIp().toString(), String.valueOf(remotePeer.getPeerAsNum())};
                    lines.putAll(ConfigTaint.taint(remoteNode, keyWords));
                }
                case PEER_CONNECT_INTERFACE_LOCAL: {
                    // 这个错误默认缺失对应语句
                    String line = "peer" + localPeer.getPeerIp().toString() + "connect-interface" + localPeer.getLocalIp().toString();
                    lines.put(violation.getMissingLine(), line);
                }
                case PEER_CONNECT_INTERFACE_REMOTE: {
                    String line = "peer" + remotePeer.getPeerIp().toString() + "connect-interface" + remotePeer.getLocalIp().toString();
                    lines.put(violation.getMissingLine(), line);
                }
                case PEER_IGNORE_LOCAL: {
                    // 这个错误默认多写了
                }
                case PEER_IGNORE_REMOTE:

                case PEER_IP_INCONSISTENT_LOCAL:
                case PEER_IP_REACH_LOCAL: {
                    // IP不一致 就把所有 peer *ip* 有关的语句都找出来
                    String[] keyWords = {"peer", localPeer.getPeerIp().toString()};
                    lines.putAll(ConfigTaint.taint(localNode, keyWords));
                }
                case PEER_IP_INCONSISTENT_REMOTE: 
                case PEER_IP_REACH_REMOTE: {
                    String[] keyWords = {"peer", remotePeer.getPeerIp().toString()};
                    lines.putAll(ConfigTaint.taint(remoteNode, keyWords));
                }

                case PEER_NOT_CONFIGURED_LOCAL: {
                    String line1 = "peer" + localPeer.getPeerIp().toString() + "enable";
                    lines.put(violation.getMissingLine(), line1);
                    String line2 = "peer" + localPeer.getPeerIp().toString() + "connect-interface" + localPeer.getLocalIp().toString();
                    lines.put(violation.getMissingLine(), line2);
                }
                case PEER_NOT_CONFIGURED_REMOTE: {
                    String line1 = "peer" + remotePeer.getPeerIp().toString() + "enable";
                    lines.put(violation.getMissingLine(), line1);
                    String line2 = "peer" + remotePeer.getPeerIp().toString() + "connect-interface" + remotePeer.getLocalIp().toString();
                    lines.put(violation.getMissingLine(), line2);
                }
                case EBGP_MAX_HOP_LOCAL: {
                    String line = "peer" + localPeer.getPeerIp().toString();
                }
                case EBGP_MAX_HOP_REMOTE:
                case UNKOWN_LOCAL:
                case UNKOWN_REMOTE:
            }
        }
        return new HashMap<Integer, String>();
    }

    private boolean isConnectInterface(String node) {
        // node上对 对端的peer配置 是否有connect-interface命令
        String[] keyWords = {"peer", localPeer.getPeerIp().toString(), "connect-interface"};
        if (ConfigTaint.taint(node, keyWords).keySet().size()>0) {
            return true;
        }
        return false;
    }

    private boolean isIgnorePeer(String node) {
        // node上对 对端的peer配置 是否有peer ignore命令
        String[] keyWords = {"peer", localPeer.getPeerIp().toString(), "ignore"};
        if (ConfigTaint.taint(node, keyWords).keySet().size()>0) {
            return true;
        }
        return false;
    }


}
