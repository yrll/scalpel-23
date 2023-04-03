package org.sng.main.localization;

import java.util.List;
import java.util.Map;

import org.sng.main.common.BgpRoute;

/*
 * Localize "violatedPropNeighbors"/"violatedAcptNeighbors" errors
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

    public enum Direction{
        IN,
        OUT
    }

    public RouteForbiddenLocalizer(String node, String policyName, String vpnName) {
        this.node = node;
        this.policyName = policyName;
        this.vpnName = vpnName;
    }

    public RouteForbiddenLocalizer(String node, BgpRoute fordidRoute, Direction direction) {
        this.node = node;
        // 如果是因为export被deny的，在export那端会有记录
        switch (direction) {
            case IN: this.policyName = fordidRoute.getImportPolicyName(); 
            case OUT: this.policyName = fordidRoute.getExportPolicyName(); 
            default: this.policyName = fordidRoute.getImportPolicyName(); 
        }
        
        this.vpnName = fordidRoute.getLatestVpnName();
    }

    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO Auto-generated method stub
        // 调涵洋的接口
        throw new UnsupportedOperationException("Unimplemented method 'getErrorConfigLines'");
    }
    
}
