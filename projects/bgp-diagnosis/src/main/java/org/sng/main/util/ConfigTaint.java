package org.sng.main.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sng.datamodel.Prefix;
import org.sng.datamodel.Ip;
import org.sng.main.BgpDiagnosis;
import org.sng.main.common.StaticRoute;

public class ConfigTaint {

    public static StaticRoute staticRouteFinder(String filePath, StaticRoute route) {
        // filePath是cfg文件地址，keyWords是静态路由相关的
        // 直接在入参的route上了
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            String targetLine = "";
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                // ifThisLine = true;
                line = line.strip();
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

    public static Map<Integer, String> staticRouteLinesFinder(String node, Prefix prefix) {
        // filePath是cfg文件地址，keyWords是静态路由相关的
        // 直接在入参的route上了
        Map<Integer, String> lineMap = new HashMap<>();
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                // ifThisLine = true;
                line = line.strip();
                if (line.startsWith(KeyWord.IP_STATIC)) {
                    if (ifLineContaintsPrefix(line, prefix)) {
                        lineMap.put(lineNum, line);
                    }
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
    
    public static Map<Integer, String> policyLinesFinder(String node, String policyName) {

        String fileName = BgpDiagnosis.cfgPathMap.get(node);//fileName改成自己存放配置的目录
        System.out.println("filename:"+fileName);

        String route_policy = "route-policy "+policyName;
        System.out.println("route-policy:"+route_policy);

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            String tempString = null;
            int line = 1;
            boolean flag = false;
            Map<Integer,String> route_policy_map = new LinkedHashMap<>();
            ArrayList<String> if_match_list = new ArrayList<String>();
            while ((tempString = reader.readLine()) != null) {
                tempString = tempString.trim();
                if(tempString.startsWith(route_policy)) {//输出route-policy那行
                    System.out.println("line " + line + ": " + tempString);
                    route_policy_map.put(line,tempString);
                    flag = true;
                }
                else {
                    if(flag && tempString.equals("#")){//输出到#那行
//                        System.out.println("line " + line + ": " + tempString);
//                        route_policy_map.put(line,tempString);
                        flag = false;
                    }else if(flag){//#输出route-policy到#之间的内容
                        System.out.println("line " + line + ": " + tempString);
                        route_policy_map.put(line,tempString);
                        if(tempString.startsWith("if-match")){
                            tempString = tempString.replaceAll("if-match","ip");
                            if_match_list.add(tempString);
                        }
                    }
                    else{
                        String modified_tempString = tempString;
                        if(tempString.contains("basic")){
                            modified_tempString = tempString.replace("basic ","");
                        }
                        else if(tempString.contains("advanced")){
                            modified_tempString = tempString.replace("advanced ","");
                        }
                        for(String keyword:if_match_list){
                            if(modified_tempString.startsWith(keyword)){
                                System.out.println("line " + line + ": " + tempString);
                                route_policy_map.put(line,tempString);
                                break;
                            }

                        }
                    }
                }
                line++;
            }
//            System.out.println(route_policy_map);
            reader.close();
            return route_policy_map;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return new LinkedHashMap<Integer, String>();
    }

    public static boolean ifLineContaintsPrefix(String line, Prefix prefix) {
        String[] words = line.split(" ");

        for (int i=0; i<words.length; i+=1) {
            String ss = words[i];
            if (Ip.isIpv4Addr(words[i])) {
                // 只识别遇到的第一个ip地址
                Ip ip = Ip.parse(words[i]);
                int maskLen = 0;
                if (words.length>i+1) {
                    int realLen = Integer.valueOf(words[i+1]);
                    if (realLen>=0 && realLen<=32) {
                        maskLen = realLen;
                    }
                }
                Prefix thisPrefix = Prefix.create(ip, maskLen);
                return thisPrefix.equals(prefix);

            }
        }
        return false;
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
            String line = reader.readLine().strip();
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                boolean ifThisLine = true;
                for (String word : keyWords) {
                    if (!line.contains(word.strip())) {
                        ifThisLine = false;
                    }
                }
                if (ifThisLine) {
                    lineMap.put(lineNum, line.strip());
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

    public static Map<Integer, String> taintWithForbidWord(String node, String[] keyWords, String forbidWord) {
        Map<Integer, String> lineMap = new HashMap<>();
        BufferedReader reader;
        String filePath = BgpDiagnosis.cfgPathMap.get(node);

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                boolean ifThisLine = false;
                String[] lineWords = line.split(" ");
                if (ifLineContaintsAllWords(line, keyWords) && !line.contains(forbidWord)) {
                    ifThisLine = true;
                } 
                
                if (ifThisLine) {
                    lineMap.put(lineNum, line.strip());
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


    public static Map<Integer, String> peerTaint(String node, String[] keyWords) {
        Map<Integer, String> lineMap = new HashMap<>();
        // 检查关键词前两位是否是peer和ip地址, 固定index 0是peer关键字, index 1是peer ip
        if (keyWords.length<2 || !keyWords[0].equals("peer") || !Ip.isIpv4Addr(keyWords[1])) {
            return lineMap;
        }

        BufferedReader reader;
        String filePath = BgpDiagnosis.cfgPathMap.get(node);

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                // read next line
                line = line.strip();
                boolean ifThisLine = true;
                if (line.contains(keyWords[0]) && line.contains(keyWords[1])) {
                    if (ifLineContaintsAllWords(line, keyWords)) {
                        lineMap.put(lineNum, line);
                    } else if (line.contains("group")) {
                        // 获取group的名称
                        String[] lineWords = line.split(" ");
                        String groupName = lineWords[lineWords.length-1];
                        String[] groupTargetWords = keyWords;
                        groupTargetWords[1] = groupName;
                        Map<Integer, String> groupLines = taint(node, groupTargetWords);
                        lineMap.putAll(groupLines);
                    }
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

    public static boolean ifLineContaintsAllWords(String line, String[] words) {
        for (String string : words) {
            if (!line.contains(string)) {
                return false;
            }
        }
        return true;
    }


}
