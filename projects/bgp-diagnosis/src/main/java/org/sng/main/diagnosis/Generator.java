package org.sng.main.diagnosis;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.BgpDiagnosis;
import org.sng.main.InputData;
import org.sng.main.InputData.NetworkType;
import org.sng.main.common.BgpPeer;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.Interface;
import org.sng.main.common.Layer2Topology;
import org.sng.main.common.StaticRoute;
import org.sng.main.diagnosis.BgpForwardingTree.TreeType;
import org.sng.main.localization.RouteForbiddenLocalizer;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;
/*
 * maintain the all overlay info (BGP and Static) of the network
 */
public class Generator {

    private static String UPT_TABLE = "updateTable";
    private static String CONVERGE_TABLE = "convergeInfo";
    private static String STATIC_INFO = "staticRouteInfo";

    private String _dstDevName;
    private Prefix _dstPrefix;
    private String _vpnName;
    private boolean _ifMpls;
    private boolean strict;

    // 
    private BgpForwardingTree _oldBgpTree;
    private StaticForwardingTree _oldStaticTree;
    // private BgpForwardingTree _newBgpTree;

    // 对于错误bgpTree的generator，这里的bgp topo就是错误的那个（但对new generator，初始化的时候还是用的错误的bgp topo）
    private BgpTopology _bgpTopology;

    private Layer2Topology _layer2Topology;
    //
    private Set<String> _failedDevs;


    public enum Protocol {
        IBGP("ibgp", 255),
        EBGP("ebgp", 255),
        MBGP("mbgp", 255),
        STATIC("static", 60),
        DIRECT("direct", 0),
        BGP_LOCAL("local", 255);

      
        private final String _name;
      
        private final int _preference;
      
        Protocol(String originType, int preference) {
          _name = originType;
          _preference = preference;
        }

        public String getProtocol() {
          return _name;
        }
      
        public int getPreference() {
          return _preference;
        }
      }

    public Generator(String nodeName, String prefix, BgpTopology bgpTopology, String vpnName, boolean ifMpls, Set<String> failedDevs, boolean strict) {
        _dstDevName = nodeName;
        _dstPrefix = Prefix.parse(prefix);
        _bgpTopology = bgpTopology;
        _vpnName = vpnName;
        _ifMpls = ifMpls;
        _failedDevs = failedDevs;
        this.strict = strict;
    }

    public Layer2Topology getLayer2Topology() {
        return _layer2Topology;
    }

    public boolean ifMpls() {
        return _ifMpls;
    }

    public BgpForwardingTree getOldBgpTree() {
        return _oldBgpTree;
    }

    // public BgpForwardingTree getNewBgpTree() {
    //     return _newBgpTree;
    // }

    public BgpTopology getBgpTopology() {
        return _bgpTopology;
    }

    public StaticForwardingTree getStaticTree() {
        return _oldStaticTree;
    }

    public String getDstDevName() {
        return _dstDevName;
    }

    public void setLayer2Topology(Layer2Topology layer2Topology) {
        _layer2Topology = layer2Topology;
    }

    public BgpForwardingTree getBgpTree() {
        return _oldBgpTree;
    }

    public int getNextHopRecursively(List<StaticRoute> routes, Ip ip) {
        for (StaticRoute staticRoute : routes) {
            if (staticRoute.getPrefix().containsIp(ip)) {
                if (staticRoute.getInterface()!=null) {
                    return 2;
                } else if (staticRoute.getNextHop()!=null) {
                    return 1 + getNextHopRecursively(routes, staticRoute.getNextHop());
                } else {
                    return 255;
                }
            }
        }
        return 255;
    }


