package org.sng.main.forwardingtree;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.BgpPeer.BgpPeerType;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.conditions.SelectionRoute;
import org.sng.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The forwarding tree is for specific single prefix (one dst multi src)
 */

public class ForwardingTree {
    // Destination (origin) router and the prefix in it.
    private Prefix _dstPrefix;
    private String _dstDevName;

    // The next-hop map is for each router to forward traffic 
    // 只是针对【路由级的】每个节点【对应协议】计算出的下一跳的map
    // (It is allowed that each node reach its next-hop router using: 1) IGP, 2) directed connection, 3) Static)
    private Map<String, String> _nextHopForwardingMap;
    // The best route source map is for each router to receive and select its best route
    // 【路由级】路由传播和优选的 
    private Map<String, String> _bestRouteFromMap;

    private enum Direction{
        IN,
        OUT
    }

    public enum TreeType {
        BGP,
        STATIC
    }

    public ForwardingTree() {
        _dstDevName = KeyWord.UNKNOWN;
        _dstPrefix = Prefix.ZERO;
        _nextHopForwardingMap = new HashMap<>();
        _bestRouteFromMap = new HashMap<>();
    }
    public ForwardingTree(String dstDev, Prefix prefix) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopForwardingMap = new HashMap<>();
        _bestRouteFromMap = new HashMap<>();
    }

    public String getDstDevName() {
        return _dstDevName;
    } 

    // RECONSTRUCTION: genNewTree
    public void copyBestRouteFromMap(Map<String, String> map) {
        assert _bestRouteFromMap.size()<1;
        for (String node : map.keySet()) {
            _bestRouteFromMap.put(node, map.get(node));
        }
    }

    public Map<String, String> getBestRouteFromMap() {
        return _bestRouteFromMap;
    }

    // RECONSTRUCTION: genNewTree
    public boolean addBestRouteFromPath(List<String> path) {
        for (int i=0; i<path.size()-1; i+=1) {
            if (!_bestRouteFromMap.containsKey(path.get(i))) {
                _bestRouteFromMap.put(path.get(i), path.get(i+1));
            } else {
                if (!_bestRouteFromMap.get(path.get(i)).equals(path.get(i+1))) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<String> getNextHopForwardingPath(String startDevName, String endDevName) {
        List<String> path = new ArrayList<>();
        path.add(startDevName);
        while(true) {
            String thisNode = path.get(path.size()-1);
            if (thisNode.equals(endDevName)) {
                break;
            }
            if (_nextHopForwardingMap.containsKey(thisNode)) {
                path.add(_nextHopForwardingMap.get(thisNode));
            } else {
                break;
            }
        }
        return path;
    }

    public List<String> getBestRouteFromPath(String startDevName, String endDevName) {
        // start&end node are both in the returned path
        List<String> path = new ArrayList<>();
        path.add(startDevName);
        while(true) {
            String thisNode = path.get(path.size()-1);
            if (thisNode.equals(endDevName)) {
                // path.add(endDevName);
                break;
            }
            if (_bestRouteFromMap.containsKey(thisNode)) {
                path.add(_bestRouteFromMap.get(thisNode));
            } else {
                break;
            }
        }
        return path;
    }

    public List<String> getForwardingPathByDevName(String startDevName) {
        List<String> path = new ArrayList<>();
        path.add(startDevName);
        while(true) {
            String thisNode = path.get(path.size()-1);
            if (thisNode.equals(_dstDevName)) {
                path.add(_dstDevName);
                break;
            }
            if (_nextHopForwardingMap.containsKey(thisNode)) {
                path.add(_nextHopForwardingMap.get(thisNode));
            } else {
                break;
            }
        }
        return path;
    }

    public ForwardingTree serializeBgpTreeFromProvJson(JsonObject jsonObject, String ip) {
        // input "updateInfo" as jsonObject
        // Map<String, Node> nextHopMap = new HashMap<>();
        for (String node : jsonObject.asMap().keySet()) {
            JsonObject nodeRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            if (nodeRoutes.get(ip)!=null) {
                for (JsonElement route_raw : nodeRoutes.get(ip).getAsJsonArray()) {
                    String index = route_raw.getAsJsonObject().get(KeyWord.INDEX).getAsString();
                    // 
                    if (!index.equals("0")) {
                        continue;
                    }
                    JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();

                    String nextHopDev = route.get(KeyWord.NEXT_HOP_DEV).getAsString();
                    String nextHopIp = route.get(KeyWord.NEXT_HOP_IP).getAsString();
                    assert !_nextHopForwardingMap.containsKey(node);
                    _nextHopForwardingMap.put(node, nextHopDev);
                }
            } 
        }
        
        return this;
    }

    public Set<String> serializeBgpTreeAndPropTreeFromProvJson(JsonObject jsonObject, String ip, Map<String, Ip> allNodes) {
        // input "updateInfo" as jsonObject
        Set<String> unreachableNodes = allNodes.keySet();
        for (String node : jsonObject.asMap().keySet()) {
            JsonObject nodeRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            if (nodeRoutes.get(ip)!=null) {
                for (JsonElement route_raw : nodeRoutes.get(ip).getAsJsonArray()) {
                    String index = route_raw.getAsJsonObject().get(KeyWord.INDEX).getAsString();
                    String a = String.valueOf(0);
                    if (!index.equals("0")) {
                        continue;
                    }
                    JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();

                    String nextHopDev = route.get(KeyWord.NEXT_HOP_DEV).getAsString();
                    String nextHopIp = route.get(KeyWord.NEXT_HOP_IP).getAsString();
                    String peerIp = route.get(KeyWord.PEER_IP).getAsString();

                    assert !_nextHopForwardingMap.containsKey(node);
                    _nextHopForwardingMap.put(node, nextHopDev);

                    assert !_bestRouteFromMap.containsKey(node);
                    String peerDevName = getNodeNameFromIp(Prefix.parse(peerIp).getEndIp().toString(), allNodes);
                    _bestRouteFromMap.put(node, peerDevName);
                    System.out.println(node + "-->" + peerDevName);
                }
            } 
        }
        for (String node : unreachableNodes) {
            List<String> path = getForwardingPathByDevName(node);
            if (path.get(path.size()-1).equals(unreachableNodes)) {
                unreachableNodes.remove(node);
            }
        }
        return unreachableNodes;
    }

    private String getNodeNameFromIp(String ip, Map<String, Ip> allNodes) {
        for (String node : allNodes.keySet()) {
            if (allNodes.get(node).toString().equals(ip)) {
                return node;
            }
        }
        return "";
    }


    public ForwardingTree serializeStaticTreeFromProvJson(JsonObject jsonObject, String ip) {
        // input "updateInfo" as jsonObject
        // Map<String, Node> nextHopMap = new HashMap<>();
        for (String node : jsonObject.asMap().keySet()) {
            JsonObject allRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            for (String vpnName : allRoutes.asMap().keySet()) {
                JsonObject typedRoutes = allRoutes.asMap().get(vpnName).getAsJsonObject();
                if (typedRoutes.get(ip)!=null) {
                    for (JsonElement route_raw : typedRoutes.get(ip).getAsJsonArray()) {
                        
                        // JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();
                        String nextHopIface = route_raw.getAsJsonObject().get(KeyWord.OUT_INFO).getAsJsonObject().get(KeyWord.IFACE_NAME).getAsString();
                        String nextHopIp = route_raw.getAsJsonObject().get(KeyWord.OUT_INFO).getAsJsonObject().get(KeyWord.IFACE_IP).getAsString();
                        
                        assert !_nextHopForwardingMap.containsKey(node);
                        // need rewriting, parse iface and iface_ip
                        _nextHopForwardingMap.put(node, nextHopIface);
                    }
                } 
            }
            
        }
        return this;
    }

    public Map<String, List<String>> getAllInNeighbors() {
        Map<String, List<String>> maps = new HashMap<>();
        for (String node : _bestRouteFromMap.keySet()) {
            // the value node is the out neighbor of the key node
            if (!maps.containsKey(_bestRouteFromMap.get(node))) {
                List<String> nodes = new ArrayList<>();
                nodes.add(node);
                maps.put(_bestRouteFromMap.get(node), nodes);
            } else {
                maps.get(_bestRouteFromMap.get(node)).add(node);
            }
        }
        return maps;
    }

    public Map<String, List<String>> getAllOutNeighbors() {
        Map<String, List<String>> maps = new HashMap<>();
        for (String node : _bestRouteFromMap.keySet()) {
            // the value node is the out neighbor of the key node
            if (!maps.containsKey(node)) {
                List<String> nodes = new ArrayList<>();
                nodes.add(_bestRouteFromMap.get(node));
                maps.put(node, nodes);
            } else {
                maps.get(node).add(_bestRouteFromMap.get(node));
            }
        }
        return maps;
    }

    public List<Long> getAsPath(String node, BgpTopology bgpTopology) {
        long thisAs = bgpTopology.getAsNumber(node);
        Set<Long> asPath = new LinkedHashSet<>();
        for (String other : getBestRouteFromPath(node, _dstDevName)) {
            long otherAs = bgpTopology.getAsNumber(other);
            if (otherAs==thisAs) {
                continue;
            }
            asPath.add(otherAs);
        }
        List<Long> asPathList = new ArrayList<>(asPath);
        Collections.reverse(asPathList);
        return asPathList;
    }

    public Map<String, List<String>> getRRClients(Map<String, List<String>> inNodes, Map<String, List<String>> outNodes, BgpTopology bgpTopology) {
        Map<String, List<String>> clientsMap = new HashMap<>();
        for (String node : inNodes.keySet()) {
            List<String> clients = new ArrayList<>();
            // in-nodes是要传出去路由的节点
            List<String> nodesIn = inNodes.get(node);
            // out-nodes是转发流量的下一跳，也是要优选的节点
            List<String> nodesOut = outNodes.get(node);
            if (nodesOut==null) {
                // dstDev没有out nodes
                continue;
            }
            for (String outNode : nodesOut) {
                // 如果outNode为iBGP peer，且in-nodes中有至少一个iBPG peer
                if (bgpTopology.getAsNumber(node)==bgpTopology.getAsNumber(outNode)) {
                    if (bgpTopology.hasNodeInAs(bgpTopology.getAsNumber(node), nodesIn)) {
                        clients.add(outNode);
                    }
                }
            }
            if (clients.size()>0) {
                clientsMap.put(node, clients);
            }
            
        }
        return clientsMap;
    }

    public List<Ip> getNextHopList(String node, BgpTopology bgpTopology) {
        List<String> path = getBestRouteFromPath(node, _dstDevName);
        List<Ip> ipList = new ArrayList<>();
        for (String nextNode : path) {
            if (nextNode.equals(node)) {
                continue;
            }
            if (bgpTopology.getAsNumber(node)==bgpTopology.getAsNumber(nextNode)) {
                ipList.add(bgpTopology.getNodeIp(nextNode));
            }
        }
        if (ipList.size()>0) {
            return ipList;
        } else {
            return null;
        }

    }
                                                    

    public Map<String, BgpCondition> genBgpConditions(BgpTopology bgpTopology) {
        // TODO: 1. assign the nextHop attribute for each route based on the traffic forwarding tree
        Map<String, BgpCondition> conds = new HashMap<>();
        // prop是in-nodes
        Map<String, List<String>> propNeighborMap = getAllInNeighbors();
        // accept是out-nodes
        Map<String, List<String>> acptNeighborMap = getAllOutNeighbors();

        Map<String, List<String>> clientsMap = getRRClients(propNeighborMap, acptNeighborMap, bgpTopology);

        for (String node : _bestRouteFromMap.keySet()) {
            if (node.equals(_dstDevName)) {
                continue;
            }
            conds.put(node, new BgpCondition.Builder(_dstPrefix)
                                            .propNeighbors(propNeighborMap.get(node))
                                            .acptNeighbors(acptNeighborMap.get(node))
                                            .ibgpPeers(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.IBGP))
                                            .ebgpPeers(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.EBGP))
                                            .rrClient(clientsMap.get(node))
                                            .selectionRoute(new SelectionRoute.Builder(_dstPrefix)
                                                                              .nextHop(getNextHopList(node, bgpTopology))
                                                                              .asPath(getAsPath(node, bgpTopology))
                                                                              .build())
                                            .redistribution(false)
                                            .build());
        }
        // 添加终点（原发）节点的约束
        conds.put(_dstDevName, new BgpCondition.Builder(_dstPrefix)
                                               .redistribution(true)
                                               .propNeighbors(propNeighborMap.get(_dstDevName))
                                               .build());
        return conds;
        
    }

    public void serializeBgpCondition(String filePath, Map<String, BgpCondition> conditions) {
        Gson gson = new GsonBuilder().serializeNulls().create();
        String jsonString = gson.toJson(conditions);
        System.out.println(jsonString);
        try{
            File file = new File(filePath);
    
            if(!file.getParentFile().exists()){
                //若父目录不存在则创建父目录
                file.getParentFile().mkdirs();
            }
    
            if(file.exists()){
                //若文件存在，则删除旧文件
                file.delete();
            }
    
            file.createNewFile();
    
            //将格式化后的字符串写入文件
            // FileWriter writer = new FileWriter(filePath);   
            // writer.write(jsonString);
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(jsonString);
            writer.flush();
            writer.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }


}
