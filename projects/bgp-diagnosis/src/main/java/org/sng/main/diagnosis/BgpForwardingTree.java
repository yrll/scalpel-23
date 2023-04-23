package org.sng.main.diagnosis;

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

import jdk.security.jarsigner.JarSigner;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpRoute;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.BgpPeer.BgpPeerType;
import org.sng.main.common.Layer2Topology;
import org.sng.main.common.StaticRoute;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.conditions.SelectionRoute;
import org.sng.main.diagnosis.Generator.Protocol;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The forwarding tree is for specific single prefix (one dst multi src)
 */

public class BgpForwardingTree {
    private int _ebgpPref = Protocol.EBGP.getPreference();
    private int _ibgpPref = Protocol.IBGP.getPreference();
    private int _bgpLocalPref = Protocol.BGP_LOCAL.getPreference();

    private String _vpnName;
    private boolean _ifMpls;
    // Destination (origin) router and the prefix in it.
    private Prefix _dstPrefix;
    private String _dstDevName;
    private BgpTreeType bgpTreeType;

    // The next-hop map is for each router to forward traffic 
    // 只是针对【路由级的】每个节点【对应协议】计算出的下一跳的map
    // (It is allowed that each node reach its next-hop router using: 1) IGP, 2) directed connection, 3) Static)
    private Map<String, String> _nextHopForwardingMap;
    // The best route source map is for each router to receive and select its best route
    // 【路由级】路由传播和优选的from节点 
    private Map<String, String> _bestRouteFromMap;
    // 一开始路由不可达的节点集合
    private Set<String> _unreachableNodesPrev;
    // 一开始接收的可达路由的集合, 最优的, 最长前缀匹配的
    private Map<String, BgpRoute> _bestRouteMap;



    public Set<String> getRouteReachableNodesInTree() {
        if (_bestRouteFromMap!=null) {
            return _bestRouteFromMap.keySet();
        } else {
            return Sets.newHashSet(_dstDevName);
        }
        
    }
    
    public int getRouteTypePref(Protocol protocol) {
        switch (protocol) {
            case EBGP: return _ebgpPref;
            case IBGP: return _ibgpPref;
            default: return _bgpLocalPref;
        }
    }

    public enum TreeType {
        BGP,
        STATIC
    }

    public enum BgpTreeType{
        ERROR,
        CORRECT,
        NEW,
        NONE
    }
    
    public enum PathType{
        BEST_ROUTE_FROM,
        BEST_FORWARDING
    }

    public BgpRoute getBestBgpRoute(String node) {
        return _bestRouteMap.get(node);
    }

    public String chooseFirstNodeHasUnreachablePeer(BgpTopology bgpTopology) {
        Set<String> reachNodes = getReachableNodes();
        for (String node : reachNodes) {
            if (bgpTopology.getConfiguredPeers(node).stream().filter(n->_unreachableNodesPrev.contains(n)).count()>0) {
                return node;
            }
        }
        assert reachNodes.size() < 1;
        return reachNodes.iterator().next();
    }

