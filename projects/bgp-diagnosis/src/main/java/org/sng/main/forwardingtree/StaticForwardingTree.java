package org.sng.main.forwardingtree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.datamodel.Prefix;
import org.sng.main.common.StaticRoute;
import org.sng.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class StaticForwardingTree {

    private Map<String, String> _nextHopIfaceForwardingMap;

    private Prefix _dstPrefix;
    private String _dstDevName;

    private Map<String, List<StaticRoute>> _routeMap;

    public StaticForwardingTree(String dstDev, Prefix prefix) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopIfaceForwardingMap = new HashMap<>();
        _routeMap = new HashMap<>();
    }
    
    public StaticForwardingTree serializeStaticTreeFromProvJson(JsonObject jsonObject, String ip) {
        // input "updateInfo" as jsonObject
        // Map<String, Node> nextHopMap = new HashMap<>();
        for (String node : jsonObject.asMap().keySet()) {
            JsonObject allRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            for (String vpnName : allRoutes.asMap().keySet()) {
                JsonObject typedRoutes = allRoutes.asMap().get(vpnName).getAsJsonObject();
                if (typedRoutes.get(ip)!=null) {
                    // 解析的都是对应前缀的静态路由
                    for (JsonElement route_raw : typedRoutes.get(ip).getAsJsonArray()) {
                        
                        // JsonObject route = route_raw.getAsJsonObject().get(KeyWord.ROUTE).getAsJsonObject();
                        // String nextHopIface = route_raw.getAsJsonObject().get(KeyWord.OUT_INFO).getAsJsonObject().get(KeyWord.IFACE_NAME).getAsString();
                        // String nextHopIp = route_raw.getAsJsonObject().get(KeyWord.OUT_INFO).getAsJsonObject().get(KeyWord.IFACE_IP).getAsString();
                        if (!_routeMap.containsKey(node)) {
                            _routeMap.put(node, new ArrayList<StaticRoute>());
                        }
                        StaticRoute route = new Gson().fromJson(route_raw.toString(), StaticRoute.class);
                        _routeMap.get(node).add(route);
                        assert !_nextHopIfaceForwardingMap.containsKey(node);
                        // need rewriting, parse iface and iface_ip
                        _nextHopIfaceForwardingMap.put(node, route.getOutInfName());
                    }
                } 
            }
            
        }
        return this;
    }

}
