package org.sng.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.sng.main.common.BgpTopology;
import org.sng.main.util.KeyWord;

public class InputData {

    /* 带fail表示可靠性网例，不带表示可达性；
     * new表示不含标准修复方案，不带new表示含标准修复
     * */
    public enum NetworkType{
        IPMETRO("ipmetro"),
        IPMETRO_NEW("ipmetro_new"),
        IPMETRO_FAIL("ipmetro_fail"),
        IPMETRO_FAIL_NEW("ipmetro_fail_new"),
        IPRAN("ipran"),
        IPRAN_NEW("ipran_new"),
        IPRAN_FAIL("ipran_fail"),
        IPRAN_FAIL_NEW("ipran_fail_new"),
        CLOUDNET("cloudnet"),
//        CLOUDNET_NEW("cloudnet_new"),
        CLOUDNET_FAIL("cloudnet_fail"),
        CLOUDNET_FAIL_NEW("cloudnet_fail_new");

        private final String name;

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
                case "ipmetro_fail":{
                    return IPMETRO_FAIL;
                }
                case "ipmetro_fail_new":{
                    return IPMETRO_FAIL_NEW;
                }
                case "ipran":{
                    return IPRAN;
                }
                case "ipran_new":{
                    return IPRAN_NEW;
                }
                case "ipran_fail":{
                    return IPRAN_FAIL;
                }
                case "ipran_fail_new":{
                    return IPRAN_FAIL_NEW;
                }
                case "cloudnet":{
                    return CLOUDNET;
                }
                case "cloudnet_fail":{
                    return CLOUDNET_FAIL;
                }
                case "cloudnet_fail_new":{
                    return CLOUDNET_FAIL_NEW;
                }
//                case "cloudnet_new":{
//                    return CLOUDNET_NEW;
//                }
            }
            return IPMETRO;
        }
    }

    public static String concatFilePath(String rootPath, String sub) {
        return rootPath + "/" + sub;
    }

    public static String projectRootPath = System.getProperty("user.dir");

    private static final List<String> ipmetroCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "4.1"));
    private static final List<String> ipmetroCaseType2 = new ArrayList<>(Arrays.asList("2.3", "3.1"));
    private static final List<String> ipranCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "2.2", "2.4", "2.5"));
    private static final List<String> cloudnetCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "1.4", "2.1", "2.2", "3.1", "4.1"));

    
    private static final Map<String, String> errIpmetroDstNameMap = new HashMap<>();
    private static final Map<String, String> errIpmetroDstIpMap = new HashMap<>();
    private static final Map<String, String> corIpmetroDstNameMap = new HashMap<>();
    private static final Map<String, String> corIpmetroDstIpMap = new HashMap<>();
    private static final Map<String, String> errIpranDstNameMap = new HashMap<>();
    private static final Map<String, String> errIpranDstIpMap = new HashMap<>();
    private static final Map<String, String> errCloudnetDstNameMap = new HashMap<>();
    private static final Map<String, String> errCloudnetDstIpMap = new HashMap<>();

    private static final String relativeErrProvRootPath = "networks/provenanceInfo/";
    private static final String relativePeerInfoRootPath = "networks/peerInfo/";
    private static final String relativeConfigRootPath = "networks/config/";
    private static final String relativeConditionRootPath = "sse_conditions/";
    private static final String relativeVioRuleRootPath = "violated_rules/";
    private static final String relativeLocalizeResultRootPath = "localize_results/";
    private static final String relativeIgpResultRootPath = "igp_reqs/";
    private static final String relativeSseProvRootPath = "sse_provenanceInfo/";

    //------------------------------------IPMETRO[ERROR]---------------------------------------------------
    private static final String errIpmetroDstName1 = "BNG30";
    private static final String errIpmetroDstIp1 = "179.0.0.117/30";

    private static final String errIpmetroDstName2 = "BR4";
    private static final String errIpmetroDstIp2 = "209.0.0.12/30";

    private static final Set<String> errIpmetroSrcNameList1 = new HashSet<>(Arrays.asList("BNG1"));
    //------------------------------------IPMETRO[ERROR]---------------------------------------------------

    //------------------------------------IPMETRO[CORRECT]----------------------------------------------------
    private static final String corIpmetroDstName1 = "BNG3";
    private static final String corIpmetroDstIp1 = "179.0.0.9/30";

    private static final String corIpmetroDstName2 = "BR3";
    private static final String corIpmetroDstIp2 = "209.0.0.9/30";
    //-------------------------------------IPMETRO[CORRECT]---------------------------------------------------

    //------------------------------------IPMETRO_NEW[ERROR]---------------------------------------------------
    private static final List<String> ipmetroNewCaseType1 = new ArrayList<>(Arrays.asList("1.1", "1.2", "1.3", "2.1", "3.1"));
    private static final List<String> ipmetroNewCaseType2 = new ArrayList<>(Arrays.asList("2.2"));
    private static final List<String> ipmetroNewCaseType3 = new ArrayList<>(Arrays.asList("4.1"));

    private static final String errIpmetroNewDstName1 = "BNG20";
    private static final String errIpmetroNewDstIp1 = "173.0.0.77/32";

    private static final Set<String> errIpmetroNewSrcNameList1 = new HashSet<>(Arrays.asList("BNG10"));

    private static final String errIpmetroNewDstName2 = "BR2";
    private static final String errIpmetroNewDstIp2 = "203.0.0.5/32";

    private static final String errIpmetroNewDstIp3 = "20.20.20.20/32";
    //------------------------------------IPMETRO_NEW[ERROR]---------------------------------------------------

    //------------------------------------IPMETRO_FAIL[ERROR]---------------------------------------------------
    private static final List<String> ipmetroFailCaseType1 = new ArrayList<>(Arrays.asList("1.1"));
    private static final List<String> ipmetroFailCaseType2 = new ArrayList<>(Arrays.asList("1.2", "1.3"));

    private static final String errIpmetroFailDstName1 = "BNG2";
    private static final String errIpmetroFailDstIp1 = "10.10.10.10/32";

    private static final Set<String> errIpmetroFailSrcNameList1 = new HashSet<>(Arrays.asList("BNG10"));

    private static final String errIpmetroFailDstIp2 = "174.0.0.1/32";
    //------------------------------------IPMETRO_FAIL[ERROR]---------------------------------------------------

    //------------------------------------IPMETRO_FAIL_NEW[ERROR]---------------------------------------------------
    private static final String errIpmetroFailNewDstName1 = "BNG2";
    private static final String errIpmetroFailNewDstIp1 = "10.10.10.10/32";

    private static final Set<String> errIpmetroFailNewSrcNameList1 = new HashSet<>(Arrays.asList("BNG10"));

    private static final String errIpmetroFailNewDstIp2 = "174.0.0.1/32";
    //------------------------------------IPMETRO_FAIL_NEW[ERROR]---------------------------------------------------

    //------------------------------------IPRAN[CORRECT]----------------------------------------------------
    private static final String errIpranDstName1 = "CSG1-1-1";
    private static final String errIpranDstIp1 = "191.0.0.0/30";

    private static final Set<String> errIpranSrcNameList1 = new HashSet<>(Arrays.asList("RSG1"));
    //------------------------------------IPRAN[CORRECT]---------------------------------------------------

    //------------------------------------IPRAN_NEW[ERROR]----------------------------------------------------
    private static final String errIpranNewDstName1 = "RSG3";
    private static final String errIpranNewDstIp1 = "183.0.0.17/32";

    private static final Set<String> errIpranNewSrcNameList1 = new HashSet<>(Arrays.asList("CSG1-1-1"));
    //------------------------------------IPRAN_NEW[ERROR]---------------------------------------------------

    //------------------------------------IPRAN_FAIL[ERROR]----------------------------------------------------
    private static final String errIpranFailDstName1 = "RSG2";
    private static final String errIpranFailDstIp1 = "174.0.0.9/32";

    private static final String errIpranFailDstName2 = "RSG4";

    private static final String errIpranFailDstIp3 = "50.0.0.9/32";

    private static final Set<String> errIpranFailSrcNameList1 = new HashSet<>(Arrays.asList("CSG1-1-1"));
    //------------------------------------IPRAN_FAIL[ERROR]---------------------------------------------------

    //------------------------------------IPRAN_FAIL_NEW[ERROR]----------------------------------------------------
    private static final String errIpranFailNewDstName1 = "RSG2";
    private static final String errIpranFailNewDstIp1 = "174.0.0.9/32";

    private static final String errIpranFailNewDstName2 = "RSG4";

    private static final Set<String> errIpranFailNewSrcNameList1 = new HashSet<>(Arrays.asList("CSG1-1-1"));
    //------------------------------------IPRAN_FAIL_NEW[ERROR]---------------------------------------------------


    //------------------------------------CLOUDNET[ERROR]---------------------------------------------------
    private static final String errCloudnetDstName1 = "cloudPE-4";
    private static final String errCloudnetDstIp1 = "50.0.0.13/32";

    private static final Set<String> errCloudnetSrcNameList1 = new HashSet<>(Arrays.asList("U1-1-1-1"));
    //------------------------------------CLOUDNET[ERROR]---------------------------------------------------

    //------------------------------------CLOUDNET_FAIL[ERROR]---------------------------------------------------
    private static final String errCloudnetFailDstName1 = "cloudPE-4";

    private static final String errCloudnetFailDstIp1 = "50.0.0.5/32";

    private static final String errCloudnetFailDstName2 = "cloudPE-2";

    private static final Set<String> errCloudnetFailSrcNameList1 = new HashSet<>(Arrays.asList("U1-1-1-1"));
    //------------------------------------CLOUDNET_FAIL[ERROR]---------------------------------------------------

    //------------------------------------CLOUDNET_FAIL_NEW[ERROR]---------------------------------------------------
    private static final String errCloudnetFailNewDstName1 = "cloudPE-4";

    private static final String errCloudnetFailNewDstIp1 = "50.0.0.5/32";


    private static final Set<String> errCloudnetFailNewSrcNameList1 = new HashSet<>(Arrays.asList("U1-1-1-1"));
    //------------------------------------CLOUDNET_FAIL_NEW[ERROR]---------------------------------------------------


    //------------------------------------CLOUDNET[CORRECT]---------------------------------------------------
    private static final String corCloudnetDstName1 = "cloudPE-1";
    private static final String corCloudnetDstIp1 = "90.0.0.1/32";

    private static final Set<String> corCloudnetSrcNameList1 = new HashSet<>(Arrays.asList("U1-1-1-1"));
    //------------------------------------CLOUDNET[CORRECT]---------------------------------------------------

    public String getBgpProvTemplateFilePath() {
        return concatFilePath(projectRootPath, "networks/provenanceInfo/template/provenanceInfo.json");
    }

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
            case IPMETRO_NEW:{
                return errIpmetroNewSrcNameList1;
            }
            case IPMETRO_FAIL:{
                return errIpmetroFailSrcNameList1;
            }
            case IPMETRO_FAIL_NEW:{
                return errIpmetroFailNewSrcNameList1;
            }
            case IPRAN: {
                return errIpranSrcNameList1;
            }
            case IPRAN_NEW:{
                return errIpranNewSrcNameList1;
            }
            case IPRAN_FAIL:{
                return errIpranFailSrcNameList1;
            }
            case IPRAN_FAIL_NEW:{
                return errIpranFailNewSrcNameList1;
            }
            case CLOUDNET: {
                return errCloudnetSrcNameList1;
            }
            case CLOUDNET_FAIL:{
                return errCloudnetFailSrcNameList1;
            }
            case CLOUDNET_FAIL_NEW:{
                return errCloudnetFailNewSrcNameList1;
            }

        }
        throw new IllegalArgumentException("Invalid network Type!");
    }

    public static Set<String> getFailedNodes(String keyString, NetworkType type) {
        switch (type) {
            case IPMETRO_FAIL:{
                if (keyString.contains("1")) {
                    return new HashSet<>(Arrays.asList("BNG1"));
                } else if (keyString.contains("2")) {
                    return new HashSet<>(Arrays.asList("BNG1"));
                } else if (keyString.contains("3")) {
                    return new HashSet<>(Arrays.asList("BNG1"));
                }
            }
            case IPMETRO_FAIL_NEW:{
                if (keyString.contains("1")) {
                    return new HashSet<>(Arrays.asList("BNG1"));
                } else if (keyString.contains("2")) {
                    return new HashSet<>(Arrays.asList("BNG1"));
                } else if (keyString.contains("3")) {
                    return new HashSet<>(Arrays.asList("BNG1"));
                }
            }

            case IPRAN_FAIL:{
                if (keyString.contains("1")) {
                    return new HashSet<>(Arrays.asList("ASG1"));
                } else if (keyString.contains("2")) {
                    return new HashSet<>(Arrays.asList("RSG2"));
                } else if (keyString.contains("3")) {
                    return new HashSet<>(Arrays.asList("RSG2"));
                }
            }
            case IPRAN_FAIL_NEW:{
                if (keyString.contains("1")) {
                    return new HashSet<>(Arrays.asList("P-RR1"));
                } else if (keyString.contains("2")) {
                    return new HashSet<>(Arrays.asList("RSG2"));
                } else if (keyString.contains("3")) {
                    return new HashSet<>(Arrays.asList("RSG2"));
                }
            }

            case CLOUDNET_FAIL:{
                if (keyString.contains("1")) {
                    return new HashSet<>(Arrays.asList("cloudPE-2"));
                } else if (keyString.contains("2")) {
                    return new HashSet<>(Arrays.asList("B1-1"));
                } else if (keyString.contains("3")) {
                    return new HashSet<>(Arrays.asList("UpperER-1"));
                }
            }
            case CLOUDNET_FAIL_NEW:{
                if (keyString.contains("1")) {
                    return new HashSet<>(Arrays.asList("cloudPE-2"));
                } else if (keyString.contains("2")) {
                    return new HashSet<>(Arrays.asList("cloudPE-2"));
                } else if (keyString.contains("3")) {
                    return new HashSet<>(Arrays.asList("cloudPE-2"));
                }
            }
            default:{
                return new HashSet<String>();
            }

        }
    }

    public static BgpTopology getRefBgpTopology(NetworkType networkType, Set<String> failedDevs) {
        String netName = networkType.getName();
        String filePath = "";
        if (netName.contains("ipmetro")) {
            filePath = "E:\\Java\\IdeaProjects\\scalpel-23\\networks\\peerInfo\\ipmetro\\PeerInfo.json";
        } else if (netName.contains("ipran")) {
            filePath = "E:\\Java\\IdeaProjects\\scalpel-23\\networks\\peerInfo\\ipran\\PeerInfo.json";
        } else if (netName.contains("cloudnet")) {
            filePath = "E:\\Java\\IdeaProjects\\scalpel-23\\networks\\peerInfo\\cloudnet\\PeerInfo.json";
        } else {
            return null;
        }

        BgpTopology bgpTopology = new BgpTopology(failedDevs);
        bgpTopology.genBgpPeersFromJsonFile(filePath);
        return bgpTopology;
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
            case IPMETRO_NEW:{
                if (ipmetroNewCaseType1.contains(keyString) || ipmetroNewCaseType3.contains(keyString)) {
                    return errIpmetroNewDstName1;
                }
                if (ipmetroNewCaseType2.contains(keyString)) {
                    return errIpmetroNewDstName2;
                }
            }
            case IPMETRO_FAIL:{
                return errIpmetroFailDstName1;
            }
            case IPMETRO_FAIL_NEW:{
                return errIpmetroFailDstName1;
            }
            case IPRAN:{
                return errIpranDstNameMap.get(keyString);
            }
            case IPRAN_NEW:{
                return errIpranNewDstName1;
            }
            case IPRAN_FAIL:{
                if (keyString.contains("1")) {
                    return errIpranFailDstName1;
                } else if (keyString.contains("2")) {
                    return errIpranFailDstName2;
                } else if (keyString.contains("3")) {
                    return errIpranFailDstName2;
                }
            }
            case IPRAN_FAIL_NEW:{
                if (keyString.contains("1")) {
                    return errIpranFailNewDstName1;
                } else if (keyString.contains("2")) {
                    return errIpranFailNewDstName2;
                } else if (keyString.contains("3")) {
                    return errIpranFailNewDstName2;
                }
            }
            case CLOUDNET:{
                return errCloudnetDstName1;
            }
            case CLOUDNET_FAIL:{
                if (keyString.contains("1")) {
                    return errCloudnetFailDstName1;
                } else if (keyString.contains("2")) {
                    return errCloudnetFailDstName2;
                } else if (keyString.contains("3")) {
                    return errCloudnetFailDstName2;
                }
            }
            case CLOUDNET_FAIL_NEW:{
                return errCloudnetFailNewDstName1;
            }

        }

        return "";
    }

    public String getErrorVpnName(NetworkType type) {
        if (type.equals(NetworkType.IPMETRO) || type.equals(NetworkType.IPMETRO_NEW)
            || type.equals(NetworkType.IPMETRO_FAIL) || type.equals(NetworkType.IPMETRO_FAIL_NEW)) {
            return KeyWord.PUBLIC_VPN_NAME;
        } else if (type.equals(NetworkType.IPRAN) || type.equals(NetworkType.IPRAN_NEW)
                    || type.equals(NetworkType.IPRAN_FAIL) || type.equals(NetworkType.IPRAN_FAIL_NEW)){
            return "LTE_RAN";
        } else if (type.equals(NetworkType.CLOUDNET) || type.equals(NetworkType.CLOUDNET_FAIL) || type.equals(NetworkType.CLOUDNET_FAIL_NEW)) {
            return "YiLiao";
        }
        return "";
    }

    public String getErrorDstIp(String keyString, NetworkType type) {
        switch (type) {
            case IPMETRO:{
                return errIpmetroDstIpMap.get(keyString);
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
            case IPMETRO_FAIL:{
                if (keyString.contains("1")) {
                    return errIpmetroFailDstIp1;
                } else if (keyString.contains("2")) {
                    return errIpmetroFailDstIp2;
                } else if (keyString.contains("3")) {
                    return errIpmetroFailDstIp2;
                }
            }
            case IPMETRO_FAIL_NEW:{
                if (keyString.contains("1")) {
                    return errIpmetroFailNewDstIp1;
                } else if (keyString.contains("2")) {
                    return errIpmetroFailNewDstIp2;
                } else if (keyString.contains("3")) {
                    return errIpmetroFailNewDstIp2;
                }
            }
            //---------------------
            case IPRAN:{
                return errIpranDstIpMap.get(keyString);
            }
            case IPRAN_NEW:{
                return errIpranNewDstIp1;
            }
            case IPRAN_FAIL:{
                if (keyString.contains("1")) {
                    return errIpranFailDstIp1;
                } else if (keyString.contains("2")) {
                    return errIpranFailDstIp1;
                } else if (keyString.contains("3")) {
                    return errIpranFailDstIp3;
                }
            }
            case IPRAN_FAIL_NEW:{
                return errIpranFailNewDstIp1;
            }
            //-------------------------
            case CLOUDNET:{
                return errCloudnetDstIp1;
            }
            case CLOUDNET_FAIL:{
                return errCloudnetFailDstIp1;
            }
            case CLOUDNET_FAIL_NEW:{
                return errCloudnetFailNewDstIp1;
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
