package org.sng.main.localization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.main.common.BgpTopology;
import org.sng.main.common.Interface;
import org.sng.main.common.Layer2Topology;
import org.sng.main.common.StaticRoute;
import org.sng.main.util.ConfigTaint;

/*
 * Localize "violateRedis" errors (3 types):
 * ==> Why the target route can not be redistributed to target routing process?
 * 1) policy 【TODO 要不要把这个归类到forbidden的错里？】
 * 2) "No Redistribution Config"
 * 3) "inValid"
 */
public class RedistributionLocalizer implements Localizer{
    private String node;
    private StaticRoute targetRoute;
    private String[] causeKeyWords;
    private Interface inf;
    private static String splitSymbol = ",";
    private Violation violation;
    private BgpTopology bgpTopology;
    private Layer2Topology layer2Topology;

    public enum RedisErrorType{
        NO_REDISTRIBUTE_COMMOND,
        ROUTE_INVALID,
        POLICY,
        NOT_BEST
    }

    private RedisErrorType getErrorTypeFromKeyWord(String causeKeyWord) {
        causeKeyWord = causeKeyWord.toLowerCase();
        if (causeKeyWord.contains("no") && causeKeyWord.contains("config")) {
            return RedisErrorType.NO_REDISTRIBUTE_COMMOND;
        } else if (causeKeyWord.contains("invalid") || causeKeyWord.contains("invaild")) {
            return RedisErrorType.ROUTE_INVALID;
        } else if (causeKeyWord.contains("best")) {
            return RedisErrorType.NOT_BEST;
        } else {
            return RedisErrorType.POLICY;
        }
    }

    public String getPolicyName() {
        for (String word : causeKeyWords) {
            if (getErrorTypeFromKeyWord(word).equals(RedisErrorType.POLICY)) {
                return word;
            }
        }
        return null;
    }

    public RedistributionLocalizer(String node, String causeKeyWord, StaticRoute route, Violation violation, Layer2Topology layer2Topology) {
        this.node = node;
        this.targetRoute = route;
        this.causeKeyWords = causeKeyWord.split(splitSymbol);
        this.inf = route.getInterface();
        this.violation = violation;
        this.layer2Topology = layer2Topology;
    }


    public List<RedisErrorType> getErrorTypes() {
        List<RedisErrorType> errList = new ArrayList<>();
        for (String word : causeKeyWords) {
            errList.add(getErrorTypeFromKeyWord(word));
        }
        return errList;
    }


    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO Auto-generated method stub
        List<RedisErrorType> errorTypes = getErrorTypes();
        Map<Integer, String> lines = new HashMap<>();
        errorTypes.forEach(n->{
            switch (n) {
                case NO_REDISTRIBUTE_COMMOND: {
                    String netCommond = "network " + targetRoute.getPrefix().getStartIp() + " " + targetRoute.getPrefix().getPrefixLength();
                    lines.put(violation.getMissingLine(), netCommond);
                    break;
                }
                case POLICY: {
                    // TODO 先找到调用ref policy的那一行
                    String[] keWords = {"import", getPolicyName()};
                    lines.putAll(ConfigTaint.taintWithForbidWord(node, keWords, "peer"));
                    lines.putAll(ConfigTaint.policyLinesFinder(node, getPolicyName()));
                    break;
                }
                case ROUTE_INVALID: {
                    // 判断是接口invalid还是下一跳ip无接口
                    boolean ifRouteHasOrigin = false;
                    if (targetRoute.getInterface()!=null) {
                        Map<Integer, String> infLines = ConfigTaint.interfaceLinesFinder(node, targetRoute.getInterface());
                        if (infLines!=null && infLines.size()>0) {
                            lines.putAll(infLines);
                            ifRouteHasOrigin = true;
                        }

                    } else {
                        String[] routeKeyWords = {"route-static", targetRoute.getPrefix().getStartIp().toString(),
                                String.valueOf(targetRoute.getPrefix().getPrefixLength())};
                        Map<Integer, String> routeLines = ConfigTaint.staticRouteLinesFinder(node, targetRoute.getPrefix());
                        if (routeLines!=null && routeLines.size()>0) {
                            lines.putAll(routeLines);
                            ifRouteHasOrigin = true;
                        }

                    }
                    if (!ifRouteHasOrigin) {

                        String missingOriginLine = ConfigTaint.genStaticRouteLine(targetRoute);
                        lines.put(violation.getMissingLine(), missingOriginLine);
                        lines.put(violation.getMissingLine(), ConfigTaint.genMissingNetworkConfigLine(targetRoute.getPrefix()));
                    }
                    break;

                }
                case NOT_BEST:{
                    lines.putAll(ConfigTaint.staticRouteLinesFinder(node, targetRoute.getPrefix()));
                    lines.putAll(ConfigTaint.interfaceLinesFinder(node, layer2Topology.getIpLocatedInterface(node, targetRoute.getPrefixString())));
                }
            }
        });
        return lines;
    }
    
}
