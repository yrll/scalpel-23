package org.sng.main.forwardingtree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.datamodel.Prefix;
import org.sng.main.BgpDiagnosis;
import org.sng.main.common.Layer2Topology;
import org.sng.main.common.StaticRoute;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class StaticForwardingTree {

    private Map<String, String> _nextHopForwardingMap;

    private Prefix _dstPrefix;
    private String _dstDevName;

    private Map<String, List<StaticRoute>> _routeMap;

    public StaticForwardingTree(String dstDev, Prefix prefix) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopForwardingMap = new HashMap<>();
        _routeMap = new HashMap<>();
    }

    public String getNextHop(String node) {
        return _nextHopForwardingMap.get(node);
    }

    public int getRoutePref(String node) {
        if (_routeMap.containsKey(node) && _routeMap.get(node).size()>0) {
            return getMaxPrefRoute(_routeMap.get(node)).getPref();
        }
        throw new IllegalArgumentException("Node (" + node + ")" + "has not target static route");
    }

    public StaticRoute getBestRoute(String node) {
        if (_routeMap.containsKey(node) && _routeMap.get(node).size()>0) {
            return getMaxPrefRoute(_routeMap.get(node));
        }
        throw new IllegalArgumentException("Node (" + node + ")" + "has not target static route");
    }

    public StaticRoute getMaxPrefRoute(List<StaticRoute> routes) {
        return routes.stream().min(Comparator.comparing(StaticRoute::getPref)).get();
    }
    
    public StaticForwardingTree serializeStaticTreeFromProvJson(JsonObject jsonObject, String ip, Layer2Topology layer2Topology) {
        // input "updateInfo" as jsonObject
        // Map<String, Node> nextHopMap = new HashMap<>();
        Map<String, String> cfgPathMap = BgpDiagnosis.cfgPathMap;
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
                        route.checkPrefix();
                        // 加上pref值
                        route = ConfigTaint.staticRouteFinder(cfgPathMap.get(node), route);
                        _routeMap.get(node).add(route);
                        assert !_nextHopForwardingMap.containsKey(node);
                        // 根据layer2 topo，把端口对应的邻接点名称加入map，如果没有对端设备，nextHop还是用接口名称
                        if (layer2Topology!=null) {
                            String nextNode = layer2Topology.getPeerDevNameFromInface(node, route.getInterface());
                            if (nextNode!=null) {
                                _nextHopForwardingMap.put(node, nextNode);
                            } else {
                                _nextHopForwardingMap.put(node, route.getOutInfName());
                            }
                        } else {
                            _nextHopForwardingMap.put(node, route.getOutInfName());
                        }
                        
                    }
                } 
            }
            
        }
        return this;
    }

}
