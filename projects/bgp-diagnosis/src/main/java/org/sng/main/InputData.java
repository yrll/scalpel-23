package org.sng.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArchUtils;

public class InputData {
    private static String[] caseTypeList = {"1.1", "1.2", "1.3", "2.1", "2.2", "2.3", "3.1", "4.1"};
    private static Map<String, String> errDstNameMap = new HashMap<>();
    private static Map<String, String> errDstIpMap = new HashMap<>();
    private static Map<String, String> corDstNameMap = new HashMap<>();
    private static Map<String, String> corDstIpMap = new HashMap<>();

    private static String errDstName1 = "BNG30";
    private static String errDstIp1 = "179.0.0.117/30";

    private static String errDstName2 = "BR4";
    private static String errDstIp2 = "209.0.0.12/30";

    private static String corDstName1 = "BNG3";
    private static String corDstIp1 = "179.0.0.9/30";

    private static String corDstName2 = "BR3";
    private static String corDstIp2 = "209.0.0.9/30";

    public InputData() {
        errDstNameMap.put("1.1", errDstName1);
        errDstNameMap.put("1.2", errDstName1);
        errDstNameMap.put("1.3", errDstName1);
        errDstNameMap.put("2.1", errDstName1);
        errDstNameMap.put("4.1", errDstName1);
        errDstIpMap.put("1.1", errDstIp1);
        errDstIpMap.put("1.2", errDstIp1);
        errDstIpMap.put("1.3", errDstIp1);
        errDstIpMap.put("2.1", errDstIp1);
        errDstIpMap.put("4.1", errDstIp1);

        errDstNameMap.put("2.3", errDstName2);
        errDstNameMap.put("3.1", errDstName2);
        errDstIpMap.put("2.3", errDstIp2);
        errDstIpMap.put("3.1", errDstIp2);

        corDstNameMap.put("1.1", corDstName1);
        corDstNameMap.put("1.2", corDstName1);
        corDstNameMap.put("1.3", corDstName1);
        corDstNameMap.put("2.1", corDstName1);
        corDstNameMap.put("4.1", corDstName1);
        corDstIpMap.put("1.1", corDstIp1);
        corDstIpMap.put("1.2", corDstIp1);
        corDstIpMap.put("1.3", corDstIp1);
        corDstIpMap.put("2.1", corDstIp1);
        corDstIpMap.put("4.1", corDstIp1);

        corDstNameMap.put("2.3", corDstName2);
        corDstNameMap.put("3.1", corDstName2);
        corDstIpMap.put("2.3", corDstIp2);
        corDstIpMap.put("3.1", corDstIp2);
    }

    public String getErrorDstName(String keyString) {
        return errDstNameMap.get(keyString);
    }

    public String getErrorDstIp(String keyString) {
        return errDstIpMap.get(keyString);
    }

    public String getCorrectDstName(String keyString) {
        return corDstNameMap.get(keyString);
    }

    public String getCorrectDstIp(String keyString) {
        return corDstIpMap.get(keyString);
    }
}
