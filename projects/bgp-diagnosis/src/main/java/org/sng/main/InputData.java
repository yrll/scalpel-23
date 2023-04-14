package org.sng.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArchUtils;
import org.sng.main.util.KeyWord;

public class InputData {

    // public enum NetworkType{
    //     BGP("bgp"),
    //     ISIS("isis");

    //     private final String _name;

    //     NetworkType(String str) {
    //         _name = str;
    //     }
    // }

    public enum NetworkType{
        IPMETRO("ipmetro"),
        IPRAN("ipran"),
        CLOUDNET("cloudnet");

        private String name;

        NetworkType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static String projectRootPath = System.getProperty("user.dir");

    private static List<String> ipmetroCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "4.1"));
    private static List<String> ipmetroCaseType2 = new ArrayList<>(Arrays.asList("2.3", "3.1"));
    private static List<String> ipranCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "2.4", "2.5"));

    
    private static Map<String, String> errIpmetroDstNameMap = new HashMap<>();
    private static Map<String, String> errIpmetroDstIpMap = new HashMap<>();
    private static Map<String, String> corIpmetroDstNameMap = new HashMap<>();
    private static Map<String, String> corIpmetroDstIpMap = new HashMap<>();
    private static Map<String, String> errIpranDstNameMap = new HashMap<>();
    private static Map<String, String> errIpranDstIpMap = new HashMap<>();

    private static String relativeErrProvRootPath = "networks/provenanceInfo/";
    private static String relativePeerInfoRootPath = "networks/peerInfo/";
    private static String relativeConfigRootPath = "networks/config/";
    private static String relativeConditionRootPath = "sse_conditions/";
    private static String relativeVioRuleRootPath = "violated_rules/";
    private static String relativeLocalizeResultRootPath = "localize_results/";
    private static String relativeIgpResultRootPath = "igp_reqs/";
    private static String relativeSseProvRootPath = "sse_provenanceInfo/";

    private static String errIpmetroDstName1 = "BNG30";
    private static String errIpmetroDstIp1 = "179.0.0.117/30";

    private static String errIpmetroDstName2 = "BR4";
    private static String errIpmetroDstIp2 = "209.0.0.12/30";

    private static String corIpmetroDstName1 = "BNG3";
    private static String corIpmetroDstIp1 = "179.0.0.9/30";

    private static String corIpmetroDstName2 = "BR3";
    private static String corIpmetroDstIp2 = "209.0.0.9/30";

    private static String errIpranDstName1 = "CSG1-1-1";
    private static String errIpranDstIp1 = "191.0.0.0/30";

    public InputData() {
        ipmetroCaseType1.forEach(t->{
            errIpmetroDstNameMap.put(t, errIpmetroDstName1);
            errIpmetroDstIpMap.put(t, errIpmetroDstIp1);
        });
        ipmetroCaseType2.forEach(t->{
            errIpmetroDstNameMap.put(t, errIpmetroDstName2);
            errIpmetroDstIpMap.put(t, errIpmetroDstIp2);
        });
        
        ipmetroCaseType1.forEach(t->{
            corIpmetroDstNameMap.put(t, corIpmetroDstName1);
            corIpmetroDstIpMap.put(t, corIpmetroDstIp1);
        });
        ipmetroCaseType2.forEach(t->{
            corIpmetroDstNameMap.put(t, corIpmetroDstName2);
            corIpmetroDstIpMap.put(t, corIpmetroDstIp2);
        });
        ipranCaseType1.forEach(t->{
            errIpranDstNameMap.put(t, errIpranDstName1);
            errIpranDstIpMap.put(t, errIpranDstIp1);
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
        String bgpProvRootPath = concatFilePath(projectRootPath, relativeConfigRootPath + type.name());
        String finalPath = concatFilePath(bgpProvRootPath, "case"+keyString);
        return finalPath;
    }

    public String getViolateRulePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeVioRuleRootPath, concatFilePath(type.name(), "ViolatedRules_Case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }


    public static String getErrorProvFilePath(String keyString, NetworkType type, String fileName) {
        String ProvRootPath = concatFilePath(projectRootPath, relativeErrProvRootPath + type.name());
        String finalPath = concatFilePath(ProvRootPath, concatFilePath("case"+keyString, fileName));
        return finalPath;
    }

    public static String getRepairProvFilePath(String keyString, NetworkType type, String fileName) {
        String ProvRootPath = concatFilePath(projectRootPath, relativeSseProvRootPath + type.name());
        String finalPath = concatFilePath(ProvRootPath, concatFilePath("case"+keyString, fileName));
        return finalPath;
    }

    public static String getCorrectProvFilePath(String keyString, NetworkType type, String fileName) {
        String ProvRootPath = concatFilePath(projectRootPath, relativeErrProvRootPath + type.name());
        String finalPath = concatFilePath(ProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.CORRECT, fileName)));
        return finalPath;
    }

    public String getPeerInfoPath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativePeerInfoRootPath, concatFilePath(type.name(), "PeerInfo" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getErrorDstName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return errIpmetroDstNameMap.get(keyString);
        } else if (type.equals(NetworkType.IPRAN)){
            return errIpranDstNameMap.get(keyString);
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return "";
        }
        return "";
    }

    public String getErrorVpnName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return KeyWord.PUBLIC_VPN_NAME;
        } else if (type.equals(NetworkType.IPRAN)){
            return "LTE_RAN";
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return "YiLiao";
        }
        return "";
    }

    public String getErrorDstIp(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return errIpmetroDstIpMap.get(keyString);
        } else if (type.equals(NetworkType.IPRAN)){
            return errIpranDstIpMap.get(keyString);
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return "";
        }
        return "";
    }

    public String getCorrectDstName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return corIpmetroDstNameMap.get(keyString);
        } else if (type.equals(NetworkType.IPRAN)){
            return "";
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return "";
        }
        return "";
    }

    public String getCorrectDstIp(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return corIpmetroDstIpMap.get(keyString);
        } else if (type.equals(NetworkType.IPRAN)){
            return "";
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return "";
        }
        return "";
    }
}
