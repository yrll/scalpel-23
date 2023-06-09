package org.sng.isisdiagnosis;

public class IsisErrorType {

    public enum ErrorType{
        // 不存在路由传播路径
        ISIS_NO_ROUTE_PATH_ERROR("目的前缀没有路径到达源节点"),
        // ISIS路由引入失败（进程间或协议间）
        ISIS_ROUTE_IMPORT_FAIL_ERROR("路由导入失败"),
        // 需要删除的ISIS路由引入
        ISIS_ROUTE_IMPORT_UNWANTED_ERROR("多源发前缀导致错误，需要删除非指定前缀的路由导入");

        private final String _name;

        ErrorType(String name) {
            _name = name;
        }

        public String errorString() {
            return _name;
        }
    }


    public enum IsisPeerFailErrorType{
        // 头部节点端口是否有配置
        IS_HEAD_INTERFACE_UP,
        // 尾部节点端口是否有配置
        IS_TAIL_INTERFACE_UP,
        // 头部节点端口没有是能EthTrunk
        HEAD_ETH_TRUNK_COINCIDE,
        // 尾部节点端口没有是能EthTrunk
        TAIL_ETH_TRUNK_COINCIDE,
        // 端口MTU是否一致
        INTERFACE_MTU_COMPARE,
        // 头部端口是否配置ISIS进程
        ISIS_HEAD_ENABLE,
        // 尾部端口是否配置ISIS进程
        ISIS_TAIL_ENABLE,
        // 两端端口端口类型是否一致
        CIRCUIT_TYPE_COINCIDE,
        // 头部端口配置的进程是否存在
        ISIS_HEAD_PROC_CONFIG_VALID,
        // 尾部端口配置的进程是否存在
        ISIS_TAIL_PROC_CONFIG_VALID,
        // 头部端口是否静默
        ISIS_HEAD_PEER_NO_SILENT,
        // 尾部端口是否静默
        ISIS_TAIL_PEER_NO_SILENT,
        // 头部端口是否关闭
        IS_HEAD_IFACE_SHUT_DOWN,
        // 尾部端口是否关闭
        IS_TAIL_IFACE_SHUT_DOWN,
        // 头部端口是否配置网段
        IS_HEAD_IFACE_STATE_UP ,
        // 尾部端口是否配置网段
        IS_TAIL_IFACE_STATE_UP,
        // 端口VLAN是否一致
        IS_SAME_VLAN,
        // 端口网段是否一致
        IS_SAME_SUBNET
    }
    public enum IsisRouteImportFailErrorType {
        // 源发路由端口没有加入指定ISIS进程
        PREFIX_ISIS_ENABLE,
        // 没有使能ISIS进程导入
        ISIS_IMPORT_ENABLE,
        // 策略过滤
        IMPORT_POLICY_FILTER,
        // 需要删除的ISIS进程引入
    }

    public enum IsisRouteImportUnwantedError{
        // 需要删除路由引入
        ISIS_UNWANTED_IMPORT,
    }
}
