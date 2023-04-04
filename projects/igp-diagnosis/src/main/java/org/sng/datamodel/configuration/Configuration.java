package org.sng.datamodel.configuration;

import java.util.List;

public class Configuration {
    private final String _sysName;
    private final List<Interface> _interfaces;
    private final List<IsisConfiguration> _isisConfigurations;


    public Configuration(String sysName, List<Interface> interfaces, List<IsisConfiguration> isisConfigurations) {
        _sysName = sysName;
        _interfaces = interfaces;
        _isisConfigurations = isisConfigurations;
    }

    public String getSysName() {
        return _sysName;
    }

    public List<Interface> getInterfaces() {
        return _interfaces;
    }

    public List<IsisConfiguration> getIsisConfigurations() {
        return _isisConfigurations;
    }
}
