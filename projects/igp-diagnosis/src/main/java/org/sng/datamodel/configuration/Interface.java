package org.sng.datamodel.configuration;

import org.sng.datamodel.Prefix;

import java.util.Set;

public class Interface {
    private final String _name;
    private final String _vpnName;
    private final Set<String> _phyIfOrEthTrunk;
    private final Prefix _originalAddress;
    private final Boolean _isShutdown;
    private final Integer _isisEnable;
    private final Boolean _isisSilent2ZeroCost;
    private final Integer _isisCost;
    private final Integer _tagValue;
    private final Boolean _circuitTypeP2P;
    private final Integer _vlanTypeDotLq;
    private final Integer _ipv6MtuAndSpread;


    public Interface(String name, String vpnName, Set<String> phyIfOrEthTrunk, Prefix originalAddress, Boolean isShutdown, Integer isisEnable,
                     String isisSilent2ZeroCost, Integer isisCost, Integer tagValue, Boolean circuitTypeP2P, Integer vlanTypeDotLq, String ipv6MtuAndSpread) {
        _name = name;
        _vpnName = vpnName;
        _phyIfOrEthTrunk = phyIfOrEthTrunk;
        _originalAddress = originalAddress;
        _isShutdown = isShutdown;
        _isisEnable = isisEnable;
        _isisSilent2ZeroCost = Boolean.parseBoolean(isisSilent2ZeroCost);
        _isisCost = isisCost;
        _tagValue = tagValue;
        _circuitTypeP2P = circuitTypeP2P;
        _vlanTypeDotLq = vlanTypeDotLq;
        _ipv6MtuAndSpread = Integer.parseInt(ipv6MtuAndSpread);
    }

    public String getName() {
        return _name;
    }

    public String getVpnName() {
        return _vpnName;
    }

    public Set<String> getPhyIfOrEthTrunk() {
        return _phyIfOrEthTrunk;
    }

    public Prefix getOriginalAddress() {
        return _originalAddress;
    }

    public Boolean getIsShutdown() {
        return _isShutdown;
    }

    public Integer getIsisEnable() {
        return _isisEnable;
    }

    public Integer getIsisCost() {
        return _isisCost;
    }

    public Boolean getIsisSilent2ZeroCost() {
        return _isisSilent2ZeroCost;
    }

    public Integer getTagValue() {
        return _tagValue;
    }

    public Boolean getCircuitTypeP2P() {
        return _circuitTypeP2P;
    }

    public Integer getVlanTypeDotLq() {
        return _vlanTypeDotLq;
    }

    public Integer getIpv6MtuAndSpread() {
        return _ipv6MtuAndSpread;
    }
}
