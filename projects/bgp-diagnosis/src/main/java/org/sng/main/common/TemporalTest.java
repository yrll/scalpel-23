package org.sng.main.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemporalTest {
    public static void main(String[] args){
        Pattern pattern = Pattern.compile("([A-Z]+).*");
        Matcher m = pattern.matcher("BNG3");
        String str = "";
        if (m.find()) {
            str = m.group(1);
        }
        System.out.println(str);
     }
}
