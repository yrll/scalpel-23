package org.sng.main.localization;


import java.util.HashMap;
import java.util.Map;

import org.sng.main.common.BgpRoute;
import org.sng.main.common.BgpTopology;
import org.sng.main.util.ConfigTaint;

/*
 * Localize "violatedPropNeighbors"/"violatedAcptNeighbors" errors
 * 针对peer export/import policy的
 * => Why a route can not be propagate expectedly?
 * 1) policy (filtered)
 * 2) vpn RT match
 */
public class RouteForbiddenLocalizer implements Localizer {

    String node;
    // 路由策略policy的名称（如果没有这个policy，那么就是vpn交叉失败导致forbid的）
    String policyName;
    // 路由对应vpn的名称 （当时为什么要加这个字段来着？：因为上一条注释）
    String vpnName;
    BgpRoute route;
    private Violation violation;
    // 新的bgpTopology
    private BgpTopology bgpTopology;
    // direction是按照violatedRule类型设置的，绝对准确
    Direction direction;
    // 这个表示没有正常建立peer关系的节点
    private String relatedPeer;
    private String peerIp;

    public enum Direction{
        IN("import"),
        OUT("export");
        String name;
        Direction(String name) {
            this.name = name;
        }
        String getName() {
            return name;
        }
    }

    public String getRelatedPeer() {
        return relatedPeer;
    }

    // public RouteForbiddenLocalizer(String node, String policyName, String vpnName, Violation violation) {
    //     this.node = node;
    //     this.policyName = policyName;
    //     this.vpnName = vpnName;
    //     this.violation = violation;
    // }

    public RouteForbiddenLocalizer(String node, BgpRoute fordidRoute, Direction direction, Violation violation, BgpTopology bgpTopology) {
        // 传入的bgp topo需要是假设的，不然可能找不到peer dev
        this.node = node;
        // 如果是因为export被deny的，在export那端会有记录
        switch (direction) {
            case IN: {
                this.policyName = fordidRoute.getImportPolicyName();
                break;
            } 
            case OUT: {
                this.policyName = fordidRoute.getExportPolicyName(); 
                break;
            }
            default: this.policyName = fordidRoute.getImportPolicyName(); 
        }
//        if (this.policyName==null || this.policyName.equals("")) {
//            this.policyName = "CAN NOT CROSS";
//        }
        this.direction = direction;
        this.vpnName = fordidRoute.getLatestVpnName();
        this.violation = violation;
        this.route = fordidRoute;
        this.bgpTopology = bgpTopology;
        // 获取peer信息（按方向
        peerIp = "0.0.0.0";
        if (direction.equals(Direction.IN)) {
            relatedPeer = route.getFromDeviceName();
            peerIp = BgpTopology.transPrefixOrIpToIpString(route.getPeerIpString());
            if (relatedPeer==null) {
                relatedPeer = bgpTopology.getNodeNameFromIp(peerIp);
            }

        } else if (direction.equals(Direction.OUT)) {
            String toDev = route.getToDeviceName();
            relatedPeer = route.getToDeviceName();
            peerIp = bgpTopology.getNodeIp(toDev);

        }

    }

    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO 先检查是不是因为 policy filter route【可能是 peer不通或者路由交叉不了】


        // STEP1: 检测是否直接配了peer ip policy
        String[] keyWords = {"peer", peerIp, policyName, direction.getName()};
        Map<Integer, String> taintResult = new HashMap<>();
        if (policyName!=null && peerIp!=null) {
             taintResult.putAll(ConfigTaint.peerTaint(node, keyWords));
        }

        taintResult.putAll(ConfigTaint.policyLinesFinder(node, policyName));
        return taintResult;
    }
    
}
