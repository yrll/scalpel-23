package org.sng.isisdiagnosis;

import org.apache.commons.io.FileUtils;
import org.sng.datamodel.configuration.RoutePolicy;
import org.sng.datamodel.isis.IsisNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigLocalization {

    public final Map<String,String> _filePathMap;
    public Map<String,List<String>> _cache;

    public ConfigLocalization(Map<String, String> filePathMap) {
        _filePathMap = filePathMap;
        _cache = new HashMap<>();
    }

    public IsisRepairOption.ErrorConfig localizeIsisProcessImport(String deviceName, Integer isisId, Integer importIsisId){
        IsisRepairOption.ErrorConfig errorConfig = null;
        List<String> fileContent = getFileContent(deviceName);
        String importFlag = "";
        if (importIsisId == IsisNode.DIRECT_IMPORT){
            importFlag = "import-route direct";
        }
        else if (importIsisId  == IsisNode.STATIC_IMPORT){
            importFlag = "import-route static";
        }
        else {
            importFlag = "import-route isis";
        }

        // 标记是否找到对应的ISIS进程配置块
        boolean isReachBlock = false;
        for (int i =1 ; i <= fileContent.size(); i++){
            String line = fileContent.get(i-1);
            if (line.contains("isis "+isisId)){
                isReachBlock = true;
            }
            if (isReachBlock && line.contains( importFlag+ importIsisId)){
                errorConfig = new IsisRepairOption.ErrorConfig(deviceName,i,line);
                break;
            }
        }
        return errorConfig;
    }

    public List<IsisRepairOption.ErrorConfig> localizeMatchedPolicy(String deviceName, RoutePolicy.MatchedPolicy matchedPolicy){
        List<IsisRepairOption.ErrorConfig> errorConfigs = new ArrayList<>();
        List<String> fileContent = getFileContent(deviceName);
        for (int i =1 ; i <= fileContent.size(); i++){
            String line = fileContent.get(i-1);
            if (line.contains("route-policy "+matchedPolicy.getRoutePolicy().getRoutePolicyName())
                    && line.contains("node "+ matchedPolicy.getRoutePolicyNode().getNodeNum())){
                errorConfigs.add(new IsisRepairOption.ErrorConfig(deviceName,i,line));
            }
            else if (matchedPolicy.getIpPrefixesV4Info() != null
                    && line.contains("ip ip-prefix " + matchedPolicy.getIpPrefixesV4Info().getName())
                    && line.contains("index "+ matchedPolicy.getIpPrefixNode().getIndex())){
                errorConfigs.add(new IsisRepairOption.ErrorConfig(deviceName,i,line));
            }
        }
        return errorConfigs;
    }

    public enum INTERFACE_CONFIG_TYPE{
        // 对应配置标识
        MTU("mtu"),
        NETWORK_TYPE("isis circuit-type"),
        ISIS_SILENT("isis silent"),
        SHUTDOWN("shutdown"),
        VLAN_TYPE("vlan-type dot1q"),
        IP_ADDRESS("ip address"),
        ISIS_ENABLE("isis enable");
        private final String _configFlag;

        INTERFACE_CONFIG_TYPE(String configFlag) {
            _configFlag = configFlag;
        }

        public String toConfigFlag() {
            return _configFlag;
        }
    }

    /** 定位端口相关配置 **/
    public IsisRepairOption.ErrorConfig localizeInterfaceConfig(String deviceName, String interfaceName,INTERFACE_CONFIG_TYPE configType){
        IsisRepairOption.ErrorConfig errorConfig = null;
        List<String> fileContent = getFileContent(deviceName);
        // 标记是否找到对应的端口配置块
        boolean isReachBlock = false;
        for (int i =1 ; i <= fileContent.size(); i++){
            String line = fileContent.get(i-1);
            if (line.contains("interface "+interfaceName)){
                isReachBlock = true;
            }
            if (isReachBlock && line.contains(configType.toConfigFlag())){
                errorConfig = new IsisRepairOption.ErrorConfig(deviceName,i,line);
                break;
            }
        }
        if (errorConfig == null && (configType == INTERFACE_CONFIG_TYPE.VLAN_TYPE || configType == INTERFACE_CONFIG_TYPE.NETWORK_TYPE)){
            errorConfig = new IsisRepairOption.ErrorConfig(deviceName,-1,configType.toConfigFlag());
        }
        return errorConfig;
    }

    /** 生成缺失的isis enable命令 **/
    public IsisRepairOption.ErrorConfig generateIsisEnable(String deviceName, Integer isisId){
        int configIsisId = isisId == null ? -1 : isisId;
        String configLine = "isis enable "+configIsisId;
        return new IsisRepairOption.ErrorConfig(deviceName,-1,configLine);
    }

    /**
     * 生成缺失的import-route命令
     **/
    public IsisRepairOption.ErrorConfig generateImportRoute(String deviceName, Integer isisId){
        String configLine;
        if (isisId == IsisNode.DIRECT_IMPORT){
            configLine = "import-route direct";
        }
        else if (isisId == IsisNode.STATIC_IMPORT){
            configLine = "import-route static";
        }
        else {
            configLine = "import-route isis " + isisId;
        }
        return new IsisRepairOption.ErrorConfig(deviceName,-1,configLine);
    }

    /** 生成缺失的端口启用命令 **/
    public IsisRepairOption.ErrorConfig generateInterfaceUp(String deviceName,String interfaceName){
        Map<String,String> matchLines = new HashMap<>();
        String configLine = "interface "+interfaceName;
        return new IsisRepairOption.ErrorConfig(deviceName,-1,configLine);
    }

    /** 生成缺失的Eth-Trunk启用命令 **/
    public IsisRepairOption.ErrorConfig generateEthTrunk(String deviceName){
        String configLine = "eth-trunk ?";
        return new IsisRepairOption.ErrorConfig(deviceName,-1,configLine);
    }

    /** 生成缺失的Eth-Trunk启用命令 **/
    public IsisRepairOption.ErrorConfig generateIsisProcess(String deviceName,Integer isisID){
        String configLine = "isis "+isisID;
        return new IsisRepairOption.ErrorConfig(deviceName,-1,configLine);
    }

    /**
     * 生成缺失的网段配置命令
     **/
    public IsisRepairOption.ErrorConfig generateIpAddress(String deviceName){
        String configLine = "ip address ?";
        return new IsisRepairOption.ErrorConfig(deviceName,-1,configLine);
    }

    private List<String> getFileContent(String deviceName) {
        if (_cache.containsKey(deviceName)){
            return _cache.get(deviceName);
        }
        else{
            File file = new File(_filePathMap.get(deviceName));
            try {
                return FileUtils.readLines(file,"UTF-8");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
