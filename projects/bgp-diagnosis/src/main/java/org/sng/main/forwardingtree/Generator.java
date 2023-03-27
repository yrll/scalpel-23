package org.sng.main.forwardingtree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.main.common.BgpPeer;
import org.sng.main.common.BgpTopology;
import org.sng.main.forwardingtree.ForwardingTree.TreeType;
import org.sng.util.KeyWord;

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
    private ForwardingTree _oldForwardingTree;
    private ForwardingTree _oldBgpTree;
    private ForwardingTree _oldStaticTree;
    private Set<String> _unreachNodes;
    private BgpTopology _bgpTopology;


    public Generator(String nodeName, String prefix, BgpTopology bgpTopology) {
        _dstDevName = nodeName;
        _dstPrefix = Prefix.parse(prefix);
        _bgpTopology = bgpTopology;
    }

    public ForwardingTree getBgpTree() {
        return _oldBgpTree;
    }

    // public Generator(String nodeName, String prefix) {
    //     _dstDevName = nodeName;
    //     _dstPrefix = Prefix.parse(prefix);
    //     // _oldBgpTree = new ForwardingTree();
    //     // _oldStaticTree = new ForwardingTree();
    // }

    // public ForwardingTree serializeTreeFromJsonFile(String path, TreeType type, String ip) {
    //     File file = new File(path);
    //     String jsonStr;
    //     try {
    //         jsonStr = FileUtils.readFileToString(file,"UTF-8");
    //         switch(type) {
    //             case BGP: {
    //                 // get BGP RIB
    //                 JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(UPT_TABLE).getAsJsonObject();
    //                 return ForwardingTree.serializeBgpTreeFromProvJson(jsonObject, ip);
    //             }
    //             case STATIC: {
    //                 JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(STATIC_INFO).getAsJsonObject();
    //                 return ForwardingTree.serializeStaticTreeFromProvJson(jsonObject, ip);
    //             }
    //         }
    //     } catch (IOException e) {
    //         // TODO Auto-generated catch block
    //         e.printStackTrace();
    //     }
    //     return new ForwardingTree();
    // }

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
                    _oldBgpTree = new ForwardingTree(_dstDevName, _dstPrefix);
                    _unreachNodes = _oldBgpTree.serializeBgpTreeAndPropTreeFromProvJson(jsonObject, _dstPrefix.toString(), _bgpTopology.getAllNodes());
                    
                    break;
                }
                case STATIC: {
                    JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(STATIC_INFO).getAsJsonObject();
                    _oldStaticTree = new ForwardingTree(_dstDevName, _dstPrefix);
                    _oldStaticTree.serializeStaticTreeFromProvJson(jsonObject, _dstPrefix.toString());
                    break;
                } 
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        
        
        System.out.println("pause");
        
    }


    public void setBGPForwardingTree(ForwardingTree tree) {
        _oldBgpTree = tree;
    }

    public void setStaticForwardingTree(ForwardingTree tree) {
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

    public ForwardingTree getNewBGPForwardingTree (ForwardingTree referenceTree, Table<String, String, BgpPeer> peerTable) {
        ForwardingTree newBgpRouteFromTree = new ForwardingTree(_dstDevName, _dstPrefix);
        newBgpRouteFromTree.copyBestRouteFromMap(_oldBgpTree.getBestRouteFromMap());
        Set<String> reachableNodes = Sets.difference(_bgpTopology.getAllNodes().keySet(), _unreachNodes);

        List<String> unassignedNodes = new ArrayList<>(_unreachNodes);

        for (String node : _unreachNodes) {
            if (node.equals(_dstDevName)) {
                // skip the dstNode
                continue;
            }
            boolean ifAddRefPath = false;
            for (String middle : reachableNodes) {
                // subPath1: src--middle
                List<String> refPath = referenceTree.getBestRouteFromPath(node, middle);
                // subPath2: middle--dst
                List<String> remainPath = _oldBgpTree.getBestRouteFromPath(middle, _dstDevName);
                if (refPath.size()>1) {
                    if (!ifTwoPathOverlap(refPath, remainPath.subList(1, remainPath.size()))) {
                        newBgpRouteFromTree.addBestRouteFromPath(refPath);
                        ifAddRefPath = true;
                    }
                }
            }
            if (!ifAddRefPath && reachableNodes.size()>0) {
                // wrong
                newBgpRouteFromTree.addBestRouteFromPath(getPath(node, _dstDevName, peerTable).get(0));
            }

            List<String> refPath = referenceTree.getBestRouteFromPath(node, referenceTree.getDstDevName());
            
            for (int i=refPath.size()-1; i>=0; i-=1) {
                String divergeNode = refPath.get(i);
                if (peerTable.contains(divergeNode, _dstDevName) || peerTable.contains(_dstDevName, divergeNode)) {
                    List<String> newPath = copyPath(refPath, 0, i+1);
                    newPath.add(_dstDevName);
                    boolean flag = newBgpRouteFromTree.addBestRouteFromPath(newPath);
                    printStringList(newPath, "req-path", ",");
                    unassignedNodes.remove(node);
                    assert flag;
                }
            }
    
        }
        for (String leftedNode : unassignedNodes) {
            // usually the leftedNode is dstNode in the ref-tree
            if (leftedNode.equals(_dstDevName)) {
                continue;
            }

            Pattern pattern = Pattern.compile("([A-Z]+).*");
            Matcher matcher = pattern.matcher(leftedNode);

            for (String node : _bgpTopology.getAllNodes().keySet()) {
                // simlilar node
                if (matcher.find() && node.contains(matcher.group(1))) {
                    // get the path in req-tree as a reference
                    List<String> refPath = newBgpRouteFromTree.getBestRouteFromPath(node, newBgpRouteFromTree.getDstDevName());
                    for(int i=0; i<refPath.size()-1; i+=1) {
                        String divergeNode = refPath.get(i);
                        if (peerTable.contains(divergeNode, leftedNode) || peerTable.contains(leftedNode, divergeNode)) {
                            List<String> newPath = copyPath(refPath, i, refPath.size());
                            newPath.add(0, leftedNode);;
                            boolean flag = newBgpRouteFromTree.addBestRouteFromPath(newPath);
                            printStringList(newPath, "req-path", ",");
                            unassignedNodes.remove(leftedNode);
                            assert flag;
                        }
                    }
                }
            }

        }
        printStringList(unassignedNodes, "still unreachable nodes", ",");
        return newBgpRouteFromTree;
    }

    private void printStringList(List<String> list, String title, String seperator) {
        System.out.println(KeyWord.PRINT_LINE_HALF + title + KeyWord.PRINT_LINE_HALF);
        for (String string : list) {
            System.out.print(string + seperator);
        }
        System.out.println();
    }

    private void printPath(List<String> path) {
        System.out.println(KeyWord.PRINT_LINE);
        for (String string : path) {
            System.out.print(string + ",");
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


}
