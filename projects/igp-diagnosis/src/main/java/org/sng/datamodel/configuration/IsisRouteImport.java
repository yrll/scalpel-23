package org.sng.datamodel.configuration;

public class IsisRouteImport {
    private final String _protocol;
    private final Integer _protocolId;
    private final Integer _tagValue;
    private final Integer _cost;
    private final Boolean _inheritCost;
    private final RoutePolicy _routePolicyModel;
    private final Integer _isisCost4ImportRoute;

    public IsisRouteImport(String protocol, Integer protocolId, Integer tagValue, Integer cost, Boolean inheritCost, RoutePolicy routePolicyModel, Integer isisCost4ImportRoute) {
        _protocol = protocol;
        _protocolId = protocolId;
        _tagValue = tagValue;
        _cost = cost;
        _inheritCost = inheritCost;
        _routePolicyModel = routePolicyModel;
        _isisCost4ImportRoute = isisCost4ImportRoute;
    }

    public String getProtocol() {
        return _protocol;
    }

    public Integer getProtocolId() {
        return _protocolId;
    }

    public Integer getTagValue() {
        return _tagValue;
    }

    public Integer getCost() {
        return _cost;
    }

    public Boolean getInheritCost() {
        return _inheritCost;
    }

    public RoutePolicy getRoutePolicyModel() {
        return _routePolicyModel;
    }

    public Integer getIsisCost4ImportRoute() {
        return _isisCost4ImportRoute;
    }
}
