package org.sng.main.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.sng.datamodel.Prefix;
import org.sng.datamodel.Ip;
import org.sng.main.common.StaticRoute;

public class ConfigTaint {

    public static StaticRoute staticRouteFinder(String filePath, StaticRoute route) {
        // filePath是cfg文件地址，keyWords是静态路由相关的
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
    
}
