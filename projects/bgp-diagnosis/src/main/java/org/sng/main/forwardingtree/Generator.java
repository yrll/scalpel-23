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
import org.sng.main.common.Layer2Topology;
import org.sng.main.forwardingtree.BgpForwardingTree.TreeType;
import org.sng.main.util.KeyWord;

import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;

public class Generator {

    private static String UPT_TABLE = "updateTable";
    private static String CONVERGE_TABLE = "convergeInfo";
    private static String STATIC_INFO = "staticRouteInfo";

    private String _dstDevName;
    private Prefix _dstPrefix;

    private BgpForwardingTree _oldBgpTree;
    private StaticForwardingTree _oldStaticTree;

    private BgpTopology _bgpTopology;

    private Layer2Topology _layer2Topology;



    public Generator(String nodeName, String prefix, BgpTopology bgpTopology) {
        _dstDevName = nodeName;
        _dstPrefix = Prefix.parse(prefix);
        _bgpTopology = bgpTopology;
    }

    public void setLayer2Topology(Layer2Topology layer2Topology) {
        _layer2Topology = layer2Topology;
    }

    public BgpForwardingTree getBgpTree() {
        return _oldBgpTree;
    }

    public void genBgpRoutePropTree(String filePath) {
        // graph is the bgp topology

    }

    public void serializeTreeFromJson(String filePath, TreeType type) {
        File file = new File(filePath);
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
                    _oldStaticTree.serializeStaticTreeFromProvJson(jsonObject, _dstPrefix.toString());
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

    public BgpForwardingTree genBgpTree(BgpForwardingTree refTree) {
        // error oldTree 所在的generator调用，oldBGPTree已经在generator里
        // 目标src节点是当前generator里的unreachable nodes
        // 在现有error oldBGPTree上继续生成minTree【针对路由传播的tree: BestRouteFrom】
        
        // 现有的错误的tree必须是连通的
        // TODO: 现有的【根据prov信息生成的】tree不连通？会有这种情况出现么？不会吧

        Set<String> reachableNodes = new HashSet<>(_oldBgpTree.getReachableNodes());
        Set<String> unreachableNodes = new HashSet<>(_oldBgpTree.getUnreachableNodes());

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
                if (reachableNodes.contains(peer)) {
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