    public int hopNumberToReachIpUsingStatic(String node, String ip) {
        // TODO: implement
        // 如果是静态路由，
        String filePath = InputData.getCorrectProvFilePath(BgpDiagnosis.caseType, BgpDiagnosis.networkType, KeyWord.RELATED_STATIC_INFO_FILE);
        Map<String, Map<String, Map<String, List<StaticRoute>>>> relatedStaticRoutes = genStaticOrDirectRouteFromFile(filePath, Protocol.STATIC);
        Optional<Prefix> prefix = Prefix.tryParse(BgpTopology.transPrefixOrIpToPrefixString(ip));
        int hopNum = 0;

        // TODO： 需要全部的static信息
        if (prefix.isPresent()) {
            if (ConfigTaint.staticRouteFinder(node, prefix.get(), false)!=null) {
                return 2;
            }
            if (relatedStaticRoutes.containsKey(node)) {
                if (relatedStaticRoutes.get(node).containsKey(KeyWord.PUBLIC_VPN_NAME)) {
                    List<StaticRoute> routes = new ArrayList<>();
                    relatedStaticRoutes.get(node).get(KeyWord.PUBLIC_VPN_NAME).keySet().forEach(ipName->{
                        routes.addAll(relatedStaticRoutes.get(node).get(KeyWord.PUBLIC_VPN_NAME).get(ipName));
                    });
                    return getNextHopRecursively(routes, prefix.get().getEndIp());
                }
            }
            return 255;
        }
        // IPv6的EBGP neighbor??
        return 255;

    }

    public void genBgpRoutePropTree(String filePath) {
        // graph is the bgp topology

    }

