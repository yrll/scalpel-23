package org.sng.datamodel.configuration;

import java.util.Map;

public class Configuration {
    private final String _sysName;
    private final Map<String,Interface> _interfaces;
    private final Map<Integer,IsisConfiguration> _isisConfigurations;


    public Configuration(String sysName, Map<String,Interface> interfaces, Map<Integer,IsisConfiguration> isisConfigurations) {
        _sysName = sysName;
        _interfaces = interfaces;
        _isisConfigurations = isisConfigurations;
    }

    public String getSysName() {
        return _sysName;
    }

    public Map<String,Interface> getInterfaces() {
        return _interfaces;
    }

    public Map<Integer,IsisConfiguration> getIsisConfigurations() {
        return _isisConfigurations;
    }
}
