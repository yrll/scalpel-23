package org.sng.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
        IPMETRO_NEW("ipmetro_new"),
        IPRAN("ipran"),
        IPRAN_NEW("ipran_new"),
        CLOUDNET("cloudnet"),
        CLOUDNET_NEW("cloudnet_new");

        private String name;

        NetworkType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static NetworkType getType(String name) {
            switch (name.toLowerCase()) {
                case "ipmetro":{
                    return IPMETRO;
                }
                case "ipmetro_new":{
                    return IPMETRO_NEW;
                }
                case "ipran":{
                    return IPRAN;
                }
                case "ipran_new":{
                    return IPRAN_NEW;
                }
                case "cloudnet":{
                    return CLOUDNET;
                }
                case "cloudnet_new":{
                    return CLOUDNET_NEW;
                }
            }
            return IPMETRO;
        }
    }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static String projectRootPath = System.getProperty("user.dir");

    private static List<String> ipmetroCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "4.1"));
    private static List<String> ipmetroCaseType2 = new ArrayList<>(Arrays.asList("2.3", "3.1"));
    private static List<String> ipranCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "2.4", "2.5"));
    private static List<String> cloudnetCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "1.4", "2.1", "2.2", "3.1", "4.1"));

    
    private static Map<String, String> errIpmetroDstNameMap = new HashMap<>();
    private static Map<String, String> errIpmetroDstIpMap = new HashMap<>();
    private static Map<String, String> corIpmetroDstNameMap = new HashMap<>();
    private static Map<String, String> corIpmetroDstIpMap = new HashMap<>();
    private static Map<String, String> errIpranDstNameMap = new HashMap<>();
    private static Map<String, String> errIpranDstIpMap = new HashMap<>();
    private static Map<String, String> errCloudnetDstNameMap = new HashMap<>();
    private static Map<String, String> errCloudnetDstIpMap = new HashMap<>();

    private static String relativeErrProvRootPath = "networks/provenanceInfo/";
    private static String relativePeerInfoRootPath = "networks/peerInfo/";
    private static String relativeConfigRootPath = "networks/config/";
    private static String relativeConditionRootPath = "sse_conditions/";
    private static String relativeVioRuleRootPath = "violated_rules/";
    private static String relativeLocalizeResultRootPath = "localize_results/";
    private static String relativeIgpResultRootPath = "igp_reqs/";
    private static String relativeSseProvRootPath = "sse_provenanceInfo/";

    //------------------------------------IPMETRO[ERROR]---------------------------------------------------
    private static String errIpmetroDstName1 = "BNG30";
    private static String errIpmetroDstIp1 = "179.0.0.117/30";

    private static String errIpmetroDstName2 = "BR4";
    private static String errIpmetroDstIp2 = "209.0.0.12/30";

    private static Set<String> errIpmetroSrcNameList1 = new HashSet<>(Arrays.asList("BNG1"));
    //------------------------------------IPMETRO[ERROR]---------------------------------------------------

    //------------------------------------IPMETRO[CORRECT]----------------------------------------------------
    private static String corIpmetroDstName1 = "BNG3";
    private static String corIpmetroDstIp1 = "179.0.0.9/30";

    private static String corIpmetroDstName2 = "BR3";
    private static String corIpmetroDstIp2 = "209.0.0.9/30";
    //-------------------------------------IPMETRO[CORRECT]---------------------------------------------------

    //------------------------------------IPMETRO_NEW[ERROR]---------------------------------------------------
    private static List<String> ipmetroNewCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "3.1"));
    private static List<String> ipmetroNewCaseType2 = new ArrayList<>(Arrays.asList("2.2"));
    private static List<String> ipmetroNewCaseType3 = new ArrayList<>(Arrays.asList("4.1"));

    private static String errIpmetroNewDstName1 = "BNG20";
    private static String errIpmetroNewDstIp1 = "173.0.0.77/32";

    private static Set<String> errIpmetroNewSrcNameList1 = new HashSet<>(Arrays.asList("BNG10"));

    private static String errIpmetroNewDstName2 = "BR2";
    private static String errIpmetroNewDstIp2 = "203.0.0.5/32";

    private static String errIpmetroNewDstIp3 = "20.20.20.20/32";
    //------------------------------------IPMETRO_NEW[ERROR]---------------------------------------------------

    //------------------------------------IPRAN[CORRECT]----------------------------------------------------
    private static String errIpranDstName1 = "CSG1-1-1";
    private static String errIpranDstIp1 = "191.0.0.0/30";

    private static Set<String> errIpranSrcNameList1 = new HashSet<>(Arrays.asList("RSG1"));
    //------------------------------------IPRAN[CORRECT]---------------------------------------------------

    //------------------------------------IPRAN_NEW[ERROR]----------------------------------------------------
    private static String errIpranNewDstName1 = "RSG3";
    private static String errIpranNewDstIp1 = "183.0.0.17/32";

    private static Set<String> errIpranNewSrcNameList1 = new HashSet<>(Arrays.asList("CSG1-1-1"));
    //------------------------------------IPRAN_NEW[ERROR]---------------------------------------------------

    //------------------------------------CLOUDNET[ERROR]---------------------------------------------------
    private static String errCloudnetDstName1 = "cloudPE-4";
    private static String errCloudnetDstIp1 = "50.0.0.13/32";

    private static Set<String> errCloudnetSrcNameList1 = new HashSet<>(Arrays.asList("U1-1-1-1"));
    //------------------------------------CLOUDNET[ERROR]---------------------------------------------------

    //------------------------------------CLOUDNET[CORRECT]---------------------------------------------------
    private static String corCloudnetDstName1 = "cloudPE-1";
    private static String corCloudnetDstIp1 = "90.0.0.1/32";

    private static Set<String> corCloudnetSrcNameList1 = new HashSet<>(Arrays.asList("U1-1-1-1"));
    //------------------------------------CLOUDNET[CORRECT]---------------------------------------------------
    

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

    public static Set<String> getRequirementSrcNodes(String keyString, NetworkType type) {
        switch (type) {
            case IPMETRO: {
                return errIpmetroSrcNameList1;
            }
            case IPRAN: {
                return errIpranSrcNameList1;
            }
            case IPRAN_NEW:{
                return errIpranNewSrcNameList1;
            }
            case CLOUDNET: {
                return errCloudnetSrcNameList1;
            }
            case IPMETRO_NEW:{
                return errIpmetroNewSrcNameList1;
            }
        }
        throw new IllegalArgumentException("Invalid network Type!");
    }

    public static String filterInvalidFilePath(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return filePath;
        } else {
            return "";
        }
    }

    // 待生成/写入的文件
    public String getIgpRequirementFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeIgpResultRootPath, concatFilePath(type.getName(), "case" + keyString +".json"));
        String filePath = concatFilePath(projectRootPath, relativePath);
        return filePath;
    }

    // 待生成/写入的文件
    public String getConditionFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeConditionRootPath, concatFilePath(type.getName(), "case" + keyString +".json"));
        String filePath = concatFilePath(projectRootPath, relativePath);
        return filePath;
    }

    // 待生成/写入的文件
    public String getResultFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeLocalizeResultRootPath, concatFilePath(type.getName(), "case" + keyString +".json"));
        String filePath = concatFilePath(projectRootPath, relativePath);
        return filePath;
    }

    // 待生成/写入的文件
    public String getPreResultFilePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeLocalizeResultRootPath, concatFilePath(type.getName(), "(pre)case" + keyString +".json"));
        String filePath = concatFilePath(projectRootPath, relativePath);
        return filePath;
    }

    // 待读取的目录
    public static String getCfgRootPath(String keyString, NetworkType type) {
        String bgpProvRootPath = concatFilePath(projectRootPath, relativeConfigRootPath + type.getName());
        String finalPath = concatFilePath(bgpProvRootPath, "case"+keyString);
        return finalPath;
    }

    // 待读取的文件
    public String getViolateRulePath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativeVioRuleRootPath, concatFilePath(type.getName(), "ViolatedRules_Case" + keyString +".json"));
        String filePath = concatFilePath(projectRootPath, relativePath);
        return filterInvalidFilePath(filePath);
    }

    // 待读取的文件
    public static String getErrorProvFilePath(String keyString, NetworkType type, String fileName) {
        String ProvRootPath = concatFilePath(projectRootPath, relativeErrProvRootPath + type.getName());
        String finalPath = concatFilePath(ProvRootPath, concatFilePath("case"+keyString, fileName));
        return filterInvalidFilePath(finalPath);
    }

    // 待读取的文件
    public static String getSseProvFilePath(String keyString, NetworkType type, String fileName) {
        String ProvRootPath = concatFilePath(projectRootPath, relativeSseProvRootPath + type.getName());
        String finalPath = concatFilePath(ProvRootPath, concatFilePath("case"+keyString, fileName));
        return filterInvalidFilePath(finalPath);
    }

    // 待读取的文件
    public static String getCorrectProvFilePath(String keyString, NetworkType type, String fileName) {
        String ProvRootPath = concatFilePath(projectRootPath, relativeErrProvRootPath + type.getName());
        String finalPath = concatFilePath(ProvRootPath, concatFilePath("case"+keyString, concatFilePath(KeyWord.CORRECT, fileName)));
        return filterInvalidFilePath(finalPath);
    }

    // 待读取的文件
    public String getPeerInfoPath(String keyString, NetworkType type) {
        String relativePath = concatFilePath(relativePeerInfoRootPath, concatFilePath(type.getName(), "PeerInfo" + keyString +".json"));
        String filePath = concatFilePath(projectRootPath, relativePath);
        return filterInvalidFilePath(filePath);
    }

    public String getErrorDstName(String keyString, NetworkType type) {
        switch (type) {
            case IPMETRO:{
                return errIpmetroDstNameMap.get(keyString);
            }
            case IPRAN:{
                return errIpranDstNameMap.get(keyString);
            }
            case IPRAN_NEW:{
                return errIpranNewDstName1;
            }
            case CLOUDNET:{
                return errCloudnetDstName1;
            }
            case IPMETRO_NEW:{
                if (ipmetroNewCaseType1.contains(keyString) || ipmetroNewCaseType3.contains(keyString)) {
                    return errIpmetroNewDstName1;
                }
                if (ipmetroNewCaseType2.contains(keyString)) {
                    return errIpmetroNewDstName2;
                }
            }
        }

        return "";
    }

    public String getErrorVpnName(NetworkType type) {
        if (type.equals(NetworkType.IPMETRO) || type.equals(NetworkType.IPMETRO_NEW)) {
            return KeyWord.PUBLIC_VPN_NAME;
        } else if (type.equals(NetworkType.IPRAN) || type.equals(NetworkType.IPRAN_NEW)){
            return "LTE_RAN";
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return "YiLiao";
        }
        return "";
    }

    public String getErrorDstIp(String keyString, NetworkType type) {
        switch (type) {
            case IPMETRO:{
                return errIpmetroDstIpMap.get(keyString);
            }
            case IPRAN:{
                return errIpranDstIpMap.get(keyString);
            }
            case IPRAN_NEW:{
                return errIpranNewDstIp1;
            }
            case CLOUDNET:{
                return errCloudnetDstIp1;
            }
            case IPMETRO_NEW:{
                if (ipmetroNewCaseType1.contains(keyString)) {
                    return errIpmetroNewDstIp1;
                }
                if (ipmetroNewCaseType2.contains(keyString)) {
                    return errIpmetroNewDstIp2;
                }
                if (ipmetroNewCaseType3.contains(keyString)) {
                    return errIpmetroNewDstIp3;
                }
            }
        }

        return "";
    }

    public String getCorrectDstName(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return corIpmetroDstNameMap.get(keyString);
        } else if (type.equals(NetworkType.IPRAN)){
            return "";
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return corCloudnetDstName1;
        }
        return "";
    }

    public String getCorrectDstIp(String keyString, NetworkType type) {
        if (type.equals(NetworkType.IPMETRO)) {
            return corIpmetroDstIpMap.get(keyString);
        } else if (type.equals(NetworkType.IPRAN)){
            return "";
        } else if (type.equals(NetworkType.CLOUDNET)) {
            return corCloudnetDstIp1;
        }
        return "";
    }
}
