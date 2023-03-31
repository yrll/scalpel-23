package org.sng.datamodel;


public class ErrorFlow {
    // todo:对于指定路径的错误，还需要加上原路径和指定路径这个输入
    private final String _srcDevice;
    private final Prefix _prefix;


    public ErrorFlow(String srcDevice, Prefix prefix) {
        _srcDevice = srcDevice;
        _prefix = prefix;
    }

    public String getSrcDevice() {
        return _srcDevice;
    }

    public Prefix getPrefix() {
        return _prefix;
    }
}
