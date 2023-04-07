package org.sng.main.common;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sng.main.BgpDiagnosis;
import org.sng.main.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class Layer2Topology {
    // 
    private Map<String, List<Layer2Edge>> _edgeMap;
    private Set<Layer2Edge> _edges;
    // 有些node没有对应edge，比如loopback0，所以单独创建一个node的集合维护所有信息
    private Set<Layer2Node> _nodes;

    public Layer2Topology(Set<Layer2Edge> edges, Map<String, List<Layer2Edge>> edgeMap, Set<Layer2Node> nodes) {
        _edges = edges;
        _edgeMap = edgeMap;
        _nodes = nodes;
    }

    public static Layer2Topology creat(Set<Layer2Node> nodes) {
        // 首先把node里用String表示的ip地址都转成相应对象实例
        nodes.forEach(n->n.checkInfPrefix());
        Set<Layer2Edge> edges = new HashSet<>();
        Map<String, List<Layer2Edge>> edgeMap = new HashMap<>();

        Set<Layer2Node> nodesRemain = new HashSet<>(nodes);

        for (Layer2Node node: nodes) {
            Iterator<Layer2Node> iter = nodesRemain.iterator();
            while(iter.hasNext()) {
                Layer2Node nodeRemain = iter.next();
                if (node.getDevName().equals(nodeRemain.getDevName())) {
                    continue;
                }
                if (node.isConnectedLayer2Node(nodeRemain)) {
                    Layer2Edge curEdge = new Layer2Edge(node, nodeRemain);
                    if (!edgeMap.containsKey(nodeRemain.getDevName())) {
                        edgeMap.put(nodeRemain.getDevName(), new ArrayList<Layer2Edge>());
                    }
                    if (!edgeMap.containsKey(node.getDevName())) {
                        edgeMap.put(node.getDevName(), new ArrayList<Layer2Edge>());
                    }
                    edges.add(curEdge);
                    // edgeMap双向添加
                    edgeMap.get(node.getDevName()).add(curEdge);
                    edgeMap.get(nodeRemain.getDevName()).add(curEdge);
                    iter.remove();
                }
            }  
        }
        return new Layer2Topology(edges, edgeMap, nodes);
    }

    public static Layer2Topology fromJson(String filePath) {
        Set<Layer2Node> layer2Nodes = new HashSet<>();
        String rawJsonStr = BgpDiagnosis.fromJsonToString(filePath);
        // file为空时 返回空拓扑
        if (rawJsonStr==null || rawJsonStr.equals("")) {
            return Layer2Topology.creat(layer2Nodes);
        }
        JsonObject jsonObject = JsonParser.parseString(rawJsonStr).getAsJsonObject().get(KeyWord.ALL_VPN_BINDING_INFO).getAsJsonObject();
        
        for (String nodeName: jsonObject.asMap().keySet()) {
            JsonObject infObject = jsonObject.asMap().get(nodeName).getAsJsonObject();
            Map<String, List<Interface>> infMap = new Gson().fromJson(infObject.toString(), new TypeToken<Map<String, List<Interface>>>() {}.getType());

            for (String nn: infMap.keySet()) {
                List<Interface> infs = infMap.get(nn);
                infs.forEach(inf->layer2Nodes.add(new Layer2Node(nodeName, inf)));
            }
        }
        return creat(layer2Nodes);
    }

    public String getPeerDevNameFromInface(String node, Interface iface) {
        if (!_edgeMap.containsKey(node) || iface.getPrefix()==null) {
            return null;
        }
        for (Layer2Edge layer2Edge : _edgeMap.get(node)) {
            // 该edge任一边节点和iface的节点相同就找到了
            String ip1 = layer2Edge.getInfPrefix().toString();
            String ip2 = iface.getPrefix().toString();
            if (layer2Edge.getInfPrefix().equals(iface.getPrefix())) {
                if (layer2Edge.getNode1Name().equals(node)) {
                    return layer2Edge.getNode2Name();
                } else {
                    return layer2Edge.getNode1Name();
                }
            } 
        }
        return null;
    }

    public boolean ifTwoNodesHaveCommonInf(String node1, String node2) {
        if (!_edgeMap.containsKey(node2)) {
            // map是双向的，只要有一边不是key就一定找不到common interface
            return false;
        }
        // 选择长度更小的list遍历
        String targetNode;
        if (_edgeMap.get(node1).size()<_edgeMap.get(node2).size()) {
            targetNode = node1;
        } else {
            targetNode = node2;
        }
        for (Layer2Edge layer2Edge : _edgeMap.get(targetNode)) {
            if (layer2Edge.getAnotherDevName(targetNode) != null) {
                return true;
            }
        }
        return false;
    }
}
