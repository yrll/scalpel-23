package org.sng.main.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.lang.model.element.Element;

import org.apache.commons.io.FileUtils;
import org.sng.datamodel.Ip;
import org.sng.main.forwardingtree.Node;
import org.sng.util.KeyWord;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    Map<String, Ip> _allNodes;
    // The as-number map for routers 
    Map<String, Long> _asNumMap;
    // store the inconsistent peer pair which violates the validation metrics above
    Map<BgpPeer, BgpPeer> _inconsistentPeers;


    public BgpTopology() {
        _asNumMap = new HashMap<>();
        _allNodes = new HashMap<>();
        _peers = new ArrayList<>();
        _peerTable = HashBasedTable.create();
        _inconsistentPeers = new HashMap<>();
    }

    // 获取node（输入1）的所有type类型的，且在nodes（输入2）中的peer，无论peer的配置是否有效
    public List<String> getBgpPeers(String node, List<String> nodes, BgpPeer.BgpPeerType type) {
        List<String> peers = new ArrayList<>();
        if (nodes!=null) {
            nodes.stream().forEach(n->{
                // 简化版写法，应该要在peer list里遍历判断的，但是目前传入的nodes都是意图上要建立peer的点对，所以只判断as-number号
                if (type==BgpPeer.BgpPeerType.IBGP && getAsNumber(node)==getAsNumber(n)) {
                    peers.add(n);
                } else if (type==BgpPeer.BgpPeerType.EBGP && getAsNumber(node)!=getAsNumber(n)) {
                    peers.add(n);
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
            if (getAsNumber(node)==asNumber) {
                return true;
            }
        }
        return false;
    }

    public long getAsNumber(String node) {
        return _asNumMap.get(node);
    }

    public Map<String, Ip> getAllNodes() {
        return _allNodes;
    }

    public Ip getNodeIp(String node) {
        return _allNodes.get(node);
    }

    public Table<String, String, BgpPeer> getPeerTable() {
        return _peerTable;
    }

    public void genBgpPeersFromJsonFile(String filePath) {
        System.out.println("Deserialize peer info...");

        File file = new File(filePath);
        String jsonStr;
        // 检测peer的单边配置
        List<String> tmpPeerNameMap = new ArrayList<>();

        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");

            JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();
            for (JsonElement allObject : jsonObject.asMap().values()) {
                System.out.println("peerSize: " + allObject.getAsJsonArray().size());
                for (JsonElement object : allObject.getAsJsonArray()) {
                    // deserialize each peer
                    BgpPeer bgpPeer = BgpPeer.deserialize(object.getAsJsonObject());
                    String localDevName = bgpPeer.getLocalDevName();
                    String peerDevName = bgpPeer.getPeerDevName();

                    
                    
                    if (!_allNodes.containsKey(localDevName)) {
                        // 以配置里本机的ip为主，作为本机的peer ip
                        // 如果配置里完全没配bgp协议，或者协议里没配peer，则不会加入allNodes
                        _allNodes.put(localDevName, bgpPeer.getLocalIp());
                        _asNumMap.put(localDevName, bgpPeer.getLocalAsNum());
                    }
                    
                    if (_peerTable.contains(peerDevName, localDevName)) {
                        // unilateral peer configuration detection
                        tmpPeerNameMap.remove(peerDevName+"|"+localDevName);
                        if (!_peerTable.get(peerDevName, localDevName).isConsistent(bgpPeer)) {
                            // 保存不一致的BgpPeer pair
                            _inconsistentPeers.put(_peerTable.get(peerDevName, localDevName), bgpPeer);
                            // 确保peerTable里的所有表项对应的peer配置都是valid的
                            _peerTable.remove(peerDevName, localDevName);
                        }
                    } else {
                        printPeerInfo(bgpPeer);
                        _peerTable.put(localDevName, peerDevName, bgpPeer);
                        // unilateral peer configuration detection
                        tmpPeerNameMap.add(localDevName+"|"+peerDevName);
                    }
                    _peers.add(bgpPeer);
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
            System.out.println("valid peer pair: "+ _peerTable.size());
     
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void printPeerInfo(BgpPeer peer) {
        System.out.println(KeyWord.PRINT_LINE);
        System.out.println(peer.getBgpPeerType());
        System.out.println(peer.getLocalDevName()+","+peer.getPeerDevName());
    }

    public boolean isValidPeer(String n1, String n2) {
        return _peerTable.contains(n1, n2);
    }

    public BgpPeer getValidPeer(String n1, String n2) {
        return _peerTable.get(n1, n2);
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

 
}
