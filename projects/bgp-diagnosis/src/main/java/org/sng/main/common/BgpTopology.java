package org.sng.main.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import org.sng.datamodel.Ip;
import org.sng.main.BgpDiagnosis;
import org.sng.main.Main;
import org.sng.main.diagnosis.Generator.Protocol;
import org.sng.main.util.KeyWord;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 * The BgpTopology is generated from bgp peer configuration
 * Router/Node/Device means the same thing 
 */

public class BgpTopology {
    // The raw peer configuration
    List<BgpPeer> _peers;
    /*
     * Metrics of validation:
     * 1. Both of nodes have configured each other as the peer and the peer ip is consistent
     * 2. For iBGP peer, we assume that their peer ip can communicate with each other
     * TODO: 3. For eBGP peer, ...
     */
    // The valid peer Info table
    Table<String, String, BgpPeer> _peerTable;
    // The router-id map for routers
    public static Map<String, String> _allNodes;
    // The as-number map for routers 
    Map<String, Long> _asNumMap;
    // store the inconsistent peer pair which violates the validation metrics above
    Map<BgpPeer, BgpPeer> _inconsistentPeers;
    // 每个node的peers，包含配了后无法建立session的peer，用于寻路
    Map<String, Set<String>> _configuredPeerMap;

    Set<String> _failedDevs;

    public Set<String> getFailedDevs() {
        return _failedDevs;
    }

    public BgpTopology(Set<String> failedDevs) {
        _failedDevs = failedDevs;
        _asNumMap = new HashMap<>();
        _allNodes = new HashMap<>();
        _peers = new ArrayList<>();
        _peerTable = HashBasedTable.create();
        _inconsistentPeers = new HashMap<>();
        _configuredPeerMap =  new HashMap<>();
    }

    public Set<String> getAllDevs() {
        return _allNodes.keySet();
    }

    public Protocol getNodesRelation(String node1, String node2) {
        if (node1.equals(node2)) {
            return Protocol.BGP_LOCAL;
        } else if (getAsNumber(node2)==getAsNumber(node1)) {
            return Protocol.IBGP;
        } else {
            return Protocol.EBGP;
        }
    }

    public Set<String> getAllNodesInSameAs(String node) {
        long asNumber = getAsNumber(node);
        if (_peers==null || _peers.size()<1) {
            return new HashSet<String>(Arrays.asList(node));
        } else {
            Set<String> nodes = new HashSet<>();
            _peers.forEach(p->{
                // fail节点判断
                if (p.getLocalAsNum()==asNumber && !_failedDevs.contains(p.getLocalDevName())) {
                    nodes.add(p.getLocalDevName());
                }
            });
            return nodes;
        }
    }

    // TODO: 节点到终点的所有简单路径的节点集合
    public Set<String> getAllNodesInSimplePaths(String node) {
        return getAllNodesInSameAs(node);
    }

    // 获取node（输入1）的所有type类型的，且在nodes（输入2）中的peer，无论peer的配置是否有效
    public Set<String> getBgpPeers(String node, Set<String> nodes, BgpPeer.BgpPeerType type) {
        if (_failedDevs.contains(node)) {
            return null;
        }
        Set<String> peers = new HashSet<>();
        if (nodes!=null) {
            nodes.stream().forEach(n->{
                if (!_failedDevs.contains(n)) {
                    // 简化版写法，应该要在peer list里遍历判断的，但是目前传入的nodes都是意图上要建立peer的点对，所以只判断as-number号
                    if (type==BgpPeer.BgpPeerType.IBGP && getAsNumber(node)==getAsNumber(n)) {
                        peers.add(n);
                    } else if (type==BgpPeer.BgpPeerType.EBGP && getAsNumber(node)!=getAsNumber(n)) {
                        peers.add(n);
                    }
                }

            });

        }
        if (peers.size()<1) {
            return null;
        }
        return peers;
    }

    // 只要有一个节点在对应as中，就返回true “设置rr-client条件的时候用”
    public boolean hasNodeInAs(long asNumber, List<String> nodes) {
        for (String node : nodes) {
            if (getAsNumber(node)==asNumber && !_failedDevs.contains(node)) {
                return true;
            }
        }
        return false;
    }



    public List<String> getNodesInAs(long asNumber, Set<String> nodes) {
        List<String> targetNodes = new ArrayList<>(nodes);
        for (String node : nodes) {
            if (getAsNumber(node)!=asNumber && !_failedDevs.contains(node)) {
                targetNodes.remove(node);
            }
        }
        return targetNodes;
    }

