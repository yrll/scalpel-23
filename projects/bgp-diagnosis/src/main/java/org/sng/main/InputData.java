package org.sng.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArchUtils;
import org.sng.main.util.KeyWord;

public class InputData {

    public enum NetworkType{
        BGP("bgp"),
        ISIS("isis");

        private final String _name;

        NetworkType(String str) {
            _name = str;
        }
    }

    // public enum NetworkType{
    //     IPMetro("ipmetro"),
    //     IPRAN("ipran"),
    //     CLOUDNET("cloudnet");

    //     private String name;

    //     NetworkType(String name) {
    //         this.name = name;
    //     }

    //     public String getName() {
    //         return name;
    //     }
    // }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static String projectRootPath = System.getProperty("user.dir");

    private static List<String> BgpListType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "4.1"));
    private static List<String> BgpListType2 = new ArrayList<>(Arrays.asList("2.3", "3.1"));
    private static List<String> IsisListType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2"));

    
    private static Map<String, String> errBgpDstNameMap = new HashMap<>();
    private static Map<String, String> errBgpDstIpMap = new HashMap<>();
    private static Map<String, String> corBgpDstNameMap = new HashMap<>();
    private static Map<String, String> corBgpDstIpMap = new HashMap<>();
    private static Map<String, String> errIsisDstNameMap = new HashMap<>();
    private static Map<String, String> errIsisDstIpMap = new HashMap<>();

    private static String relativeProvRootPath = "networks/provenanceInfo/";
    private static String relativePeerInfoRootPath = "networks/provenanceInfo/peerInfo/";
    private static String relativeConditionRootPath = "sse_conditions/";
    private static String relativeVioRuleRootPath = "violated_rules/";
    private static String relativeLocalizeResultRootPath = "localize_results/";
    private static String relativeIgpResultRootPath = "igp_reqs/";

    private static String errBgpDstName1 = "BNG30";
    private static String errBgpDstIp1 = "179.0.0.117/30";

    private static String errBgpDstName2 = "BR4";
    private static String errBgpDstIp2 = "209.0.0.12/30";

    private static String corBgpDstName1 = "BNG3";
    private static String corBgpDstIp1 = "179.0.0.9/30";

    private static String corBgpDstName2 = "BR3";
    private static String corBgpDstIp2 = "209.0.0.9/30";

    private static String errIsisDstName1 = "CSG1-2-1";
    private static String errIsisDstIp1 = "192.0.0.20/30";
    public InputData() {
        BgpListType1.forEach(t->{
            errBgpDstNameMap.put(t, errBgpDstName1);
            errBgpDstIpMap.put(t, errBgpDstIp1);
        });
        BgpListType2.forEach(t->{
            errBgpDstNameMap.put(t, errBgpDstName2);
            errBgpDstIpMap.put(t, errBgpDstIp2);
        });
        
        BgpListType1.forEach(t->{
            corBgpDstNameMap.put(t, corBgpDstName1);
            corBgpDstIpMap.put(t, corBgpDstIp1);
        });
        BgpListType2.forEach(t->{
            corBgpDstNameMap.put(t, corBgpDstName2);
            corBgpDstIpMap.put(t, corBgpDstIp2);
        });
        IsisListType1.forEach(t->{
            errIsisDstNameMap.put(t, errIsisDstName1);
            errIsisDstIpMap.put(t, errIsisDstIp1);
        });

    }

    public String getIgpRequirementFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeIgpResultRootPath, concatFilePath(type.name(), "case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getConditionFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeConditionRootPath, concatFilePath(type.name(), "case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getResultFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeLocalizeResultRootPath, concatFilePath(type.name(), "case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getPreResultFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeLocalizeResultRootPath, concatFilePath(type.name(), "(pre)case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public static String getCfgRootPath(String keyString, NetworkType type) {
        String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
        String finalPath = concatFilePath(bgpProvRootPath, "case"+keyString);
        return finalPath;
    }

    public String getViolateRulePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeVioRuleRootPath, concatFilePath(type.name(), "ViolatedRules_Case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);

    }


    public static String getErrorProvFilePath(String keyString, NetworkType type, String fileName) {
        if (type.equals(NetworkType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.ERROR, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+type.name());
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public static String getRepairProvFilePath(String keyString, NetworkType type, String fileName) {
        if (type.equals(NetworkType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.REPAIRED, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+type.name());
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public static String getCorrectProvFilePath(String keyString, NetworkType type, String fileName) {
        if (type.equals(NetworkType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.CORRECT, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public String getPeerInfoPath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativePeerInfoRootPath, concatFilePath(type.name(), "PeerInfo" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getErrorDstName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.BGP)) {
            return errBgpDstNameMap.get(keyString);
        } else {
            return errIsisDstNameMap.get(keyString);
        }
    }

    public String getErrorVpnName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.BGP)) {
            return KeyWord.PUBLIC_VPN_NAME;
        } else {
            return "LTE_RAN";
        }
    }

    public String getErrorDstIp(String keyString, NetworkType type) {
        if (type.equals(NetworkType.BGP)) {
            return errBgpDstIpMap.get(keyString);
        } else {
            return errIsisDstIpMap.get(keyString);
        }  
    }

    public String getCorrectDstName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.BGP)) {
            return corBgpDstNameMap.get(keyString);
        } else {
            return null;
        }
    }

    public String getCorrectDstIp(String keyString, NetworkType type) {
        if (type.equals(NetworkType.BGP)) {
            return corBgpDstIpMap.get(keyString);
        } else {
            return null;
        }
    }
}
