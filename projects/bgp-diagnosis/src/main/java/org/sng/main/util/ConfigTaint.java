package org.sng.main.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sng.datamodel.Prefix;
import org.sng.datamodel.Ip;
import org.sng.main.BgpDiagnosis;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.Interface;
import org.sng.main.common.StaticRoute;
import org.sng.main.diagnosis.Generator;
import org.sng.main.diagnosis.VpnInstance;

public class ConfigTaint {

    public static StaticRoute staticRouteRefine(String node, StaticRoute route) {
        // filePath是cfg文件地址，keyWords是静态路由相关的
        // 直接在入参的route上了
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            String targetLine = "";
            int lineNum = 1;
            while (line != null) {
                // System.out.println(line);
                line = line.strip();
                if (line.startsWith(KeyWord.IP_STATIC)) {
                    // 1. 检查vpn是否匹配
                    if (!route.getVpnName().equals(KeyWord.PUBLIC_VPN_NAME)) {
                        if (!line.contains(route.getVpnName())) {
                            continue;
                        }
                    }
                    // 2. 查询当前static route的前缀ip+mask是否和传入的route一致
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

    // 不包括找默认路由
    public static StaticRoute staticRouteFinder(String node, Prefix prefix, boolean strictMatch) {
        // filePath是cfg文件地址，keyWords是静态路由相关的
        // 直接在入参的route上了
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
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
                    String vpnName = KeyWord.PUBLIC_VPN_NAME;
                    for (int i=0; i<words.length; i+=1) {
                        // 检测某一行静态路由
                        String ss = words[i];
                        if (words[i].contains("vpn")) {
                            vpnName = words[i+1];
                        }
                        if (Ip.isIpv4Addr(words[i]) && !ifFindTargetPrefix) {
                            Ip ip = Ip.parse(words[i]);
                            Prefix thisPrefix = Prefix.create(ip, Integer.valueOf(words[i+1]));
                            if (thisPrefix.equals(Prefix.ZERO)) {
                                // 不考虑默认路由
                                break;
                            }
                            if (strictMatch) {
                                // 严格匹配，连掩码都要一致，不匹配则跳出
                                if (!thisPrefix.equals(prefix)) {
                                    break;
                                }
                            } else {
                                // 非严格匹配，但是不考虑默认路由，
                                if (!thisPrefix.containsPrefix(prefix)) {
                                    break;
                                }
                            }

                            ifFindTargetPrefix = true;
                            StaticRoute targetRoute = new StaticRoute(node, vpnName, thisPrefix.toString(), words[i+2]);
                            for (int j=i+1; j<words.length; j++) {
                                if (words[j].equals(KeyWord.PREFERENCE)) {
                                    targetRoute.setPreference(Integer.parseInt(words[j+1]));
                                }
                            }
                            return targetRoute;
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
        return null;
    }

    public static String genNetworkCommondLine(Prefix prefix) {
        return "network " + prefix.getStartIp().toString() + " " + prefix.getPrefixLength();
    }

    public static String genStaticRouteLine(StaticRoute staticRoute) {
        String vpnName = staticRoute.getVpnName();
        String nextHop = staticRoute.getNextHopString();
        String prefixString = staticRoute.getPrefixString();
        int preference = staticRoute.getPref();
        if (prefixString==null) {
            return null;
        }
        Prefix prefix = Prefix.parse(prefixString);
        prefixString = BgpTopology.transPrefixOrIpToIpString(prefixString);
        int maskLen = prefix.getPrefixLength();

        if (vpnName.equals(KeyWord.PUBLIC_VPN_NAME)) {
            vpnName = "";
        } else {
            vpnName = "vpn-instance " + vpnName;
        }
        if (preference != Generator.Protocol.STATIC.getPreference()) {
            return "ip route-static " + vpnName + " " + prefixString + " " + maskLen + " " + nextHop + " preference " + preference;
        } else {
            return "ip route-static " + vpnName + " " + prefixString + " " + maskLen + " " + nextHop;
        }
    }

    public static Map<Integer, String> interfaceLinesFinder(String node, Interface targetInterface) {
        Map<Integer, String> lineMap = new HashMap<>();
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            int lineNum = 1;
            boolean reachTargetLine = false;
            while (line != null) {
                if (line.contains(KeyWord.ENDING_TOKEN)) {
                    reachTargetLine = false;
                }
                if (reachTargetLine) {
                    lineMap.put(lineNum, line);
                }

                if (line.startsWith(KeyWord.INTERFACE)) {
                    String thisInfName = line.split(" ")[1];
                    // 输入的接口名字containts当前遍历的接口名字，需要把父接口也找到，为了避开其他子接口要排除包含“.”的名字

                    if (targetInterface.getInfName().equals(thisInfName) || targetInterface.getInfName().contains(thisInfName) && !thisInfName.contains(".")) {
                        lineMap.put(lineNum, line);
                        reachTargetLine = true;
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

    // 查找 peer ip [keyword] 语句是否存在，遇到group可以迭代找到相关语句
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
                        String[] groupTargetWords = keyWords.clone();
                        groupTargetWords[1] = groupName;
                        // 把ref peer groupd 那行也加进来
                        lineMap.put(lineNum, line);
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

    // 获取 bgp 相应地址镞的preference值设置(单播、vpn实例)
    public static List<Integer> getBgpIpv4Preference(String node, String vpnName) {
        BufferedReader reader;
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
        String startKeyWord = KeyWord.IPV4_FAMILY;
        if (vpnName.equals(KeyWord.PUBLIC_VPN_NAME)) {
            vpnName = KeyWord.UNICAST;       
        }
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            boolean ifReachTargetVpn = false;
            while (line != null && !ifReachTargetVpn) {
                // System.out.println(line);
                // read next line
                line = line.strip();
                if (!ifReachTargetVpn && line.startsWith(startKeyWord)) {
                    if (line.contains(vpnName)) {
                        ifReachTargetVpn = true;
                    }
                }
                if (ifReachTargetVpn && line.contains(KeyWord.PREFERENCE)) {
                    String[] words = line.split(" ");
                    if (words.length!=4) {
                        break;
                    }
                    List<Integer> prefList = new ArrayList<>();
                    prefList.add(Integer.parseInt(words[1]));
                    prefList.add(Integer.parseInt(words[2]));
                    prefList.add(Integer.parseInt(words[3]));
                    return prefList;
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static VpnInstance getVpnInstance(String node, String vpnName) {
        BufferedReader reader;
        String filePath = BgpDiagnosis.cfgPathMap.get(node);
        VpnInstance vpnInstance = new VpnInstance(node, vpnName);
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine().strip();
            int lineNum = 1;
            boolean ifReachVpnLine = false;
            while (line != null) {
                // System.out.println(line);
                // read next line
                if (line.contains(KeyWord.IP_VPN_INSTANCE) && line.contains(vpnName)) {
                    // 进入这段表达找到了对应名称的VPN
                    vpnInstance.addConfigLine(lineNum, line);
                    ifReachVpnLine = true;
                    line = reader.readLine();
                    while (line != null && !line.contains(KeyWord.ENDING_TOKEN)) {
                        String[] words = line.split(" ");
                        if (line.contains(KeyWord.IP_FAMILY_TOKEN)){
                            vpnInstance.setIpFamily(line.strip());
                            vpnInstance.addConfigLine(lineNum, line);
                        } else if (line.contains(KeyWord.TUNNEL_POLICY)){
                            // tnl-policy [policy-name]
                            vpnInstance.setTnlPolicyName(words[words.length-1]);
                            vpnInstance.addConfigLine(lineNum, line);
                        } else if (line.contains(KeyWord.RD_TOKEN)) {
                            // route-distinguisher [route-distinguisher]
                            vpnInstance.setRouterDistinguisher(words[words.length-1]);
                            vpnInstance.addConfigLine(lineNum, line);
                        } else if (line.contains(KeyWord.RT_TOKEN)) {
                            // vpn-target { vpn-target } &<1-8> [ vrfRtType ]
                            vpnInstance.addRtList(words, line);
                            vpnInstance.addConfigLine(lineNum, line);
                        } 
                        line = reader.readLine();
                        lineNum += 1;
                    }
                }
                if (ifReachVpnLine) {
                    break;
                }
                line = reader.readLine();
                lineNum += 1;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 如果解析失败 返回空值
        return vpnInstance;
        
        
    }
}
