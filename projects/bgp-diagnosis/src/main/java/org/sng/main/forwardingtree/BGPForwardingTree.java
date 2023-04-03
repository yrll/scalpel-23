package org.sng.main.forwardingtree;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpRoute;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.BgpPeer.BgpPeerType;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.conditions.SelectionRoute;
import org.sng.main.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The forwarding tree is for specific single prefix (one dst multi src)
 */

public class BgpForwardingTree {
    // Destination (origin) router and the prefix in it.
    private Prefix _dstPrefix;
    private String _dstDevName;

    // The next-hop map is for each router to forward traffic 
    // 只是针对【路由级的】每个节点【对应协议】计算出的下一跳的map
    // (It is allowed that each node reach its next-hop router using: 1) IGP, 2) directed connection, 3) Static)
    private Map<String, String> _nextHopForwardingMap;
    // The best route source map is for each router to receive and select its best route
    // 【路由级】路由传播和优选的from节点 
    private Map<String, String> _bestRouteFromMap;

    private Set<String> _unreachableNodes;


    private boolean _ifMpls;

    public enum TreeType {
        BGP,
        STATIC
    }
    
    public enum PathType{
        BEST_ROUTE_FROM,
        BEST_FORWARDING
    }

    public String chooseFirstNodeHasUnreachablePeer(BgpTopology bgpTopology) {
        Set<String> reachNodes = getReachableNodes();
        for (String node : reachNodes) {
            if (bgpTopology.getConfiguredPeers(node).stream().filter(n->_unreachableNodes.contains(n)).count()>0) {
                return node;
            }
        }
        assert false;
        return "";
    }

