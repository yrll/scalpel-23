package org.sng.main.localization;

import java.util.*;
import java.util.stream.Stream;

import org.sng.main.BgpDiagnosis;
import org.sng.main.common.BgpRoute;
import org.sng.main.common.StaticRoute;
import org.sng.main.diagnosis.Generator;
import org.sng.main.localization.RouteForbiddenLocalizer.Direction;

import com.google.gson.annotations.SerializedName;

// 每个设备一个violation实例

public class Violation {
    @SerializedName("ipPrefix")
    String ipPrefixString;
    String vpnName;
    List<String> violatedRrClient;
    List<BgpRoute> violatedPropNeighbors;
    List<BgpRoute> violatedAcptNeighbors;
    // prefer 的表示?
    List<Map<String, BgpRoute>> violatedRoutePrefer;
    Set<String> violateIbgpPeer;
    Set<String> violateEbgpPeer;
    StaticRoute originStaticRoute;
    StaticRoute originDirectRoute;
    // DirectRoute originDirectRoute;
    int missingLineCounter;

    // 描述redis失败的原因的字符串，用逗号分隔多个原因
    String violateRedis;

    public void addViolateEbgpPeer(String node) {
        if (violateEbgpPeer==null) {
            violateEbgpPeer = new HashSet<String>();
        }
        violateEbgpPeer.add(node);
    }

    public void addViolateIbgpPeer(String node) {
        if (violateIbgpPeer==null) {
            violateIbgpPeer = new HashSet<String>();
        }
        violateIbgpPeer.add(node);
    }

    public static <T> boolean ifListValid(List<T> aimList) {
        if (aimList==null || aimList.size()<1) {
            return false;
        } 
        return true;
    }

    public static <T> boolean ifSetValid(Set<T> aimList) {
        if (aimList==null || aimList.size()<1) {
            return false;
        } 
        return true;
    }

    public int getMissingLine() {
        missingLineCounter -= 1;
        return missingLineCounter;
    }

    public List<BgpRoute> getViolatedPropNeighbors() {
        return violatedPropNeighbors;
    }

    public List<BgpRoute> getViolatedAcptNeighbors() {
        return violatedAcptNeighbors;
    }

    public Set<String> getViolateEbgpPeers() {
        return violateEbgpPeer;
    }

    public Set<String> getViolateIbgpPeers() {
        return violateIbgpPeer;
    }

    public Map<Integer, String> localize(String curDevName, Generator newGenerator, Generator errGenerator) {

        Set<Localizer> results = new HashSet<>();

        if (ifListValid(violatedRrClient)) {
            results.add(new ReflectClientLocalizer(curDevName, violatedRrClient, this, errGenerator.getBgpTopology()));
        }

        if (ifListValid(violatedAcptNeighbors)) {
            violatedAcptNeighbors.forEach(n->{
                RouteForbiddenLocalizer routeForbiddenLocalizer = new RouteForbiddenLocalizer(curDevName, n, Direction.IN, this, newGenerator.getBgpTopology());
                String peer = routeForbiddenLocalizer.getRelatedPeer();
                if (peer!=null) {
                    if (!errGenerator.getBgpTopology().isValidPeer(curDevName, peer)) {
                        results.add(new PeerLocalizer(curDevName, peer, errGenerator, this, newGenerator.getBgpTopology()));
                    }
                }
                results.add(routeForbiddenLocalizer);
            });
        }

        if (ifListValid(violatedPropNeighbors)) {
            violatedPropNeighbors.forEach(n->{

                results.add(new RouteForbiddenLocalizer(curDevName, n, Direction.OUT, this, errGenerator.getBgpTopology()));
            });
        }

        if (ifSetValid(violateEbgpPeer)) {
            violateEbgpPeer.forEach(n->{
                results.add(new PeerLocalizer(curDevName, n, errGenerator, this));
            });
        }

        if (ifSetValid(violateIbgpPeer)) {
            violateIbgpPeer.forEach(n->{
                results.add(new PeerLocalizer(curDevName, n, errGenerator, this));
            });
        }

        if (violateRedis!=null && !violateRedis.equals("")) {
            StaticRoute targetRoute = originStaticRoute;
            if (originStaticRoute==null && originDirectRoute!=null) {
                targetRoute = originDirectRoute;
            } else {
                // 有redistribution的错，但是没有violatedRoute，生成一条valid的静态路由
                String nextHopString = errGenerator.getBgpTopology().getNodeIp(curDevName);
                targetRoute = new StaticRoute(curDevName, vpnName, ipPrefixString, nextHopString);
            }
            results.add(new RedistributionLocalizer(curDevName, violateRedis, targetRoute, this, errGenerator.getBgpTopology()));
        }

        Map<Integer, String> lineMap = new LinkedHashMap<>();
        results.forEach(n->{
            lineMap.putAll(n.getErrorConfigLines());
        });
        return lineMap;
    }

//    public Set<PeerLocalizer> postProcessRouteForbiddenError() {
//        // 检测单边的，因为如果peer没配对，两边都会受影响
//        for (BgpRoute bgpRoute: violatedAcptNeighbors) {
//
//        }
//        return null;
//    }
}
