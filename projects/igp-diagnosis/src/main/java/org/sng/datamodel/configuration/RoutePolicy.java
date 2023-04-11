package org.sng.datamodel.configuration;

import org.sng.datamodel.Prefix;

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

    // todo: tag的判断
    public MatchedPolicy match(Prefix prefix){
        MatchedPolicy matchedPolicy = null;
        for (RoutePolicyNode routePolicyNode : _routePolicyNodeModelList){
            IpPrefixesV4Info ipPrefixesV4Info = routePolicyNode.getIfMatchIpPrefix();
            if (ipPrefixesV4Info == null){
                matchedPolicy = new MatchedPolicy(this, routePolicyNode,null,null);
                break;
            }
            else {
                IpPrefixNodeModel matchIpPrefixNode = ipPrefixesV4Info.matchPrefix(prefix);
                if (matchIpPrefixNode != null){
                    matchedPolicy = new MatchedPolicy(this, routePolicyNode,ipPrefixesV4Info,matchIpPrefixNode);
                    break;
                }
            }

        }
        return matchedPolicy;
    }

    public class MatchedPolicy{
        private final RoutePolicy _routePolicy;
        private final RoutePolicyNode _routePolicyNode;
        private final IpPrefixesV4Info _ipPrefixesV4Info;
        private final IpPrefixNodeModel _ipPrefixNode;


        public MatchedPolicy(RoutePolicy routePolicy, RoutePolicyNode routePolicyNode, IpPrefixesV4Info ipPrefixesV4Info, IpPrefixNodeModel ipPrefixNode) {
            _routePolicy = routePolicy;
            _routePolicyNode = routePolicyNode;
            _ipPrefixesV4Info = ipPrefixesV4Info;
            _ipPrefixNode = ipPrefixNode;
        }

        public RoutePolicy getRoutePolicy(){
            return _routePolicy;
        }

        public RoutePolicyNode getRoutePolicyNode() {
            return _routePolicyNode;
        }

        public IpPrefixesV4Info getIpPrefixesV4Info() {
            return _ipPrefixesV4Info;
        }

        public IpPrefixNodeModel getIpPrefixNode() {
            return _ipPrefixNode;
        }
    }
}