    public boolean ifConfiguredRRClient(String node1, String node2) {
        // node1是否配置了node2作为反射客户【peer不valid也可以】
        if (isValidPeer(node1, node2)) {
            BgpPeer bgpPeer = getValidPeer(node1, node2);
            return bgpPeer.isClient();
        } else {
            for (BgpPeer bgpPeer : _inconsistentPeers.keySet()) {
                if (bgpPeer.ifPeerBetween(node1, node2)) {
                    return bgpPeer.isClient();
                }
            }
        }
        return false;
    }

    public Long getAsNumber(String node) {
        if (_asNumMap.containsKey(node)) {
            return _asNumMap.get(node);
        } else {
            // TODO 会有这种情况？
            return null;
        }
        
    }

    public Map<String, String> getAllNodes() {
        return _allNodes;
    }

    public static String transPrefixOrIpToIpString(String str) {
        if (str.contains("/")) {
            str = str.split("/")[0];
        }
        return str;
    }

    public static String transPrefixOrIpToPrefixString(String str) {
        if (!str.contains("/")) {
            str += "/32";
        }
        return str;
    }

    public String getNodeIp(String node) {
        if (_allNodes.containsKey(node)) {
            String nodeIp =  _allNodes.get(node);
            if (nodeIp==null) {
                return null;
            }
            return transPrefixOrIpToIpString(nodeIp);
        }
        return null;
    }

    public Table<String, String, BgpPeer> getPeerTable() {
        return _peerTable;
    }

    public void genBgpPeersFromJsonFile(String filePath) {
        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);

        System.out.println("Deserialize peer info...");

        String jsonStr = BgpDiagnosis.fromJsonToString(filePath);
        
        // peer文件为空
        if (jsonStr==null || jsonStr.equals("")) {
            return;
        }

        // 检测peer的单边配置
        List<String> tmpPeerNameMap = new ArrayList<>();

        JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();
        for (JsonElement allObject : jsonObject.asMap().values()) {
            System.out.println("PEER_SIZE: " + allObject.getAsJsonArray().size());

            for (JsonElement object : allObject.getAsJsonArray()) {
                // deserialize each peer
                BgpPeer bgpPeer = BgpPeer.deserialize(object.getAsJsonObject());
                String localDevName = bgpPeer.getLocalDevName();
                String peerDevName = bgpPeer.getPeerDevName();
                if (_failedDevs.contains(localDevName) || _failedDevs.contains(peerDevName)) {
                    continue;
                }

                // 存node - ip - asNumber 映射
                if (!_allNodes.containsKey(localDevName)) {
                    // 以配置里本机的ip为主，作为本机的peer ip
                    // 如果配置里完全没配bgp协议，或者协议里没配peer，则不会加入allNodes
                    _allNodes.put(localDevName, bgpPeer.getLocalIpString());
                    _asNumMap.put(localDevName, bgpPeer.getLocalAsNum());
                }
                
                // c处理valid和invalid的peer
                if (_peerTable.contains(peerDevName, localDevName)) {
                    // unilateral peer configuration detection
                    tmpPeerNameMap.remove(peerDevName+"|"+localDevName);
                    if (!_peerTable.get(peerDevName, localDevName).isConsistent(bgpPeer)) {
                        // 保存不一致的BgpPeer pair
                        _inconsistentPeers.put(_peerTable.get(peerDevName, localDevName), bgpPeer);
                        // 确保peerTable里的所有表项对应的peer配置都是valid的
                        _peerTable.remove(peerDevName, localDevName);
                    } else {
                        _peerTable.put(localDevName, peerDevName, bgpPeer);
                    }
                } else {
                    if (Main.printLog) {
                        printPeerInfo(bgpPeer);
                    }

                    _peerTable.put(localDevName, peerDevName, bgpPeer);
                    // unilateral peer configuration detection
                    tmpPeerNameMap.add(localDevName+"|"+peerDevName);
                }
                _peers.add(bgpPeer);

                // 存每个节点已配的peer，这里单向的配置按照双向的处理
                //（所以除非两边都没配我们才认为就是不连通的）
                if (!_configuredPeerMap.containsKey(localDevName)) {
                    _configuredPeerMap.put(localDevName, new HashSet<>());
                }
                if (!_configuredPeerMap.containsKey(peerDevName)) {
                    _configuredPeerMap.put(peerDevName, new HashSet<>());
                } 
                _configuredPeerMap.get(localDevName).add(peerDevName);
                _configuredPeerMap.get(peerDevName).add(localDevName);

            }
            // unilateral 配置处理
            for (String pair : tmpPeerNameMap) {
                String[] peer = pair.split("|");
                if (_peerTable.contains(peer[0], peer[1])) {
                    BgpPeer bgpPeer = _peerTable.remove(peer[0], peer[1]);
                    _inconsistentPeers.put(bgpPeer, null);
                }
            }
        }
        if (Main.printLog) {
            System.out.println("VALID PEER PAIR SIZE: "+ _peerTable.size());
        }

    }

    private void printPeerInfo(BgpPeer peer) {
        System.out.println(KeyWord.PRINT_LINE);
        System.out.println(peer.getBgpPeerType());
        System.out.println(peer.getLocalDevName()+","+peer.getPeerDevName());
    }

    public boolean isValidPeer(String n1, String n2) {
        return getValidPeer(n1, n2)!=null;
    }

    public BgpPeer getValidPeer(String n1, String n2) {
        if (_failedDevs.contains(n1) || _failedDevs.contains(n2)) {
            return null;
        }
        if (_peerTable.contains(n1, n2)) {
            return _peerTable.get(n1, n2);
        } else {
            return null;
        }
    }

    // 可以是invalid的
    public BgpPeer getBgpPeer(String localDev, String peerDev) {
        if (isValidPeer(localDev, peerDev)) {
            return getValidPeer(localDev, peerDev);
        } else {
            for (BgpPeer bgpPeer : _inconsistentPeers.keySet()) {
                if (bgpPeer.isLocalDev(localDev)) {
                    return bgpPeer;
                }
            }
        }
        return null;
    }

    public boolean isConfiguredPeer(String n1, String n2) {
        if (isValidPeer(n1, n2)) {
            return true;
        } else {
            for (BgpPeer bgpPeer : _inconsistentPeers.keySet()) {
                if (bgpPeer.isLocalDev(n1) || bgpPeer.isPeerDev(n1)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean ifHasNotConfiguredPeer(String n1, String n2) {
        if (_allNodes.containsKey(n1) && _allNodes.containsKey(n2)) {
            // 配了协议和至少一个其他的peer
            if (isValidPeer(n1, n2)) {
                // 节点间配了peer，且valid
                return false;
            } else {
                // n1上配了n2为peer，但是invalid，返回false
                // n1上根本没配过n2为peer，返回true
                return !isConfiguredPeer(n1, n2);
            }
        }
        // 可能是协议就没配对：没配peer
        return true;
    }

    public String getNodeNameFromIp(String ip) {
        ip = transPrefixOrIpToIpString(ip);
        for (String node : _allNodes.keySet()) {
            if (_allNodes.get(node).toString().equals(ip)) {
                return node;
            }
        }
        return "";
    }

    public String getNodeNameFromIp(Ip ip) {
        for (String node : _allNodes.keySet()) {
            if (_allNodes.get(node).equals(ip)) {
                return node;
            }
        }
        return "";
    }

    // 两个node是否有配了的直连peer（可以invalid）
    private boolean directConnect(String node1, String node2) {
        if (!_configuredPeerMap.containsKey(node1)) {
            return false;
        }
        return _configuredPeerMap.get(node1).contains(node2);
    }

    // 判断两个节点在BGP topology上是否可达（可以invalid）
    public boolean ifConnected(String node1, String node2) {
        // generates the connection component using BFS
        if (!getAllDevs().contains(node1) || !getAllDevs().contains(node2)) {
            return false;
        }
        Map <String, Boolean> visitedMap = new HashMap<>();
        // vistedMap initialization
        _allNodes.forEach((node, ip)->{
            visitedMap.put(node, false);
        });
        // 以node1为起点开始BFS遍历它的neighbors
        Queue<String> queue = new LinkedList<String>();
        queue.add(node1);
        while(!queue.isEmpty()) {
            String curNode = queue.poll();
            visitedMap.put(curNode, true);
            for (String peer : visitedMap.keySet()) {
                if (directConnect(curNode, peer) && !visitedMap.get(peer)) {
                    queue.add(peer);
                    visitedMap.put(peer, true);
                }
            }
        }
        // 最终visited标志位为true的表示node1所在连通分支
        return visitedMap.get(node2);

    }

    // TODO: 如果topology一开始就不连通？怎么连通？提前连通还是根据需求连通？
    // 会出现这种情况么？那岂不是没有一条正确的流? ? 【有可能，如果有些节点配的静态】
    public List<String> getUnconfiguredNodes(String cfgRootPath) {
        return null;
    }

    public Set<String> getConfiguredPeers(String node) {
        return _configuredPeerMap.get(node);
    }
 
}
