package org.sng.main.localization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sng.main.common.Interface;
import org.sng.main.common.LocalRoute;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

/*
 * Localize "violateRedis" errors (3 types):
 * ==> Why the target route can not be redistributed to target routing process?
 * 1) policy 【TODO 要不要把这个归类到forbidden的错里？】
 * 2) "No Redistribution Config"
 * 3) "inValid"
 */
public class RedistributionLocalizer implements Localizer{
    private String node;
    private LocalRoute targetRoute;
    private String[] causeKeyWords;
    private Interface inf;
    private static String splitSymbol = ",";
    private Violation violation;

    public enum RedisErrorType{
        NO_REDISTRIBUTE_COMMOND,
        ROUTE_INVALID,
        POLICY
    }

    private RedisErrorType getErrorTypeFromKeyWord(String causeKeyWord) {
        if (causeKeyWord.toLowerCase().contains("no")) {
            return RedisErrorType.NO_REDISTRIBUTE_COMMOND;
        } else if (causeKeyWord.toLowerCase().contains("invalid")) {
            return RedisErrorType.ROUTE_INVALID;
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

    public RedistributionLocalizer(String node, String causeKeyWord, LocalRoute route, Violation violation) {
        this.node = node;
        this.targetRoute = route;
        this.causeKeyWords = causeKeyWord.split(splitSymbol);
        this.inf = route.getInterface();
        this.violation = violation;
    }

    public RedistributionLocalizer(String node, String causeKeyWord, Interface iface, Violation violation) {
        this.node = node;
        this.inf = iface;
        this.causeKeyWords = causeKeyWord.split(splitSymbol);
        this.violation = violation;
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
                }
                case POLICY: lines.putAll(ConfigTaint.policyFinder(node, getPolicyName()));
                case ROUTE_INVALID: {
                    String[] routeKeyWords = {"route-static", targetRoute.getPrefix().getStartIp().toString(), 
                                                String.valueOf(targetRoute.getPrefix().getPrefixLength())};
                    lines.putAll(ConfigTaint.taint(node, routeKeyWords));
                }
            }
        });
        return lines;
    }
    
}
