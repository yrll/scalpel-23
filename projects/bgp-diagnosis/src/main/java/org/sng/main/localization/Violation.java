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

    public Set<String> getViolateEbgpPeers() {
        return violateEbgpPeer;
    }

    public Set<String> getViolateIbgpPeers() {
        return violateIbgpPeer;
    }

    public Map<Integer, String> localize(String curDevName, Generator generator) {

        List<Localizer> results = new ArrayList<>();

        if (ifListValid(violatedRrClient)) {
            results.add(new ReflectClientLocalizer(curDevName, violatedRrClient, this, generator.getBgpTopology()));
        }

        if (ifListValid(violatedAcptNeighbors)) {
            violatedAcptNeighbors.forEach(n->{
                results.add(new RouteForbiddenLocalizer(curDevName, n, Direction.IN, this, generator.getBgpTopology()));
            });
        }

        if (ifListValid(violatedPropNeighbors)) {
            violatedPropNeighbors.forEach(n->{
                results.add(new RouteForbiddenLocalizer(curDevName, n, Direction.OUT, this, generator.getBgpTopology()));
            });
        }

        if (ifSetValid(violateEbgpPeer)) {
            violateEbgpPeer.forEach(n->{
                results.add(new PeerLocalizer(curDevName, n, generator, this));
            });
        }

        if (ifSetValid(violateIbgpPeer)) {
            violateIbgpPeer.forEach(n->{
                results.add(new PeerLocalizer(curDevName, n, generator, this));
            });
        }

        if (violateRedis!=null && !violateRedis.equals("")) {
            StaticRoute targetRoute = originStaticRoute;
            if (originStaticRoute==null && originDirectRoute!=null) {
                targetRoute = originDirectRoute;
            } else {
                // 有redistribution的错，但是没有violatedRoute，生成一条valid的静态路由
                String nextHopString = generator.getBgpTopology().getNodeIp(curDevName);
                targetRoute = new StaticRoute(curDevName, vpnName, ipPrefixString, nextHopString);
            }
            results.add(new RedistributionLocalizer(curDevName, violateRedis, targetRoute, this, generator.getBgpTopology()));

        }

        Map<Integer, String> lineMap = new LinkedHashMap<>();
        results.forEach(n->{
            lineMap.putAll(n.getErrorConfigLines());
        });
        return lineMap;
    }

}