    public BgpForwardingTree() {
        _dstDevName = KeyWord.UNKNOWN;
        _dstPrefix = Prefix.ZERO;
        _nextHopForwardingMap = new HashMap<>();
        _bestRouteFromMap = new HashMap<>();
    }
    public BgpForwardingTree(String dstDev, Prefix prefix) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopForwardingMap = new HashMap<>();
        _bestRouteFromMap = new HashMap<>();
        _ifMpls = false;
    }

    public String getDstDevName() {
        return _dstDevName;
    } 

    public void setUnreachableNodes(Set<String> nodes) {
        _unreachableNodes = nodes;
    }

    public void addNextHopForwardingEdge(String head, String tail) {
        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);
        if (_nextHopForwardingMap.containsKey(head)) {
            if (!_nextHopForwardingMap.get(head).equals(tail)) {
                logger.warning("INCONSISTENT FORWARDING BEHAVIOUR!!");
            }
        }
        _nextHopForwardingMap.put(head, tail);
    }

    public void addBestRouteFromEdge(String head, String tail) {
        Logger logger = Logger.getLogger(KeyWord.LOGGER_NAME);
        if (_bestRouteFromMap.containsKey(head)) {
            if (!_bestRouteFromMap.get(head).equals(tail)) {
                logger.warning("INCONSISTENT FORWARDING BEHAVIOUR!!");
            }
        }
        _bestRouteFromMap.put(head, tail);
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

    public Set<String> getReachableNodes() {
        // TODO: 选哪个map作为参考？
        return _nextHopForwardingMap.keySet();
    }

    public Set<String> getUnreachableNodes() {
        return _unreachableNodes;
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

    public List<String> getBestRouteFromPath(String startDevName, String endDevName) {
        // start&end node are both in the returned path
        List<String> path = new ArrayList<>();
        path.add(startDevName);
        while(true) {
            String thisNode = path.get(path.size()-1);
            if (thisNode.equals(endDevName) || !_bestRouteFromMap.containsKey(thisNode) || thisNode.equals(_dstDevName)) {
                break;
            }
            path.add(_bestRouteFromMap.get(thisNode));
        }
        // must be a valid path
        if (path.get(path.size()-1).equals(endDevName)) {
            return path;
        } else {
            return null;
        } 
    }

    public List<String> getForwardingPath(String startDevName, String endDevName) {
        // start&end node are both in the returned path
        List<String> path = new ArrayList<>();
        path.add(startDevName);
        while(true) {
            String thisNode = path.get(path.size()-1);
            if (thisNode.equals(endDevName) || !_nextHopForwardingMap.containsKey(thisNode)) {
                break;
            }
            path.add(_nextHopForwardingMap.get(thisNode));
        }
        // valid path
        if (path.get(path.size()-1).equals(endDevName)) {
            return path;
        } else {
            return null;
        }   
    }

    public Set<String> serializeBgpTreeFromProvJson(JsonObject jsonObject, String ip, BgpTopology bgpTopology) {
        // input "updateInfo" as jsonObject
        _unreachableNodes = new HashSet<>(bgpTopology.getAllNodes().keySet());
        // 移除dst节点, 这是针对BGP的unreachable, 指通过BGP路由不可达的节点集合
        //【prov文件里也不会记录准换后的BGP route，只有准备redistribute出去的static route】
        _unreachableNodes.remove(_dstDevName);

        for (String node : jsonObject.asMap().keySet()) {
            JsonObject nodeRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            if (nodeRoutes.get(ip)!=null) {
                int routeIndex = Integer.MAX_VALUE;
                for (JsonElement route_raw : nodeRoutes.get(ip).getAsJsonArray()) {
                    // 保持index最小的route作为bestRoute【prov文件里并不是所有route的index都是从0开始】
                    int index = Integer.valueOf(route_raw.getAsJsonObject().get(KeyWord.INDEX).getAsString());
                    if (index > routeIndex) {
                        continue;
                    }
                    routeIndex = index;
                    JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();

                    String nextHopDev = route.get(KeyWord.NEXT_HOP_DEV).getAsString();
                    String nextHopIp = route.get(KeyWord.NEXT_HOP_IP).getAsString();
                    String peerIp = route.get(KeyWord.PEER_IP).getAsString();
                    String peerDevName = bgpTopology.getNodeNameFromIp(Prefix.parse(peerIp).getEndIp());

                    assert !_nextHopForwardingMap.containsKey(node);
                    _nextHopForwardingMap.put(node, nextHopDev);
                    assert !_bestRouteFromMap.containsKey(node);
                    _bestRouteFromMap.put(node, peerDevName);

                    System.out.println("Best route from: " + node + "-->" + peerDevName);
                    System.out.println("Next-hop from: " + node + "-->" + nextHopDev);
                    
                }
            } 
        }
        // 把dst节点也放进来，下一跳是自己
        _bestRouteFromMap.put(_dstDevName,_dstDevName);
        _nextHopForwardingMap.put(_dstDevName,_dstDevName);
        // 检测不可达节点（即使有下一跳但是没有连通的path也算不可达）
        for (String node : bgpTopology.getAllNodes().keySet()) {
            // TODO 这里应该用哪个map？
            List<String> path = getForwardingPath(node, _dstDevName);
            if (path!=null) {
                path.stream().forEach(n->_unreachableNodes.remove(n));
            }
        }
        assert ifAllConnected();
        return _unreachableNodes;
    }

    public Map<String, List<String>> getAllInNeighbors(Map<String, String> forwardingMap) {
        // 入参是BGPTree的bestRouteFromMap
        Map<String, List<String>> maps = new HashMap<>();
        for (String node : forwardingMap.keySet()) {
            // the value node is the out neighbor of the key node
            if (node.equals(forwardingMap.get(node))) {
                // dst节点的下一跳是自己，所以不把自己加入prop/in 集合中
                continue;
            }
            if (!maps.containsKey(forwardingMap.get(node))) {
                List<String> nodes = new ArrayList<>();
                nodes.add(node);
                maps.put(forwardingMap.get(node), nodes);
            } else {
                maps.get(forwardingMap.get(node)).add(node);
            }
        }
        return maps;
    }

    public Map<String, List<String>> getAllOutNeighbors(Map<String, String> forwardingMap) {
        Map<String, List<String>> maps = new HashMap<>();
        for (String node : forwardingMap.keySet()) {
            // the value node is the out neighbor of the key node
            if (node.equals(forwardingMap.get(node))) {
                // dst节点的下一跳是自己，所以不把自己加入acpt/out 集合中
                continue;
            }
            if (!maps.containsKey(node)) {
                List<String> nodes = new ArrayList<>();
                nodes.add(forwardingMap.get(node));
                maps.put(node, nodes);
            } else {
                maps.get(node).add(forwardingMap.get(node));
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
        // TODO 考虑和原有改动最小
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
            
            List<String> nodesOutNotClient = bgpTopology.getNodesInAs(bgpTopology.getAsNumber(node), nodesIn).stream().filter(nodeIn->!bgpTopology.ifConfiguredRRClient(node, nodeIn)).collect(Collectors.toList());
            List<String> nodesInNotClient = bgpTopology.getNodesInAs(bgpTopology.getAsNumber(node), nodesIn).stream().filter(nodeIn->!bgpTopology.ifConfiguredRRClient(node, nodeIn)).collect(Collectors.toList()); 
            
            if (nodesInNotClient.size()==0 || nodesOutNotClient.size()==0) {
                continue;
            } else if (nodesInNotClient.size() <= nodesOutNotClient.size()) {
                nodesInNotClient.forEach(n->clients.add(n));
            } else {
                nodesOutNotClient.forEach(n->clients.add(n));
            }

            if (clients.size() > 0) {
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
        Map<String, List<String>> propNeighborMap = getAllInNeighbors(_bestRouteFromMap);
        // accept是out-nodes
        Map<String, List<String>> acptNeighborMap = getAllOutNeighbors(_bestRouteFromMap);

        Map<String, List<String>> clientsMap = getRRClients(propNeighborMap, acptNeighborMap, bgpTopology);

        for (String node : _bestRouteFromMap.keySet()) {
            // dst节点单独设置
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
        // 添加终点（原发）节点的redistribute约束
        String node = _dstDevName;
        conds.put(node, new BgpCondition.Builder(_dstPrefix)
                                            .propNeighbors(propNeighborMap.get(node))
                                            .acptNeighbors(acptNeighborMap.get(node))
                                            .ibgpPeers(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.IBGP))
                                            .ebgpPeers(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.EBGP))
                                            .rrClient(clientsMap.get(node))
                                            .redistribution(true)
                                            .build());
        return conds;
        
    }

    public void serializeBgpCondition(String filePath, Map<String, BgpCondition> conditions) {
        Gson gson = new GsonBuilder().serializeNulls().create();
        String jsonString = gson.toJson(conditions);
        // System.out.println(jsonString);
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

    public boolean ifAllConnected() {
        // 检测这个树是否不连通
        // 基本思路：【forwardingMap】的keySet里的节点都有valid路径
        // TODO: 该检查哪个map？
        Set<String> nodesInTree = new HashSet<>(_nextHopForwardingMap.keySet());
        for (String node : _nextHopForwardingMap.keySet()) {
            List<String> path = getForwardingPath(node, _dstDevName);
            if (path==null) {
                continue;
            }
            if (nodesInTree.size()==0) {
                break;
            }
            nodesInTree.removeAll(path);            
        }
        return nodesInTree.size()==0;
    }


}
