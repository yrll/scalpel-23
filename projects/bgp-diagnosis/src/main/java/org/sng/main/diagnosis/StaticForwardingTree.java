package org.sng.main.diagnosis;

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
    private String _vpnName;

    private Map<String, String> _nextHopForwardingMap;

    private Prefix _dstPrefix;
    private String _dstDevName;

    private Map<String, List<StaticRoute>> _routesMap;
    private Map<String, StaticRoute> _bestRouteMap;

    public StaticForwardingTree(String dstDev, Prefix prefix, String vpnName) {
        _dstDevName = dstDev;
        _dstPrefix = prefix;
        _nextHopForwardingMap = new HashMap<>();
        _routesMap = new HashMap<>();
        _vpnName = vpnName;
        _bestRouteMap = new HashMap<>();
    }




    public String getNextHop(String node) {
        return _nextHopForwardingMap.get(node);
    }

    public int getRoutePref(String node) {
        if (_routesMap.containsKey(node) && _routesMap.get(node).size()>0) {
            return getMaxPrefRoute(_routesMap.get(node)).getPref();
        }
        throw new IllegalArgumentException("Node (" + node + ")" + "has not target static route");
    }

    public StaticRoute getBestRoute(String node) {
        if (_routesMap.containsKey(node) && _routesMap.get(node).size()>0) {
            return getMaxPrefRoute(_routesMap.get(node));
        }
        throw new IllegalArgumentException("Node (" + node + ")" + "has not target static route");
    }

    public StaticRoute getMaxPrefRoute(List<StaticRoute> routes) {
        return routes.stream().min(Comparator.comparing(StaticRoute::getPref)).get();
    }

    public void addForwardingInfo(String node, StaticRoute curRoute, Layer2Topology layer2Topology) {
        if (!_routesMap.containsKey(node)) {
            _routesMap.put(node, new ArrayList<StaticRoute>());
        }
        _routesMap.get(node).add(curRoute);
        if (_nextHopForwardingMap.containsKey(node)) {
            if (curRoute.getPref() > _bestRouteMap.get(node).getPref()) {
                return;
            }
        }

        // 根据layer2 topo，把端口对应的邻接点名称加入map，如果没有对端设备，nextHop还是用接口名称
        if (layer2Topology!=null) {
            String nextNode = layer2Topology.getPeerDevNameFromInface(node, curRoute.getInterface());
            if (nextNode!=null) {
                _nextHopForwardingMap.put(node, nextNode);
            } else {
                String nextOutString = curRoute.getOutInfName();
                _nextHopForwardingMap.put(node, nextOutString);
            }
        } else {
            _nextHopForwardingMap.put(node, curRoute.getOutInfName());
        }
        _bestRouteMap.put(node, curRoute);
    }
    
    public StaticForwardingTree serializeStaticTreeFromProvJson(JsonObject jsonObject, String ip, Layer2Topology layer2Topology) {
        // input "updateInfo" as jsonObject
        Prefix tagetPrefix = Prefix.parse(ip);
        Map<String, String> cfgPathMap = BgpDiagnosis.cfgPathMap;
        for (String node : jsonObject.asMap().keySet()) {
            JsonObject allRoutes = jsonObject.asMap().get(node).getAsJsonObject();
            if (allRoutes.keySet().contains(_vpnName)) {
                JsonObject allVpnRoutes = allRoutes.get(_vpnName).getAsJsonObject();
                for (String ipString : allVpnRoutes.keySet()) {
                    Prefix curPrefix = Prefix.parse(ipString);
                    if (curPrefix.containsPrefix(tagetPrefix)) {
                        // 路由前缀匹配
                        for (JsonElement route_raw : allVpnRoutes.get(ipString).getAsJsonArray()) {

                            // 解析当前静态路由
                            StaticRoute curRoute = new Gson().fromJson(route_raw.toString(), StaticRoute.class);
                            curRoute.checkPrefix();
                            curRoute = ConfigTaint.staticRouteRefine(node, curRoute);
                            // 一些refine处理：Prefix转换，pref值读取
                            addForwardingInfo(node, curRoute, layer2Topology);
                            
                        }
                    }
                }
            }
            
        }
        // 为所有没有static的节点检查一遍配置，这里检查静态路由还会加入interface的信息（如果有对于inf
        // 也查找直连路由
        for (String node: cfgPathMap.keySet()) {
            StaticRoute route = ConfigTaint.staticRouteFinder(node, tagetPrefix, true);
            if (route!=null) {
                if (route.getInterface()==null && route.getNextHopString()!=null) {
                    route.setInterface(layer2Topology.getIpLocatedInterface(node, route.getNextHopString()));
                }
                addForwardingInfo(node, route, layer2Topology);
            }
        }
        return this;
    }

}
