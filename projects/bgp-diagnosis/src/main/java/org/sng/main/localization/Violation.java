package org.sng.main.localization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.sng.main.BgpDiagnosis;
import org.sng.main.common.BgpRoute;
import org.sng.main.common.LocalRoute;
import org.sng.main.forwardingtree.Generator;
import org.sng.main.localization.RouteForbiddenLocalizer.Direction;

import com.google.gson.annotations.SerializedName;

// 每个设备一个violation实例

public class Violation {
    @SerializedName("ipPrefix")
    String ipPrefixString;
    List<String> violatedRrClient;
    List<BgpRoute> violatedPropNeighbors;
    List<BgpRoute> violatedAcptNeighbors;
    // prefer 的表示？
    List<String> violateIbgpPeer;
    List<String> violateEbgpPeer;
    LocalRoute originStaticRoute;
    LocalRoute originDirectRoute;
    // DirectRoute originDirectRoute;
    int missingLineCounter;

    // 描述redis失败的原因的字符串，用逗号分隔多个原因
    String violateRedis;

    public static <T> boolean ifListValid(List<T> aimList) {
        if (aimList==null || aimList.size()<1) {
            return false;
        } 
        return true;
    }

    public int getMissingLine() {
        missingLineCounter -= 1;
        return missingLineCounter;
    }

    public Map<Integer, String> localize(String curDevName, Generator generator) {

        List<Localizer> results = new ArrayList<>();

        if (ifListValid(violatedRrClient)) {
            results.add(new ReflectClientLocalizer(curDevName, violatedRrClient, this));
        }

        if (ifListValid(violatedAcptNeighbors)) {
            violatedAcptNeighbors.forEach(n->{
                results.add(new RouteForbiddenLocalizer(curDevName, n, Direction.IN, this));
            });
        }

        if (ifListValid(violatedPropNeighbors)) {
            violatedPropNeighbors.forEach(n->{
                results.add(new RouteForbiddenLocalizer(curDevName, n, Direction.OUT, this));
            });
        }

        if (ifListValid(violateEbgpPeer)) {
            violateEbgpPeer.forEach(n->{
                results.add(new PeerLocalizer(curDevName, n, generator, this));
            });
        }

        if (ifListValid(violateIbgpPeer)) {
            violateIbgpPeer.forEach(n->{
                results.add(new PeerLocalizer(curDevName, n, generator, this));
            });
        }

        if (violateRedis!=null || !violateRedis.equals("")) {
            results.add(new RedistributionLocalizer(curDevName, violateRedis, originStaticRoute, this));
        }

        Map<Integer, String> lineMap = new HashMap<>();
        results.forEach(n->{
            lineMap.putAll(n.getErrorConfigLines());
        });
        return lineMap;
    }

}
