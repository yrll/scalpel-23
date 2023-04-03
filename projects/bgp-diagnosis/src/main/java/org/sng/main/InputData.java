package org.sng.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArchUtils;
import org.sng.main.util.KeyWord;

public class InputData {

    public enum ErrorType{
        BGP,
        ISIS,
    }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static String projectRootPath = System.getProperty("user.dir");
    private static String[] caseTypeList = {"1.1", "1.2", "1.3", "2.1", "2.2", "2.3", "3.1", "4.1"};
    
    private static Map<String, String> errBgpDstNameMap = new HashMap<>();
    private static Map<String, String> errBgpDstIpMap = new HashMap<>();
    private static Map<String, String> corBgpDstNameMap = new HashMap<>();
    private static Map<String, String> corBgpDstIpMap = new HashMap<>();

    private static Map<String, String> errIsisDstNameMap = new HashMap<>();
    private static Map<String, String> errIsisDstIpMap = new HashMap<>();

    private static String relativeProvRootPath = "networks/provenanceInfo/";
    private static String relativePeerInfoRootPath = "networks/provenanceInfo/peerInfo/";

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
        errBgpDstNameMap.put("1.1", errBgpDstName1);
        errBgpDstNameMap.put("1.2", errBgpDstName1);
        errBgpDstNameMap.put("1.3", errBgpDstName1);
        errBgpDstNameMap.put("2.1", errBgpDstName1);
        errBgpDstNameMap.put("4.1", errBgpDstName1);
        errBgpDstIpMap.put("1.1", errBgpDstIp1);
        errBgpDstIpMap.put("1.2", errBgpDstIp1);
        errBgpDstIpMap.put("1.3", errBgpDstIp1);
        errBgpDstIpMap.put("2.1", errBgpDstIp1);
        errBgpDstIpMap.put("4.1", errBgpDstIp1);

        errBgpDstNameMap.put("2.3", errBgpDstName2);
        errBgpDstNameMap.put("3.1", errBgpDstName2);
        errBgpDstIpMap.put("2.3", errBgpDstIp2);
        errBgpDstIpMap.put("3.1", errBgpDstIp2);

        corBgpDstNameMap.put("1.1", corBgpDstName1);
        corBgpDstNameMap.put("1.2", corBgpDstName1);
        corBgpDstNameMap.put("1.3", corBgpDstName1);
        corBgpDstNameMap.put("2.1", corBgpDstName1);
        corBgpDstNameMap.put("4.1", corBgpDstName1);
        corBgpDstIpMap.put("1.1", corBgpDstIp1);
        corBgpDstIpMap.put("1.2", corBgpDstIp1);
        corBgpDstIpMap.put("1.3", corBgpDstIp1);
        corBgpDstIpMap.put("2.1", corBgpDstIp1);
        corBgpDstIpMap.put("4.1", corBgpDstIp1);

        corBgpDstNameMap.put("2.3", corBgpDstName2);
        corBgpDstNameMap.put("3.1", corBgpDstName2);
        corBgpDstIpMap.put("2.3", corBgpDstIp2);
        corBgpDstIpMap.put("3.1", corBgpDstIp2);
    }

    public String getConditionFilePath(String keyString, ErrorType type) {
        if (type.equals(ErrorType.BGP)) {
            String relativePath = projectRootPath + "/sse_conditions/bgp";
            return concatFilePath(relativePath, "case" + keyString +".json");
        } else {
            String relativePath = projectRootPath + "sse_conditions/isis";
            return concatFilePath(relativePath, "case" + keyString +".json");
        }
    }

    public static String getCfgRootPath(String keyString, ErrorType type) {
        if (type.equals(ErrorType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+"bgp");
            String finalPath = concatFilePath(bgpProvRootPath, "case"+keyString);
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+"isis");
            String finalPath = concatFilePath(isisProvRootPath, "case"+keyString);
            return finalPath;
        }
    }


    public String getErrorProvFilePath(String keyString, ErrorType type, String fileName) {
        if (type.equals(ErrorType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+"bgp");
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.ERROR, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativePeerInfoRootPath+"isis");
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public String getCorrectProvFilePath(String keyString, ErrorType type, String fileName) {
        if (type.equals(ErrorType.BGP)) {
            String bgpProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+"bgp");
            String finalPath = concatFilePath(bgpProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.CORRECT, fileName)));
            return finalPath;
        } else {
            String isisProvRootPath = concatFilePath(projectRootPath, relativeProvRootPath+"isis");
            String finalPath = concatFilePath(isisProvRootPath, concatFilePath("case"+keyString, fileName));
            return finalPath;
        }
    }

    public String getPeerInfoPath(String keyString, ErrorType type) {
        String fileName = "PeerInfo"+keyString+".json";
        if (type.equals(ErrorType.BGP)) {
            return concatFilePath(projectRootPath, relativePeerInfoRootPath + "bgp/" + fileName);
            // Provenance Info files path
        } else {
            return concatFilePath(projectRootPath, relativePeerInfoRootPath + "isis/" + fileName);
        }
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