    public void serializeTreeFromJson(String filePath, TreeType type, BgpForwardingTree.BgpTreeType bgpTreeType) {
        File file = new File(filePath);
        System.out.println(filePath);
        String jsonStr;
        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");
            switch(type) {
                case BGP: {
                    // get BGP RIB
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(UPT_TABLE).getAsJsonObject();
                    _oldBgpTree = new BgpForwardingTree(_dstDevName, _dstPrefix, _vpnName, bgpTreeType);
                    _oldBgpTree.serializeBgpTreeFromProvJson(jsonObject, _dstPrefix.toString(), _bgpTopology, _layer2Topology, strict);
                    break;
                }
                case STATIC: {
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(STATIC_INFO).getAsJsonObject();
                    _oldStaticTree = new StaticForwardingTree(_dstDevName, _dstPrefix, _vpnName);
                    _oldStaticTree.serializeStaticTreeFromProvJson(jsonObject, _dstPrefix.toString(), _layer2Topology);
                    break;
                } 
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("pause");
        
    }


    public void setBGPForwardingTree(BgpForwardingTree tree) {
        _oldBgpTree = tree;
    }

    public void setStaticForwardingTree(StaticForwardingTree tree) {
        _oldStaticTree = tree;
    }

    private <T> boolean ifTwoPathOverlap (List<T> p1, List<T> p2) {
        for (T node : p1) {
            if (p2.contains(node)) {
                return true;
            }
        }
        return false;
    }

    private List<List<String>> getPath(String src, String dst, Table<String, String, BgpPeer> nodeMap) {
        return null;
    } 

    private void printStringList(List<String> list, String title, String seperator) {
        System.out.println(KeyWord.PRINT_LINE_HALF + title + KeyWord.PRINT_LINE_HALF);
        for (String string : list) {
            System.out.print(string + seperator);
        }
        System.out.println();
    }


    private List<String> copyPath(List<String> path, int fromIndex, int toIndex) {
        List<String> newPath = new ArrayList<>();
        assert toIndex>fromIndex && path.size()>=toIndex && fromIndex>=0;
        for (int i=fromIndex; i<toIndex; i+=1) {
            newPath.add(path.get(i));
        }
        return newPath;
    }

    // 输入是spec中要求可达的节点，如果某个节点X在AS i中，则需要将AS i中其他iBGP节点也加入需要可达的nodes集合
    // 如果使用隧道，则不需要AS内 full mesh
    // 返回的节点集合就是要在MST加入的节点
    private Set<String> processReachNodes(Set<String> nodes) {
        nodes.remove(_dstDevName);
        if (!_ifMpls) {
            Set<String> reachNodes = new HashSet<>(nodes);
            nodes.forEach(n->{
                reachNodes.addAll(_bgpTopology.getAllNodesInSameAs(n));
            });
            // 总是移除dst节点
            reachNodes.remove(_dstDevName);
            return reachNodes;
        } else {
            return nodes;
        }
    }



    /*
     * 这一步只生成BGP Tree里的路由传播和优选的树（实际转发时，域内路径可能会有出入）
     * 这里的出入源自于域内传播的BGP路由下一跳不一定永远是接收eBGP路由的入节点
     * 【域内(AS内)可能会通过不止一个IGP域相连，所以反射时下一跳可能会多次改变】
     * 1）跨IGP域的节点可能改下一跳再传 :o
     * 2）跨IGP域的节点终止目标路由（明细路由）传播，发送默认路由至上游节点 :(
     * ---------------------------------------------------------------------------
     * 现有的错误的tree必须是连通的
     * TODO: 现有的【根据prov信息生成的】tree不连通？会有这种情况出现么？不会吧？有的话是什么情况呢
     * TODO: 这里生成的BGP树是针对可达性要求的（最小生成树算法【如果有reference参考，可以考虑改进选下一个“最小边”的指标】）
     * 如果有waypoint/bypass要求，需要先改动原有的错误的树（还没实现），但这里要分情况考虑：
     * 1）如果是要经过/绕开AS内部(intra)某些节点：a) 非边界节点：需要IGP支持，b）边界节点：BGP策略
     * 2）如果要经过/绕开某些AS(inter)：则此时路由传播和优选的路径就和转发的路径一致（AS-level）
     * ---------------------------------------------------------------------------
     * PS: 现在暂时都不考虑ACL这种数据面的策略
    */ 

    public BgpForwardingTree genBgpTree(Set<String> reqReachNodes, BgpForwardingTree refTree) {
        // error oldTree 所在的generator调用，oldBGPTree已经在generator里
        // 目标src节点是当前generator里的unreachable nodes
        // 在现有error oldBGPTree上继续生成minTree【针对路由传播的tree: BestRouteFrom】
        if (!_ifMpls) {
            reqReachNodes = processReachNodes(reqReachNodes);
        }

        // 判断是不是需要bgpTopology的参考来构造转发树
        boolean ifNeedBgpTopo = false;
        BgpTopology bgpTopology = _bgpTopology;
        for (String reqNode: reqReachNodes) {
            if (!_bgpTopology.ifConnected(reqNode, _dstDevName)) {
                ifNeedBgpTopo = true;
                bgpTopology = InputData.getRefBgpTopology(BgpDiagnosis.networkType, _failedDevs);
            }
        }


        Set<String> reachableNodes = new HashSet<>(_oldBgpTree.getReachableNodes());
        // 考虑fail节点
        reachableNodes.removeAll(_failedDevs);

        reqReachNodes.removeAll(reachableNodes);
        Set<String> unreachableNodes = new HashSet<>(bgpTopology.getAllDevs());
        unreachableNodes.removeAll(reachableNodes);
        // 考虑fail节点
        unreachableNodes.removeAll(_failedDevs);

        Map<String, Integer> distanceMap = new HashMap<>();
        Map<String, String> primNearestNodeMap = new HashMap<>(_oldBgpTree.getBestRouteFromMap());
        // disMap initialization
        bgpTopology.getAllDevs().forEach(node->distanceMap.put(node, Integer.MAX_VALUE));
        // TODO: 如果没有serialize到BGPTree时，没有节点和bgp ip的映射，这里会出现bestRouteFrom和nextHopForwarding不一致问题：nextHop有devName，但是bestRouteFrom没有devName
        reachableNodes.forEach(node->distanceMap.put(node, _oldBgpTree.getBestRouteFromPath(node, _dstDevName).size()-1));
        // dstNode init
        distanceMap.put(_dstDevName, 0);

        // 用ref的连接信息参考作为Prim的加入节点选择（MST不止一个时）
        String curNode = _oldBgpTree.chooseFirstNodeHasUnreachablePeer(bgpTopology); // 选一个已reach的开始

        // 把已有的reach节点的peer遍历更新一遍disMap【重要，不然会出现有点节点拓展失败的情况】
        for (String reachedNode: reachableNodes) {
            for (String peer: bgpTopology.getConfiguredPeers(reachedNode)) {
                if (distanceMap.get(reachedNode) + 1 < distanceMap.get(peer)) {
                    distanceMap.put(peer, distanceMap.get(reachedNode) + 1);
                    primNearestNodeMap.put(peer, reachedNode);
                } else if (distanceMap.get(reachedNode) + 1 == distanceMap.get(peer)) {
                    if (refTree!=null) {
                        // 参考正确流的信息
                        if (refTree.getBestRouteFromNode(peer).equals(reachedNode)) {
                            primNearestNodeMap.put(peer, reachedNode);
                        }
                    }
                }
            }
        }
        // 开始在当前MST上加节点
        while (reqReachNodes.size()>0) {
            for (String peer: bgpTopology.getConfiguredPeers(curNode)) {
//                if (reachableNodes.contains(peer)) {
//                    // 更新disMap时只考虑spec要求的节点集合
//                    continue;
//                }
                System.out.println("*****************");
                System.out.println(curNode);
                System.out.println(peer);
                if (distanceMap.get(curNode) + 1 < distanceMap.get(peer)) {
                    distanceMap.put(peer, distanceMap.get(curNode) + 1);
                    primNearestNodeMap.put(peer, curNode);
                } else if (distanceMap.get(curNode) + 1 == distanceMap.get(peer)) {
                    if (refTree != null) {
                        // 参考正确流的信息
                        if (refTree.getBestRouteFromNode(peer).equals(curNode)) {
                            primNearestNodeMap.put(peer, curNode);
                        }
                    }
                }
            }
            // 选下一个进入树的节点
            // TODO: 根据refTree选？
            String nextNode = unreachableNodes.iterator().next();
            for (String unreachNode : unreachableNodes) {
                if (distanceMap.get(nextNode) > distanceMap.get(unreachNode)) {
                    nextNode = unreachNode;
                }
            }
            assert bgpTopology.isConfiguredPeer(nextNode, primNearestNodeMap.get(nextNode));
            _oldBgpTree.addBestRouteFromEdge(nextNode, primNearestNodeMap.get(nextNode));
            curNode = nextNode;
            unreachableNodes.remove(curNode);
            reqReachNodes.remove(curNode);
            reachableNodes.add(curNode);
            printStringList(_oldBgpTree.getBestRouteFromPath(curNode, _dstDevName), "NEW-PATH", ", ");
        }
        _oldBgpTree.setUnreachableNodes(unreachableNodes);
        return _oldBgpTree;
    }

    private String getNextNodeAddToMST(Set<String> nodes, Map<String, Integer> distanceMap) {
        if (nodes.size()<1) {
            return null;
        } else if (nodes.size()==1) {
            return nodes.iterator().next();
        } else {
            String node = nodes.iterator().next();
            int dis = distanceMap.get(node);
            for (String n: nodes) {
                if (distanceMap.get(n) < dis) {
                    dis = distanceMap.get(n);
                    node = n;
                }
            }
            return node;
        }
    }


 
    public static Map<String, Map<String, Map<String, List<StaticRoute>>>> genStaticOrDirectRouteFromFile(String filePath, Protocol protocol) {
        String jsonStr = BgpDiagnosis.fromJsonToString(filePath);
        if (jsonStr==null || jsonStr.equals("")) {
            return null;
        }
        JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();
        String protocolType = protocol.getProtocol();
        String targetKey = "";
        // 检测key值里是否有匹配协议名称的关键字
        for (String keyString : jsonObject.keySet()) {
            if (keyString.toLowerCase().contains(protocolType)) {
                targetKey = keyString;
                break;
            }
        }
        // 没有关键词则返回
        if (targetKey.equals("")) {
            return null;
        }
        // 按照返回类型格式解析相应的json对象
        jsonStr = jsonObject.get(targetKey).toString();
        // Map<String, Map<String, List<StaticRoute>>> routes = new Gson().fromJson(jsonStr, new TypeToken<Map<String, Map<String, List<StaticRoute>>>>() {}.getType());
        return new Gson().fromJson(jsonStr, new TypeToken<Map<String, Map<String, Map<String, List<StaticRoute>>>>>() {}.getType());
    }

    public Map<String, Set<Node>> computeReachIgpNodes(BgpForwardingTree newBgpTree) {

        if (newBgpTree!=null) {
            return newBgpTree.computeReachIgpNodes(_bgpTopology);
        } else if (_oldBgpTree!=null) {
            return _oldBgpTree.computeReachIgpNodes(_bgpTopology);
        } else {
            return null;
        }
    }
}
