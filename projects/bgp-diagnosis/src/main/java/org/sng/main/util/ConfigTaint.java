package org.sng.main.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.sng.datamodel.Prefix;
import org.sng.datamodel.Ip;
import org.sng.main.BgpDiagnosis;
import org.sng.main.common.LocalRoute;

public class ConfigTaint {

    public static LocalRoute staticRouteFinder(String filePath, LocalRoute route) {
        // filePath是cfg文件地址，keyWords是静态路由相关的
        // 直接在入参的route上了
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();
            String targetLine = "";
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                // ifThisLine = true;
                if (line.startsWith(KeyWord.IP_STATIC)) {
                    // 查询当前static route的前缀ip+mask是否和传入的route一致
                    String[] words = line.split(" ");
                    boolean ifFindTargetPrefix = false; // 标识是否读取过前缀，下一个匹配的ip才是下一跳
                    for (int i=0; i<words.length; i+=1) {
                        String ss = words[i];
                        if (Ip.isIpv4Addr(words[i]) && !ifFindTargetPrefix) {
                            Ip ip = Ip.parse(words[i]);
                            Prefix thisPrefix = Prefix.create(ip, Integer.valueOf(words[i+1]));
                            if (!thisPrefix.equals(route.getPrefix())) {
                                break;
                            }
                            ifFindTargetPrefix = true;
                            // 改route
                            route.setLineNum(lineNum);
                        }
                        if (words[i].equals(KeyWord.PREFERENCE)) {
                            // 改route
                            route.setPreference(Integer.valueOf(words[i+1]));
                            break;
                        }
                    }
                }
                line = reader.readLine();
                lineNum += 1;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return route;
    }
    
    public static Map<Integer, String> policyFinder(String node, String policyName) {
        return new HashMap<Integer, String>();
    }

    /*
     * 找到包含所有关键字的全部行
     */
    public static Map<Integer, String> taint(String node, String[] keyWords) {
        Map<Integer, String> lineMap = new HashMap<>();
        BufferedReader reader;
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                boolean ifThisLine = true;
                for (String word : keyWords) {
                    if (!line.contains(word)) {
                        ifThisLine = false;
                    }
                }
                if (ifThisLine) {
                    lineMap.put(lineNum, line);
                }
                line = reader.readLine();
                lineNum += 1;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lineMap;
    }
}
