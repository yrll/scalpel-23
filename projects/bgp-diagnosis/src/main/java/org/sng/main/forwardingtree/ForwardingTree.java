package org.sng.main.forwardingtree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.datamodel.Prefix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The forwarding tree is for specific single prefix (one dst multi src)
 */

public class ForwardingTree {
    private Prefix _dstPrefix;
    private String _dstDevName;
    // private Map<Integer, String> _nameMap;
    private Map<String, Node> _nextHopMap;

    public ForwardingTree() {
        _dstDevName = KeyWord.UNKNOWN;
        _dstPrefix = Prefix.ZERO;
        _nextHopMap = new HashMap<>();
    }
    public ForwardingTree(Prefix prefix, Map<String, Node> nextHopMap) {
        _dstPrefix = prefix;
        _nextHopMap = nextHopMap;
    }

    public ForwardingTree(String dstDev, Prefix prefix, Map<String, Node> nextHopMap) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopMap = nextHopMap;
    }

    public List<Node> getForwardingPath(String startDevName, String endDevName) {
        List<Node> path = new ArrayList<>();
        path.add(new Node(startDevName, Prefix.ZERO));
        while(true) {
            Node thisNode = path.get(path.size()-1);
            if (thisNode.getDevName().equals(endDevName)) {
                break;
            }
            path.add(_nextHopMap.get(thisNode.getDevName()));
        }
        return path;
    }

    public static ForwardingTree serializeBgpTreeFromProvJson(JsonObject jsonObject, String ip) {
        // input "updateInfo" as jsonObject
        Map<String, Node> nextHopMap = new HashMap<>();
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
                    assert !nextHopMap.containsKey(node);
                    nextHopMap.put(node, new Node(nextHopDev, nextHopIp));
                }
            } 
        }
        return new ForwardingTree(Prefix.parse(ip), nextHopMap);
    }

    public static ForwardingTree serializeStaticTreeFromProvJson(JsonObject jsonObject, String ip) {
        // input "updateInfo" as jsonObject
        Map<String, Node> nextHopMap = new HashMap<>();
        for (String node : jsonObject.asMap().keySet()) {
            JsonObject allRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            for (String vpnName : allRoutes.asMap().keySet()) {
                JsonObject typedRoutes = allRoutes.asMap().get(vpnName).getAsJsonObject();
                if (typedRoutes.get(ip)!=null) {
                    for (JsonElement route_raw : typedRoutes.get(ip).getAsJsonArray()) {
                        
                        // JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();
                        String nextHopIface = route_raw.getAsJsonObject().get(KeyWord.OUT_INFO).getAsJsonObject().get(KeyWord.IFACE_NAME).getAsString();
                        String nextHopIp = route_raw.getAsJsonObject().get(KeyWord.OUT_INFO).getAsJsonObject().get(KeyWord.IFACE_IP).getAsString();
                        
                        assert !nextHopMap.containsKey(node);
                        // need rewriting, parse iface and iface_ip
                        nextHopMap.put(node, new Node(nextHopIface, nextHopIp));
                    }
                } 
            }
            
        }
        return new ForwardingTree(Prefix.parse(ip), nextHopMap);
    }

    // public static ForwardingTree serializeBgpTreeFromFile(String filePath, String ip) {

    // }


}
