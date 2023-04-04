package org.sng.datamodel.configuration;

import java.util.List;

public class IpPrefixesV4Info {
    private final Integer _id;
    private final String _name;
    private final List<IpPrefixNodeModel> _ipPrefixNodeModelList;


    public IpPrefixesV4Info(Integer id, String name, List<IpPrefixNodeModel> ipPrefixNodeModelList) {
        _id = id;
        _name = name;
        _ipPrefixNodeModelList = ipPrefixNodeModelList;
    }

    public Integer getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    public List<IpPrefixNodeModel> getIpPrefixNodeModelList() {
        return _ipPrefixNodeModelList;
    }
}
