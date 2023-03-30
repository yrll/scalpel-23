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
    private Map<String, List<Layer2Edge>> _edgeMap;
    private Set<Layer2Edge> _edges;

    public Layer2Topology(Set<Layer2Edge> edges) {
        _edges = edges;
    }

    public Layer2Topology(Set<Layer2Edge> edges, Map<String, List<Layer2Edge>> edgeMap) {
        _edges = edges;
        _edgeMap = edgeMap;
    }

    public static Layer2Topology creat(Set<Layer2Node> nodes) {
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
                    edgeMap.get(node.getDevName()).add(curEdge);
                    edgeMap.get(nodeRemain.getDevName()).add(curEdge);
                    iter.remove();
                }
            }  
        }
        return new Layer2Topology(edges, edgeMap);
    }

    public static Layer2Topology fromJson(String filePath) {
        Set<Layer2Node> layer2Nodes = new HashSet<>();
        String rawJsonStr = BgpDiagnosis.fromJsonToString(filePath);
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
}