    public BgpForwardingTree(String dstDev, Prefix prefix, String vpnName, BgpTreeType bgpTreeType) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopForwardingMap = new HashMap<>();
        _bestRouteFromMap = new HashMap<>();
        _vpnName = vpnName;
        _bestRouteMap = new HashMap<>();
        _ifMpls = !vpnName.equals(KeyWord.PUBLIC_VPN_NAME);
        this.bgpTreeType = bgpTreeType;
    }



    public String getDstDevName() {
        return _dstDevName;
    } 

    public void setUnreachableNodes(Set<String> nodes) {
        _unreachableNodesPrev = nodes;
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
        return _unreachableNodesPrev;
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
            String nextNode = _bestRouteFromMap.get(thisNode);

            if (!thisNode.equals(endDevName) && path.contains(nextNode)) {
                System.out.println(path);
                System.out.println("LOOP!!!!!!!!!!!");
                return null;
            }
            path.add(nextNode);

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

            String nextNode = _nextHopForwardingMap.get(thisNode);
            if (!thisNode.equals(endDevName) && path.contains(nextNode)) {
                System.out.println(path);
                System.out.println("LOOP!!!!!!!!!!!");
                return null;
            }
            path.add(nextNode);
        }
        // valid path
        if (path.get(path.size()-1).equals(endDevName)) {
            return path;
        } else {
            return null;
        }   
    }

    private void setProtocolPref() {
        List<Integer> prefList = ConfigTaint.getBgpIpv4Preference(_dstDevName, _vpnName);
        if (prefList!=null && prefList.size()==3) {
            _ebgpPref = prefList.get(0);
            _ibgpPref = prefList.get(1);
            _bgpLocalPref = prefList.get(2);
        }
    }




    public Set<String> serializeBgpTreeFromProvJson(JsonObject jsonObject, String ip, BgpTopology bgpTopology, Layer2Topology layer2Topology, boolean ifStrict) {
        Set<String> failedDevs = bgpTopology.getFailedDevs();
        // input "updateInfo" as jsonObject
        // 解析配置里对该(vpn)ip的preference
        setProtocolPref();
        Prefix tagetPrefix = Prefix.parse(ip);
        _unreachableNodesPrev = new HashSet<>(bgpTopology.getAllNodes().keySet());
        // 移除dst节点, 这是针对BGP的unreachable, 指通过BGP路由不可达的节点集合
        //【prov文件里也不会记录准换后的BGP route，只有准备redistribute出去的static route】
        _unreachableNodesPrev.remove(_dstDevName);

        for (String node : jsonObject.asMap().keySet()) {
            // faile节点
            if (failedDevs.contains(node)) {
                continue;
            }
            JsonObject nodeRoutes = jsonObject.asMap().get(node).getAsJsonObject();


            for (String ipString : nodeRoutes.keySet()) {
                Prefix curPrefix = Prefix.parse(ipString);
                if (ifStrict) {
                    // 严格匹配，前缀长度一致
                    if (!curPrefix.equals(tagetPrefix)) {
                        continue;
                    }
                } else {
                    // 非严格匹配，当前解析到的前缀包含目的前缀即可
                    if (!curPrefix.containsPrefix(tagetPrefix)) {
                        continue;
                    }
                }
                // 明细路由优于默认路由
                if (_bestRouteMap.containsKey(node)) {
                    if (curPrefix.equals(Prefix.ZERO)) {
                        break;
                    }
                }

                if (node.equals(_dstDevName) && curPrefix.equals(tagetPrefix)) {
                    if (bgpTreeType.equals(BgpTreeType.ERROR)) {
                        // 说明这个provnanceInfo不是目的设备的，把tree清空
                        _nextHopForwardingMap = new HashMap<>();
                        _bestRouteFromMap = new HashMap<>();
                        _bestRouteMap = new HashMap<>();
                        _unreachableNodesPrev = new HashSet<>(bgpTopology.getAllNodes().keySet());
                        _unreachableNodesPrev.remove(_dstDevName);
                        _bestRouteFromMap.put(_dstDevName,_dstDevName);
                        _nextHopForwardingMap.put(_dstDevName,_dstDevName);
                        return _unreachableNodesPrev;
                    }
                    // 检查是否只有bgp路由。如果有直连或静态（优先级高于bgp）也可以
//                    StaticRoute route = ConfigTaint.staticRouteFinder(node, curPrefix, true);
//                    if (route==null) {
//                        if (layer2Topology.getPrefixLocatedInterface(node, curPrefix.toString())==null) {
//
//                        }
//                    }

                }
                // 当前RIB表前缀匹配目标前缀
                int routeIndex = Integer.MAX_VALUE;
                for (JsonElement route_raw : nodeRoutes.get(ipString).getAsJsonArray()) {
                    // 保持index最小的route作为bestRoute【prov文件里并不是所有route的index都是从0开始】
                    int index = Integer.valueOf(route_raw.getAsJsonObject().get(KeyWord.INDEX).getAsString());
                    if (index >= routeIndex) {
                        // 以遇到的第一个index最高的为准
                        continue;
                    }
                    routeIndex = index;
                    JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();
                    BgpRoute bgpRoute = new Gson().fromJson(route, BgpRoute.class);

                    String nextHopDev = route.get(KeyWord.NEXT_HOP_DEV).getAsString();
                    String nextHopIpString = route.get(KeyWord.NEXT_HOP_IP).getAsString();
                    String peerIpString = route.get(KeyWord.PEER_IP).getAsString();
                    String peerDevName = bgpTopology.getNodeNameFromIp(peerIpString);

                    // fail节点不加入
                    if (failedDevs.contains(nextHopDev) || failedDevs.contains(peerDevName)) {
                        routeIndex = Integer.MAX_VALUE;
                        continue;
                    }

                    if (_bestRouteMap.containsKey(node)) {
                        Prefix bestRoutePrefix = _bestRouteMap.get(node).getPrefix();
                        // 新的route前缀更短则更新best*Map
                        if (bgpRoute.getPrefix().getPrefixLength() > bestRoutePrefix.getPrefixLength()) {
                            continue;
                        }
                    }
                    _nextHopForwardingMap.put(node, nextHopDev);
                    _bestRouteMap.put(node, bgpRoute);
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
                path.stream().forEach(n->_unreachableNodesPrev.remove(n));
            }
        }
        assert ifAllConnected();
        return _unreachableNodesPrev;
    }

    public Map<String, Set<String>> getAllInNeighbors(Map<String, String> forwardingMap, Set<String> considerNodes) {
        // 入参是BGPTree的bestRouteFromMap
        Map<String, Set<String>> maps = new HashMap<>();
        for (String node : forwardingMap.keySet()) {
            // the value node is the out neighbor of the key node
            if (node.equals(forwardingMap.get(node))) {
                // dst节点的下一跳是自己，所以不把自己加入prop/in 集合中
                continue;
            }
            if (!maps.containsKey(forwardingMap.get(node))) {
                Set<String> nodes = new HashSet<>();
                if (considerNodes.contains(node) && considerNodes.contains(forwardingMap.get(node))) {
                    nodes.add(node);
                }

                maps.put(forwardingMap.get(node), nodes);
            } else {
                if (considerNodes.contains(node) && considerNodes.contains(forwardingMap.get(node))) {
                    maps.get(forwardingMap.get(node)).add(node);
                }

            }
        }
        return maps;
    }

    public Map<String, Set<String>> getAllOutNeighbors(Map<String, String> forwardingMap, Set<String> considerNodes) {
        Map<String, Set<String>> maps = new HashMap<>();
        for (String node : forwardingMap.keySet()) {
            // the value node is the out neighbor of the key node
            if (node.equals(forwardingMap.get(node))) {
                // dst节点的下一跳是自己，所以不把自己加入acpt/out 集合中
                continue;
            }
            if (!maps.containsKey(node)) {
                Set<String> nodes = new HashSet<>();
                if (considerNodes.contains(node) && considerNodes.contains(forwardingMap.get(node))) {
                    nodes.add(forwardingMap.get(node));
                }

                maps.put(node, nodes);
            } else {
                if (considerNodes.contains(node) && considerNodes.contains(forwardingMap.get(node))) {
                    maps.get(node).add(forwardingMap.get(node));
                }

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

    public Map<String, Set<String>> getRRClients(Map<String, Set<String>> inNodes, Map<String, Set<String>> outNodes, BgpTopology bgpTopology) {
        // TODO 考虑和原有改动最小
        Map<String, Set<String>> clientsMap = new HashMap<>();
        for (String node : inNodes.keySet()) {
            Set<String> clients = new HashSet<>();
            // in-nodes是要传出去路由的节点
            Set<String> nodesIn = inNodes.get(node);
            // out-nodes是转发流量的下一跳，也是要优选的节点
            Set<String> nodesOut = outNodes.get(node);
            if (nodesOut==null) {
                // dstDev没有out nodes
                continue;
            }
            // 把已经配了的删除了
            List<String> nodesOutNotClient = bgpTopology.getNodesInAs(bgpTopology.getAsNumber(node), nodesIn).stream()
                                            .filter(nodeIn->!bgpTopology.ifConfiguredRRClient(node, nodeIn)).collect(Collectors.toList());
            List<String> nodesInNotClient = bgpTopology.getNodesInAs(bgpTopology.getAsNumber(node), nodesIn).stream()
                                            .filter(nodeIn->!bgpTopology.ifConfiguredRRClient(node, nodeIn)).collect(Collectors.toList());
            
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

    public List<String> getNextHopList(String node, BgpTopology bgpTopology) {
        List<String> path = getBestRouteFromPath(node, _dstDevName);
        List<String> ipList = new ArrayList<>();
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
                                                    

    public Map<String, BgpCondition> genBgpConditions(Set<String> reqSrcNodes, BgpTopology bgpTopology) {
        Set<String> nodesSetCondition = _bestRouteFromMap.keySet();
        if (_ifMpls) {
            nodesSetCondition = new HashSet<>();
            for (String node: reqSrcNodes) {
                List<String> path = getBestRouteFromPath(node, _dstDevName);
                nodesSetCondition.addAll(path);
            }
        }
        // TODO: 1. assign the nextHop attribute for each route based on the traffic forwarding tree
        Map<String, BgpCondition> conds = new HashMap<>();
        // prop是in-nodes
        Map<String, Set<String>> propNeighborMap = getAllInNeighbors(_bestRouteFromMap, nodesSetCondition);
        // accept是out-nodes
        Map<String, Set<String>> acptNeighborMap = getAllOutNeighbors(_bestRouteFromMap, nodesSetCondition);

        Map<String, Set<String>> clientsMap = getRRClients(propNeighborMap, acptNeighborMap, bgpTopology);


        for (String node : nodesSetCondition) {
            // dst节点单独设置
            if (node.equals(_dstDevName)) {
                continue;
            }
            String vpnName = _vpnName;
            if (!ConfigTaint.getVpnInstance(node, vpnName).isValid()) {
                vpnName = KeyWord.PUBLIC_VPN_NAME;
            }
            // prop-neighbor和acpt-neighbor还有peer的list可以去除不在nodesSetCondition里的节点
            conds.put(node, new BgpCondition.Builder(_dstPrefix.toString())
                                            // vpn名称只在有vpnName的节点上设置，其余设置public
                                            .vpnName(vpnName)
                                            .propNeighbors(propNeighborMap.get(node))
                                            .acptNeighbors(acptNeighborMap.get(node))
                                            .ibgpPeers(getIntersectantSet(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.IBGP), nodesSetCondition))
                                            .ebgpPeers(getIntersectantSet(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.EBGP), nodesSetCondition))
                                            .rrClient(clientsMap.get(node))
                                            .selectionRoute(new SelectionRoute.Builder(_dstPrefix.toString())
                                                                              .vpnName(vpnName)
                                                                              .nextHop(getNextHopList(node, bgpTopology))
                                                                              .asPath(getAsPath(node, bgpTopology))
                                                                              .build())
                                            .redistribution(false)
                                            .build());
        }
        // 添加终点（原发）节点的redistribute约束
        String node = _dstDevName;
        conds.put(node, new BgpCondition.Builder(_dstPrefix.toString())
                                            .vpnName(_vpnName)
                                            .propNeighbors(propNeighborMap.get(node))
                                            .acptNeighbors(acptNeighborMap.get(node))
                                            .ibgpPeers(getIntersectantSet(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.IBGP), nodesSetCondition))
                                            .ebgpPeers(getIntersectantSet(bgpTopology.getBgpPeers(node, propNeighborMap.get(node), BgpPeerType.EBGP), nodesSetCondition))
                                            .rrClient(clientsMap.get(node))
                                            .redistribution(true)
                                            .build());
        return conds;
        
    }

    /*
    * 求set1和set2的交集
    * */
    private Set<String> getIntersectantSet(Set<String> set1, Set<String> set2) {
        if (set1 != null) {
            set1.retainAll(set2);
        }

        return set1;
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

    public String getBestNextHop(String node) {
        if (_nextHopForwardingMap.containsKey(node)) {
            return _nextHopForwardingMap.get(node);
        }
        return "";
    }

    public String getBestRouteFromNode(String node) {
        if (_bestRouteFromMap.containsKey(node)) {
            return _bestRouteFromMap.get(node);
        }
        return "";
    }

    public <T> boolean ifSetContainsSameElement(Set<T> inputSet, T eleT) {
        for (T t : inputSet) {
            if (t.equals(eleT)) {
                return true;
            }
        }
        return false;
    }

    /*
     * 计算满足转发可达所需的IGP互通的点对集合：
     * 1）IBGP Peer（只考虑原来在unreachableNodes里的，不在的说明已经可达了）
     * 2）所有节点BGP路由下一跳
     */
    /**
     * @param bgpTopology
     * @return
     */
    public Map<String, Set<Node>> computeReachIgpNodes(BgpTopology bgpTopology) {
        //
        Map<String, Set<Node>> reachNodes = new HashMap<>();
        Map<String, Set<String>> inNeighborMap = getAllInNeighbors(_bestRouteFromMap, _bestRouteFromMap.keySet());
        // Ibgp peer 互达
        for (String node : inNeighborMap.keySet()) {
            // 每个node都要和它的所有in节点建立peer关系（双向可达）
            if (!reachNodes.containsKey(node)) {
                reachNodes.put(node, new HashSet<Node>());
            }
            inNeighborMap.get(node).forEach(peer->{
                
                // node到peer可达
                String peerIp;
                if (bgpTopology.getNodeIp(peer)!=null) {
                    peerIp = bgpTopology.getNodeIp(peer);
                } else {
                    peerIp = _bestRouteMap.get(peer).getPeerIpString();
                }
                Node reachPeerNode = new Node(peer, peerIp);
                if (!ifSetContainsSameElement(reachNodes.get(node), reachPeerNode)) {
                    reachNodes.get(node).add(reachPeerNode);
                }
                
                // peer到node也可达
                if (!reachNodes.containsKey(peer)) {
                    reachNodes.put(peer, new HashSet<Node>());
                }
                String thisIp;
                if (bgpTopology.getNodeIp(node)!=null) {
                    thisIp = bgpTopology.getNodeIp(node);
                } else {
                    thisIp = _bestRouteMap.get(node).getPeerIpString();
                }
                Node reachThisNode = new Node(node, thisIp);
                if (!ifSetContainsSameElement(reachNodes.get(peer), reachThisNode)) {
                    reachNodes.get(peer).add(reachThisNode);
                }
            });
        }
        // best-route下一跳可达
        if (_bestRouteMap!=null) {
            _bestRouteMap.forEach((node, route)->{
                if (!reachNodes.containsKey(node)) {
                    reachNodes.put(node, new HashSet<Node>());
                }
                // TODO 避开EBGP下一跳
                if (bgpTopology.getAsNumber(node)==bgpTopology.getAsNumber(route.getNextHopDev())) {
                    Node nextHopNode = new Node(route.getNextHopDev(), route.getNextHopIpString());
                    if (!ifSetContainsSameElement(reachNodes.get(node), nextHopNode)) {
                        reachNodes.get(node).add(nextHopNode);
                    }
                } 
            });
        }
        
        return reachNodes;
    }

}
