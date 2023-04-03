package org.sng.main.localization;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Localizer {

    // key是行号，String是那一行的配置命令
    Map<Integer, String> getErrorConfigLines();
}
