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
import org.sng.main.diagnosis.Generator;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;


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
    private BgpTopology refBgpTopology;
    private Violation violation;
    // 表示创建的时候是通过其他节点的violation创建的，所以这里定位出的error lines要写入全局变量
    private boolean initializeFromOtherNode;

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

    public PeerLocalizer(String node1, String node2, Generator generator, Violation violation, boolean initializeFromOtherNode) {
        this.localNode = node1;
        this.remoteNode = node2;
        this.localCfgFilePath = BgpDiagnosis.cfgPathMap.get(node1);
        this.remoteCfgFilePath = BgpDiagnosis.cfgPathMap.get(node2);
        this.generator = generator;
        this.localPeer = generator.getBgpTopology().getBgpPeer(node1, node2);
        this.remotePeer = generator.getBgpTopology().getBgpPeer(node2, node1);
        this.violation = violation;
        this.initializeFromOtherNode = initializeFromOtherNode;
    }

    public PeerLocalizer(String node1, String node2, Generator generator, Violation violation, BgpTopology refBgpTopology, boolean initializeFromOtherNode) {
        this.localNode = node1;
        this.remoteNode = node2;
        this.localCfgFilePath = BgpDiagnosis.cfgPathMap.get(node1);
        this.remoteCfgFilePath = BgpDiagnosis.cfgPathMap.get(node2);
        this.generator = generator;
        this.localPeer = generator.getBgpTopology().getBgpPeer(node1, node2);
        this.remotePeer = generator.getBgpTopology().getBgpPeer(node2, node1);
        this.violation = violation;
        this.refBgpTopology = refBgpTopology;
        this.initializeFromOtherNode =initializeFromOtherNode;
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
            if (!localPeer.isConsistent(remotePeer)) {
                // ip或者as-num不一致, 至少有一个错了, 顺着诊断一遍
                if (!localPeer.getLocalIpString().equals(remotePeer.getPeerIpString())) {
                    errList.add(PeerErrorType.PEER_IP_INCONSISTENT_LOCAL);
                } 
                if (!remotePeer.getLocalIpString().equals(localPeer.getPeerIpString())) {
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
                if (!isLocalConnectInterface) {
                    errList.add(PeerErrorType.PEER_CONNECT_INTERFACE_LOCAL);
                }
                if (isLocalIgnorePeer) {
                    errList.add(PeerErrorType.PEER_IGNORE_LOCAL);
                }

                if (localPeer.getBgpPeerType()==BgpPeerType.EBGP) {
                    int atLeastHop = generator.hopNumberToReachIpUsingStatic(localNode, localPeer.getPeerIpString());
                    if (atLeastHop==0) {
                        errList.add(PeerErrorType.PEER_IP_REACH_LOCAL);
                    } else if (atLeastHop > localPeer.getEBgpMaxHop()) {
                        errList.add(PeerErrorType.EBGP_MAX_HOP_LOCAL);
                    }
                } else {
                    errList.add(PeerErrorType.PEER_IP_REACH_LOCAL);
                }

                // remoteNode
                boolean isRemoteConnectInterface = isConnectInterface(remoteNode);
                boolean isRemoteIgnorePeer = isIgnorePeer(remoteNode);
                if (!isRemoteConnectInterface) {
                    errList.add(PeerErrorType.PEER_CONNECT_INTERFACE_REMOTE);
                }
                if (isRemoteIgnorePeer) {
                    errList.add(PeerErrorType.PEER_IGNORE_REMOTE);
                }

                if (remotePeer.getBgpPeerType()==BgpPeerType.EBGP) {
                    int atLeastHop = generator.hopNumberToReachIpUsingStatic(remoteNode, remotePeer.getPeerIpString());
                    if (atLeastHop==0) {
                        errList.add(PeerErrorType.PEER_IP_REACH_REMOTE);
                    } else if (atLeastHop > remotePeer.getEBgpMaxHop()) {
                        errList.add(PeerErrorType.EBGP_MAX_HOP_REMOTE);
                    }
                } else {
                    errList.add(PeerErrorType.PEER_IP_REACH_REMOTE);
                }

            }
        } else if (localPeer!=null) {
            errList.add(PeerErrorType.PEER_NOT_CONFIGURED_REMOTE);
        } else if (remotePeer!=null){
            errList.add(PeerErrorType.PEER_NOT_CONFIGURED_LOCAL);
        }
        return errList;
    }

    public void putErrorLinesToGlobalErrorMap(String node, Map<Integer, String> lines) {

        if (BgpDiagnosis.errMap.containsKey(localNode)) {
            BgpDiagnosis.errMap.get(localNode).putAll(lines);
        } else {
            BgpDiagnosis.errMap.put(localNode, lines);
        }

    }

    @Override
    // 只查自身localNode可改的错
    public Map<Integer, String> getErrorConfigLines() {
        Map<Integer, String> lines = new HashMap<>();
        List<PeerErrorType> errorTypes = getErrorTypes();
        for (PeerErrorType err : errorTypes) {
            switch (err) {
                case PEER_AS_NUMBER_INCONSISTENT_LOCAL: {
                    String[] keyWords = {"peer", localPeer.getPeerIpString().toString(), String.valueOf(localPeer.getPeerAsNum())};
                    if (!initializeFromOtherNode) {
                        lines.putAll(ConfigTaint.peerTaint(localNode, keyWords));
                    } else {
                        if (BgpDiagnosis.errMap.containsKey(localNode)) {
                            BgpDiagnosis.errMap.get(localNode).putAll(ConfigTaint.peerTaint(localNode, keyWords));
                        } else {
                            BgpDiagnosis.errMap.put(localNode, ConfigTaint.peerTaint(localNode, keyWords));
                        }
                    }

                    break;
                }
                // case PEER_AS_NUMBER_INCONSISTENT_REMOTE: {
                //     String[] keyWords = {"peer", remotePeer.getPeerIpString().toString(), String.valueOf(remotePeer.getPeerAsNum())};
                //     lines.putAll(ConfigTaint.taint(remoteNode, keyWords));
                //     break;
                // }
                case PEER_CONNECT_INTERFACE_LOCAL: {
                    // 这个错误默认缺失对应语句
                    String line = "peer " + localPeer.getPeerIpString().toString() + " connect-interface " + localPeer.getLocalIpString().toString();
                    lines.put(violation.getMissingLine(), line);
                    break;
                }
                // case PEER_CONNECT_INTERFACE_REMOTE: {
                //     String line = "peer " + remotePeer.getPeerIpString().toString() + " connect-interface " + remotePeer.getLocalIpString().toString();
                //     lines.put(violation.getMissingLine(), line);
                //     break;
                // }
                case PEER_IGNORE_LOCAL: {
                    // 这个错误默认多写了
                }
                case PEER_IGNORE_REMOTE:

                case PEER_IP_INCONSISTENT_LOCAL:
                case PEER_IP_REACH_LOCAL: {
                    // IP不一致 就把所有 peer *ip* 有关的语句都找出来
                    String[] keyWords = {"peer", localPeer.getPeerIpString().toString()};
                    lines.putAll(ConfigTaint.taint(localNode, keyWords));
                    break;
                }
                // case PEER_IP_INCONSISTENT_REMOTE: 
                // case PEER_IP_REACH_REMOTE: {
                //     String[] keyWords = {"peer", remotePeer.getPeerIpString().toString()};
                //     lines.putAll(ConfigTaint.taint(remoteNode, keyWords));
                //     break;
                // }

                case PEER_NOT_CONFIGURED_LOCAL: {
                    String peerIp = remoteNode;
                    String localIp = localNode;
                    String asNumber = "as-number";
                    if (refBgpTopology!=null) {
                        peerIp = refBgpTopology.getNodeIp(remoteNode);
                        localIp = refBgpTopology.getNodeIp(localNode);
                        asNumber = Long.toString(refBgpTopology.getAsNumber(remoteNode));
                    }
                    // 因为有的peer只配了单边，但是bgp topo上两端的peer info都会缺失，所以要在配置里再检查一遍
                    Map<Integer, String> peerConfig = getPeerConfiguration(localNode, remoteNode, peerIp);
                    if (peerConfig.size()==0) {
                        List<String> missingLines = ConfigTaint.genMissingPeerConfigLines(localIp, peerIp, asNumber);
                        if (initializeFromOtherNode) {
                            missingLines.forEach(line->peerConfig.put(violation.getMissingLine(), line));
                            putErrorLinesToGlobalErrorMap(localNode, peerConfig);
                        } else {
                            missingLines.forEach(line->lines.put(violation.getMissingLine(), line));
                        }

                    } else {
                        if (initializeFromOtherNode) {
                            putErrorLinesToGlobalErrorMap(localNode, peerConfig);
                        } else {
                            lines.putAll(peerConfig);
                        }

                    }



                    break;
                }
                // case PEER_NOT_CONFIGURED_REMOTE: {
                //     String line1 = "peer " + remotePeer.getPeerIpString().toString() + " enable";
                //     lines.put(violation.getMissingLine(), line1);
                //     String line2 = "peer " + remotePeer.getPeerIpString().toString() + " connect-interface " + remotePeer.getLocalIpString().toString();
                //     lines.put(violation.getMissingLine(), line2);
                //     break;
                // }
                case EBGP_MAX_HOP_LOCAL: {
                    int realHop = generator.hopNumberToReachIpUsingStatic(localNode, localPeer.getPeerIpString());
                    String line = "peer " + localPeer.getPeerIpString().toString() + " ebgp-max-hop " + String.valueOf(realHop);
                    String[] keyWords = {"peer", localPeer.getPeerIpString().toString(), "ebgp-max-hop"};
                    lines.putAll(ConfigTaint.peerTaint(localNode, keyWords));
                    lines.put(violation.getMissingLine(), line);
                    break;
                }
                // case EBGP_MAX_HOP_REMOTE: {
                //     int realHop = generator.hopNumberToReachIpUsingStatic(remoteNode, remotePeer.getPeerIpString());
                //     String line = "peer " + remotePeer.getPeerIpString().toString() + " ebgp-max-hop " + String.valueOf(realHop);
                //     String[] keyWords = {"peer", remotePeer.getPeerIpString().toString(), "ebgp-max-hop"};
                //     lines.putAll(ConfigTaint.taint(remoteNode, keyWords));
                //     lines.put(violation.getMissingLine(), line);
                //     break;
                // }
                case UNKOWN_LOCAL: {
                    String[] keyWords = {"peer", localPeer.getPeerIpString().toString()};
                    lines.putAll(ConfigTaint.peerTaint(localNode, keyWords));
                    break;
                }
                // case UNKOWN_REMOTE: {
                //     String[] keyWords = {"peer", remotePeer.getPeerIpString().toString()};
                //     lines.putAll(ConfigTaint.taint(remoteNode, keyWords));
                //     break;
                // }
            }
        }
        return lines;
    }

    public Map<Integer, String> getPeerConfiguration(String localDev, String peerDev, String peerIpString) {
        String[] peerWords = {"peer", BgpTopology.transPrefixOrIpToIpString(peerIpString)};
        String[] enableWords = {"enable", "connect"};
        return ConfigTaint.peerTaint(localDev, peerWords);
    }

    private boolean isConnectInterface(String node) {
        // node上对 对端的peer配置 是否有connect-interface命令
        if (node.equals(localNode)) {
            String[] keyWords = {"peer", localPeer.getPeerIpString().toString(), "connect-interface"};
            return ConfigTaint.taint(node, keyWords).keySet().size()>0;
        } else if (node.equals(remoteNode)) {
            String[] keyWords = {"peer", remotePeer.getPeerIpString().toString(), "connect-interface"};
            return ConfigTaint.taint(node, keyWords).keySet().size()>0;
        } else {
            return false;
        }
        
        

    }

    private boolean isIgnorePeer(String node) {
        // node上对 对端的peer配置 是否有peer ignore命令
        String[] keyWords = {"peer", localPeer.getPeerIpString().toString(), "ignore"};
        if (ConfigTaint.taint(node, keyWords).keySet().size()>0) {
            return true;
        }
        return false;
    }


}
