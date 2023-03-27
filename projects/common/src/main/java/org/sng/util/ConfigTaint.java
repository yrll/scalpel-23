package org.sng.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ConfigTaint {

    public static String[] staticRouteFinder(String filePath, String [] keyWords) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();
            boolean ifThisLine = false;
            String targetLine = "";
            while (line != null && !ifThisLine) {
                // System.out.println(line);
                // read next line
                ifThisLine = true;
                for (int i=0; i<keyWords.length; i++) {
                    if (!line.contains(keyWords[i])) {
                        ifThisLine = false;
                        break;
                    }
                }
                if (ifThisLine) {
                    targetLine = line;
                }
                line = reader.readLine();
            }
            reader.close();
            return targetLine.split(" ");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "".split(" ");
    }
    
}
