package org.sng.main.forwardingtree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpPeer;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.Interface;
import org.sng.main.common.Layer2Topology;
import org.sng.main.forwardingtree.BgpForwardingTree.TreeType;
import org.sng.main.localization.RouteForbiddenLocalizer;
import org.sng.main.util.KeyWord;

import com.google.common.collect.Sets;
import com.google.common.collect.Table;
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

    // 
    private BgpForwardingTree _oldBgpTree;
    private StaticForwardingTree _oldStaticTree;

    private BgpTopology _bgpTopology;

    private Layer2Topology _layer2Topology;



    public Generator(String nodeName, String prefix, BgpTopology bgpTopology) {
        _dstDevName = nodeName;
        _dstPrefix = Prefix.parse(prefix);
        _bgpTopology = bgpTopology;
    }

    public BgpTopology getBgpTopology() {
        return _bgpTopology;
    }

    public void setLayer2Topology(Layer2Topology layer2Topology) {
        _layer2Topology = layer2Topology;
    }

    public BgpForwardingTree getBgpTree() {
        return _oldBgpTree;
    }


    public int hopNumberToReachIpUsingStatic(String node, Ip ip) {
        // TODO: implement
        return 2;
    }

    public void genBgpRoutePropTree(String filePath) {
        // graph is the bgp topology

    }

    public void serializeTreeFromJson(String filePath, TreeType type) {
        File file = new File(filePath);
        System.out.println(filePath);
        String jsonStr;
        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");
            switch(type) {
                case BGP: {
                    // get BGP RIB
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(UPT_TABLE).getAsJsonObject();
                    _oldBgpTree = new BgpForwardingTree(_dstDevName, _dstPrefix);
                    _oldBgpTree.serializeBgpTreeFromProvJson(jsonObject, _dstPrefix.toString(), _bgpTopology);
                    break;
                }
                case STATIC: {
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(STATIC_INFO).getAsJsonObject();
                    _oldStaticTree = new StaticForwardingTree(_dstDevName, _dstPrefix);
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

    // public BgpForwardingTree getNewBGPForwardingTree (BgpForwardingTree referenceTree, Table<String, String, BgpPeer> peerTable) {
    //     BgpForwardingTree newBgpRouteFromTree = new BgpForwardingTree(_dstDevName, _dstPrefix);
    //     newBgpRouteFromTree.copyBestRouteFromMap(_oldBgpTree.getBestRouteFromMap());
    //     Set<String> 
    //     Set<String> reachableNodes = Sets.difference(_bgpTopology.getAllNodes().keySet(), _unreachNodes);

    //     List<String> unassignedNodes = new ArrayList<>(_unreachNodes);

    //     for (String node : _unreachNodes) {
    //         if (node.equals(_dstDevName)) {
    //             // skip the dstNode
    //             continue;
    //         }
    //         boolean ifAddRefPath = false;
    //         for (String middle : reachableNodes) {
    //             // subPath1: src--middle
    //             List<String> refPath = referenceTree.getBestRouteFromPath(node, middle);
    //             // subPath2: middle--dst
    //             List<String> remainPath = _oldBgpTree.getBestRouteFromPath(middle, _dstDevName);
    //             if (refPath!=null && refPath.size()>1) {
    //                 if (!ifTwoPathOverlap(refPath, remainPath.subList(1, remainPath.size()))) {
    //                     newBgpRouteFromTree.addBestRouteFromPath(refPath);
    //                     ifAddRefPath = true;
    //                 }
    //             }
    //         }
    //         // if (!ifAddRefPath && reachableNodes.size()>0) {
    //         //     // wrong
    //         //     newBgpRouteFromTree.addBestRouteFromPath(getPath(node, _dstDevName, peerTable).get(0));
    //         // }

    //         List<String> refPath = referenceTree.getBestRouteFromPath(node, referenceTree.getDstDevName());
    //         if (refPath==null) {
    //             continue;
    //         }
    //         for (int i=refPath.size()-1; i>=0; i-=1) {
    //             String divergeNode = refPath.get(i);
    //             if (peerTable.contains(divergeNode, _dstDevName) || peerTable.contains(_dstDevName, divergeNode)) {
    //                 List<String> newPath = copyPath(refPath, 0, i+1);
    //                 newPath.add(_dstDevName);
    //                 boolean flag = newBgpRouteFromTree.addBestRouteFromPath(newPath);
    //                 printStringList(newPath, "req-path", ",");
    //                 unassignedNodes.remove(node);
    //                 assert flag;
    //             }
    //         }
    
    //     }
    //     for (String leftedNode : unassignedNodes) {
    //         // usually the leftedNode is dstNode in the ref-tree
    //         if (leftedNode.equals(_dstDevName)) {
    //             continue;
    //         }

    //         Pattern pattern = Pattern.compile("([A-Z]+).*");
    //         Matcher matcher = pattern.matcher(leftedNode);

    //         for (String node : _bgpTopology.getAllNodes().keySet()) {
    //             // simlilar node
    //             if (matcher.find() && node.contains(matcher.group(1))) {
    //                 // get the path in req-tree as a reference
    //                 List<String> refPath = newBgpRouteFromTree.getBestRouteFromPath(node, newBgpRouteFromTree.getDstDevName());
    //                 for(int i=0; i<refPath.size()-1; i+=1) {
    //                     String divergeNode = refPath.get(i);
    //                     if (peerTable.contains(divergeNode, leftedNode) || peerTable.contains(leftedNode, divergeNode)) {
    //                         List<String> newPath = copyPath(refPath, i, refPath.size());
    //                         newPath.add(0, leftedNode);;
    //                         boolean flag = newBgpRouteFromTree.addBestRouteFromPath(newPath);
    //                         printStringList(newPath, "req-path", ",");
    //                         unassignedNodes.remove(leftedNode);
    //                         assert flag;
    //                     }
    //                 }
    //             }
    //         }

    //     }
    //     printStringList(unassignedNodes, "still unreachable nodes", ",");
    //     return newBgpRouteFromTree;
    // }

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
    private Set<String> processReachNodes(Set<String> nodes) {
        Set<String> reachNodes = new HashSet<>(nodes);
        nodes.forEach(n->{
            reachNodes.addAll(_bgpTopology.getAllNodesInSameAs(n));
        });
        // 总是移除dst节点
        reachNodes.remove(_dstDevName);
        return reachNodes;
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

    public BgpForwardingTree genBgpTree(Set<String> reqReachNodes, Set<Interface> failedInfaces, BgpForwardingTree refTree) {
        // error oldTree 所在的generator调用，oldBGPTree已经在generator里
        // 目标src节点是当前generator里的unreachable nodes
        // 在现有error oldBGPTree上继续生成minTree【针对路由传播的tree: BestRouteFrom】

        Set<String> reachableNodes = new HashSet<>(_oldBgpTree.getReachableNodes());
        reqReachNodes.removeAll(reachableNodes);
        Set<String> unreachableNodes = processReachNodes(reqReachNodes);

        Map<String, Integer> distanceMap = new HashMap<>();
        Map<String, String> primNearestNodeMap = new HashMap<>(_oldBgpTree.getBestRouteFromMap());
        // disMap initialization
        unreachableNodes.stream().forEach(node->distanceMap.put(node, Integer.MAX_VALUE));
        reachableNodes.stream().forEach(node->distanceMap.put(node, _oldBgpTree.getBestRouteFromPath(node, _dstDevName).size()-1));
        // dstNode init
        distanceMap.put(_dstDevName, 0);
        // 用ref的连接信息参考作为Prim的加入节点选择（MST不止一个时）
        String curNode = _oldBgpTree.chooseFirstNodeHasUnreachablePeer(_bgpTopology); // 选一个已reach的开始
        while (unreachableNodes.size()>0) {
            for (String peer: _bgpTopology.getConfiguredPeers(curNode)) {
                if (reachableNodes.contains(peer) || !unreachableNodes.contains(peer)) {
                    // 更新disMap时只考虑spec要求的节点集合
                    continue;
                }
                if (distanceMap.get(curNode) + 1 < distanceMap.get(peer)) {
                    distanceMap.put(peer, distanceMap.get(curNode) + 1);
                    primNearestNodeMap.put(peer, curNode);
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
            assert _bgpTopology.isConfiguredPeer(nextNode, primNearestNodeMap.get(nextNode));
            _oldBgpTree.addBestRouteFromEdge(nextNode, primNearestNodeMap.get(nextNode));
            curNode = nextNode;
            unreachableNodes.remove(curNode);
            reachableNodes.add(curNode);
            printStringList(_oldBgpTree.getBestRouteFromPath(curNode, _dstDevName), "NEW-PATH", ", ");
        }
        _oldBgpTree.setUnreachableNodes(unreachableNodes);
        return _oldBgpTree;
    }


}
