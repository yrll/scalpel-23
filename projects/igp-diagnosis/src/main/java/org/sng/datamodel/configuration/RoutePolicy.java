package org.sng.datamodel.configuration;

import java.util.List;

public class RoutePolicy {
    private final Integer _id;
    private final String _routePolicyName;
    private final List<RoutePolicyNode> _routePolicyNodeModelList;


    public RoutePolicy(Integer id, String routePolicyName, List<RoutePolicyNode> routePolicyNodeModelList) {
        _id = id;
        _routePolicyName = routePolicyName;
        _routePolicyNodeModelList = routePolicyNodeModelList;
    }

    public Integer getId() {
        return _id;
    }

    public String getRoutePolicyName() {
        return _routePolicyName;
    }

    public List<RoutePolicyNode> getRoutePolicyNodeModelList() {
        return _routePolicyNodeModelList;
    }
}
