package org.sng.datamodel.configuration;

public class RoutePolicyNode {
    public static String PERMIT_MODE = "PERMIT";
    public static String DENY_MODE = "DENY";
    private final Integer _nodeNum;
    private final String _policyMatchMode;
    private final IpPrefixesV4Info _ifMatchIpPrefix;
    private final Boolean _ifMatchTag;
    private final Integer _applyTag;


    public RoutePolicyNode(Integer nodeNum, String policyMatchMode, IpPrefixesV4Info ifMatchIpPrefix, Boolean ifMatchTag, Integer applyTag) {
        _nodeNum = nodeNum;
        _policyMatchMode = policyMatchMode;
        _ifMatchIpPrefix = ifMatchIpPrefix;
        _ifMatchTag = ifMatchTag;
        _applyTag = applyTag;
    }

    public Integer getNodeNum() {
        return _nodeNum;
    }

    public String getPolicyMatchMode() {
        return _policyMatchMode;
    }

    public IpPrefixesV4Info getIfMatchIpPrefix() {
        return _ifMatchIpPrefix;
    }

    public Boolean getIfMatchTag() {
        return _ifMatchTag;
    }

    public Integer getApplyTag() {
        return _applyTag;
    }
}
