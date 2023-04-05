package org.sng.datamodel.isis;


import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class IsisNode {
    private final String _devName;
    private final Integer _id;
    private final int _isisId;

    public static final int DIRECT_ENABLE = -1;
    public static final int DIRECT_IMPORT = -2;
    public static final int STATIC_IMPORT = -3;
    public static final int NEW_ISIS_PROCESS = -4;

    public static final List<Integer> PREFIX_ORIGIN_IDS = Arrays.asList(DIRECT_IMPORT,DIRECT_ENABLE,STATIC_IMPORT);
    public static final List<Integer> NOT_ISIS_PROTOCOL_IDS = Arrays.asList(DIRECT_IMPORT,STATIC_IMPORT);


    public IsisNode(String devName, Integer id, int isisId) {
        _devName = devName;
        _id = id;
        _isisId = isisId;
    }

    public static IsisNode creatDirectEnableNode(String devName){
        return new IsisNode(devName,null,DIRECT_ENABLE);
    }

    public static IsisNode creatDirectImportNode(String devName){
        return new IsisNode(devName,null,DIRECT_IMPORT);
    }

    public static IsisNode creatStaticImportNode(String devName){
        return new IsisNode(devName,null,STATIC_IMPORT);
    }

    public static IsisNode creatNewIsisNode(String devName){
        return new IsisNode(devName,null,NEW_ISIS_PROCESS);
    }

    public String getDevName() {
        return _devName;
    }

    public Integer getId() {
        return _id;
    }

    public int getIsisId() {
        return _isisId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        IsisNode node = (IsisNode) obj;
        return _devName.equals(node._devName) && _isisId == node._isisId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_devName, _isisId);
    }
}
