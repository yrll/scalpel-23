package org.sng.datamodel.configuration;

import java.util.List;

public class IsisConfiguration {
    private final Integer _isisId;
    private final String  _isisLevel;
    private final String _vpnName;
    private final String  _costStyle;
    private final String _areaId;
    private final String  _systemId;
    private final Boolean _summary;
    private final Integer _loadBalancingNum;
    private final Integer _circuitCost;
    private final List<IsisRouteImport> _importRoutes;
    private final String _networkEntity;


    public IsisConfiguration(Integer isisId, String isisLevel, String vpnName, String costStyle, String areaId, String systemId,
                             Boolean summary, Integer loadBalancingNum, Integer circuitCost, List<IsisRouteImport> importRoutes, String networkEntity) {
        _isisId = isisId;
        _isisLevel = isisLevel;
        _vpnName = vpnName;
        _costStyle = costStyle;
        _areaId = areaId;
        _systemId = systemId;
        _summary = summary;
        _loadBalancingNum = loadBalancingNum;
        _circuitCost = circuitCost;
        _importRoutes = importRoutes;
        _networkEntity = networkEntity;
    }

    public Integer getIsisId() {
        return _isisId;
    }

    public String getIsisLevel() {
        return _isisLevel;
    }

    public String getVpnName() {
        return _vpnName;
    }

    public String getCostStyle() {
        return _costStyle;
    }

    public String getAreaId() {
        return _areaId;
    }

    public String getSystemId() {
        return _systemId;
    }

    public Boolean getSummary() {
        return _summary;
    }

    public Integer getLoadBalancingNum() {
        return _loadBalancingNum;
    }

    public Integer getCircuitCost() {
        return _circuitCost;
    }

    public List<IsisRouteImport> getImportRoutes() {
        return _importRoutes;
    }
    public String getNetworkEntity() {
        return _networkEntity;
    }

}
