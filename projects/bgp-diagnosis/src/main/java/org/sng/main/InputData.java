package org.sng.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArchUtils;
import org.sng.main.util.KeyWord;

public class InputData {

    public enum ErrorType{
        BGP("bgp"),
        ISIS("isis");

        private final String _name;

        ErrorType(String str) {
            _name = str;
        }
    }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static String projectRootPath = System.getProperty("user.dir");

    private static List<String> BgpListType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "4.1"));
    private static List<String> BgpListType2 = new ArrayList<>(Arrays.asList("2.3", "3.1", "4.1"));
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

    public String getConditionFilePath(String keyString, ErrorType type) {
        String relativePath = concatFilePath(relativeConditionRootPath, concatFilePath(type.name(), "case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getResultFilePath(String keyString, ErrorType type) {
        String relativePath = concatFilePath(relativeLocalizeResultRootPath, concatFilePath(type.name(), "case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public static String getCfgRootPath(String keyString, ErrorType type) {
        String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
        String finalPath = concatFilePath(bgpProvRootPath, "case"+keyString);
        return finalPath;
    }

    public String getViolateRulePath(String keyString, ErrorType type) {
        String relativePath = concatFilePath(relativeVioRuleRootPath, concatFilePath(type.name(), "ViolatedRules_Case" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);

    }


    public String getErrorProvFilePath(String keyString, ErrorType type, String fileName) {
        if (type.equals(ErrorType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.ERROR, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+type.name());
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public String getCorrectProvFilePath(String keyString, ErrorType type, String fileName) {
        if (type.equals(ErrorType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.CORRECT, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath + type.name());
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public String getPeerInfoPath(String keyString, ErrorType type) {
        String relativePath = concatFilePath(relativePeerInfoRootPath, concatFilePath(type.name(), "PeerInfo" + keyString +".json"));
        return concatFilePath(projectRootPath, relativePath);
    }

    public String getErrorDstName(String keyString, ErrorType type) {
        if (type.equals(ErrorType.BGP)) {
            return errBgpDstNameMap.get(keyString);
        } else {
            return errIsisDstNameMap.get(keyString);
        }
        
    }

    public String getErrorDstIp(String keyString, ErrorType type) {
        if (type.equals(ErrorType.BGP)) {
            return errBgpDstIpMap.get(keyString);
        } else {
            return errIsisDstIpMap.get(keyString);
        }  
    }

    public String getCorrectDstName(String keyString, ErrorType type) {
        if (type.equals(ErrorType.BGP)) {
            return corBgpDstNameMap.get(keyString);
        } else {
            return null;
        }
    }

    public String getCorrectDstIp(String keyString, ErrorType type) {
        if (type.equals(ErrorType.BGP)) {
            return corBgpDstIpMap.get(keyString);
        } else {
            return null;
        }
    }
}
