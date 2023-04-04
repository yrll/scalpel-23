package org.sng.parse;


import com.google.common.graph.ValueGraph;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.sng.datamodel.*;
import org.sng.datamodel.configuration.*;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;
import org.sng.datamodel.isis.IsisTopology;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class JsonParser {

    /** 从JSON对象"isisNodes"中获取ISIS图 **/
    public static ValueGraph<IsisNode,IsisEdgeValue> parseIsisCommonGraph(JsonObject nodesJson){
        Map<Integer, IsisNode> isisNodeMap = getNodesFromJson(nodesJson);
        List<IsisNode> isisNodeList = new ArrayList<>(isisNodeMap.values());
        assert isisNodeList.size() == new HashSet<>(isisNodeList).size();
        List<IsisEdge> isisEdgeList = getEdgesFromJson(nodesJson,isisNodeMap);
        IsisTopology isisTopology = IsisTopology.creat(isisNodeList,isisEdgeList);
        return isisTopology.getGraph();
    }

    /** 将文件转换为String **/
    public static JsonObject getJsonObject(String filePath) throws IOException {
        File file = new File(filePath);
        String jsonStr =  FileUtils.readFileToString(file,"UTF-8");
        return com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
    }

    /** 从JSON对象"isisNodes"获取@IsisNodes信息 **/
    private static Map<Integer, IsisNode> getNodesFromJson(JsonObject nodesJson){
        Map<Integer,IsisNode> isisNodeMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()){
            JsonObject nodeProps = (JsonObject) entry.getValue();
            int id =nodeProps.getAsJsonPrimitive("id").getAsInt();
            String devName = nodeProps.getAsJsonPrimitive("devName").getAsString();
            int isisId = nodeProps.getAsJsonPrimitive("isisId").getAsInt();
            isisNodeMap.put(id,new IsisNode(devName,id,isisId));
        }
        return isisNodeMap;
    }

    /** 从JSON对象"isisNodes"获取@IsisEdges信息 **/
    private static List<IsisEdge> getEdgesFromJson(JsonObject nodesJson, Map<Integer, IsisNode> isisNodeMap){
        List<IsisEdge> isisEdgeList = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()){

            // 源节点
            int sourceNodeId = Integer.parseInt(entry.getKey());
            IsisNode sourceNode = isisNodeMap.get(sourceNodeId);
            JsonObject succeedingEdges = ((JsonObject) entry.getValue()).getAsJsonObject("succeedingNode2Edge");

            // 从JSON对象"succeedingNode2Edge"获取目的节点与边
            for (Map.Entry<String, JsonElement> edgeEntry : succeedingEdges.entrySet()){
                // 获取目的节点
                int targetNodeId = Integer.parseInt(edgeEntry.getKey());
                IsisNode targetNode = isisNodeMap.get(targetNodeId);

                // 从JSON对象"equalCostIsisEdges"获取边以及边上的信息
                JsonObject edgeValue = edgeEntry.getValue().getAsJsonObject();
                JsonObject elements = edgeValue.asMap().values().stream().findFirst().get().getAsJsonObject();
                JsonObject edgeProps = elements.get("equalCostIsisEdges").getAsJsonArray().get(0).getAsJsonObject();
                int cost = edgeProps.get("cost").getAsInt();
                String srcPhyIf = edgeProps.get("srcPhyIf").getAsString();
                String dstPhyIf = edgeProps.get("dstPhyIf").getAsString();
                IsisEdgeValue isisEdgeValue = new IsisEdgeValue(srcPhyIf,dstPhyIf,cost);
                isisEdgeList.add(new IsisEdge(sourceNode,targetNode,isisEdgeValue));
            }
        }
        return isisEdgeList;
    }

    /** 从JSON对象"dstPrefix2ImportNodes"获取每个prefix的路由导入信息 **/
    public static Map<Prefix,List<IsisEdge>> parsePrefixImportEdges(JsonObject prefixImport, Set<IsisNode> isisNodeList){
        Map<Prefix,List<IsisEdge>> prefixEdgesMap = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : prefixImport.entrySet()){
            Prefix prefix = Prefix.parse(entry.getKey());
            List<JsonElement> importList = entry.getValue().getAsJsonArray().asList();
            Set<IsisEdge> isisEdgeSet = new HashSet<>();
            for (JsonElement element: importList){
                JsonObject importInfo = (JsonObject) element;
                String devName = importInfo.get("devName").getAsString();
                int dstIsisId = importInfo.get("dstIsisProc").getAsInt();
                int cost = importInfo.get("cost").getAsInt();
                String srcProtocol = importInfo.get("srcProtocol").getAsString();

                IsisNode dstNode = getExistingNode(isisNodeList,devName,dstIsisId);

                // ISIS进程导入信息
                if (srcProtocol.equals("ISIS")){
                    int srcIsisId = importInfo.get("srcIsisProc").getAsInt();
                    IsisNode srcNode = getExistingNode(isisNodeList,devName,srcIsisId);
                    IsisEdgeValue isisEdgeValue = new IsisEdgeValue("null","null",cost);
                    isisEdgeSet.add(new IsisEdge(srcNode,dstNode,isisEdgeValue));
                }
                // 其他协议导入信息（direct，static）
                else {
                    IsisNode srcNode = IsisNode.creatDirectNode(devName);
                    String srcDevIf = importInfo.get("srcDevIf").getAsString();
                    IsisEdgeValue isisEdgeValue = new IsisEdgeValue("null",srcDevIf,cost);
                    isisEdgeSet.add(new IsisEdge(srcNode,dstNode,isisEdgeValue));
                }
            }
            prefixEdgesMap.put(prefix,new ArrayList<>(isisEdgeSet));
        }
        return prefixEdgesMap;
    }

    private static IsisNode getExistingNode(Set<IsisNode> isisNodeList, String devName, int isisId){
        IsisNode existNode = null;
        for (IsisNode node : isisNodeList){
            if (node.getDevName().equals(devName) && node.getIsisId() == isisId){
                existNode = node;
                break;
            }
        }
        assert existNode != null;
        return existNode;
    }

    /** 从JSON对象"relatedStaticAndDirectInfo"中获取直连路由的源发节点 **/
    public static Map<Prefix,Set<String>> getDirectRouteOrigins(JsonObject jsonObject){
        Map<Prefix,Set<String>> prefixDevicesMap = new HashMap<>();
        // todo: 静态路由的获取与建模
        JsonObject directRouteInfo = jsonObject.get("directRouteInfo").getAsJsonObject();
        for (Map.Entry<String, JsonElement> deviceVpnEntry : directRouteInfo.entrySet()) {
            String device = deviceVpnEntry.getKey();
            JsonObject vpnList = deviceVpnEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> vpnPrefixEntry : vpnList.entrySet()) {
                JsonObject prefixList = vpnPrefixEntry.getValue().getAsJsonObject();
                // 这层为每个prefix和对应的信息
                for (Map.Entry<String, JsonElement> prefixInfoEntry : prefixList.entrySet()){
                    JsonObject prefixInfo = prefixInfoEntry.getValue().getAsJsonArray().get(0).getAsJsonObject();
                    Prefix prefix = Prefix.parse(prefixInfo.get("ipPrefix").getAsString());

                    if (prefixDevicesMap.containsKey(prefix)) {
                        prefixDevicesMap.get(prefix).add(device);
                    } else {
                        Set<String> deviceSet =  new HashSet<>();
                        deviceSet.add(device);
                        prefixDevicesMap.put(prefix,deviceSet);
                    }
                }
            }
        }
        return prefixDevicesMap;
    }

    /** 从JSON对象"connectInterfaceInfo"中获取物理拓扑 **/
    public static Layer1Topology getLayer1Toplogy(JsonObject jsonObject){
        ArrayList<Layer1Edge> edges = new ArrayList<>();
        for (Map.Entry<String, JsonElement> deviceVpnEntry : jsonObject.entrySet()) {
            String headIfaceStr = deviceVpnEntry.getKey();
            String tailIfaceStr = deviceVpnEntry.getValue().getAsString();
            edges.add(new Layer1Edge(parseIfaceStr(headIfaceStr),parseIfaceStr(tailIfaceStr)));
        }
        return new Layer1Topology(edges);
    }

    private static Layer1Node parseIfaceStr(String ifaceStr){
        String[] splitStr = ifaceStr.split("@");
        String deviceName = splitStr[0];
        String ifaceName = splitStr[1];
        return new Layer1Node(deviceName,ifaceName);
    }

    /** 从JSON对象"configurationInfo"中获取配置信息 **/
    public static Map<String, Configuration> getConfigurations(JsonObject jsonObject){
        Map<String, Configuration> configurations = new HashMap<>();
        List<JsonElement> elements = new ArrayList<>(jsonObject.asMap().values());
        for (JsonElement element: elements){
            JsonObject configObject = (JsonObject) element;
            String uniqueName = configObject.get("uniqName").getAsString();
            List<Interface> allIfaceConfigs = getAllIfaceConfigs(configObject.get("allInterfaces").getAsJsonObject());
            List<IpPrefixesV4Info> allIpPrefixesV4Info = getAllIpPrefixesV4Info(configObject.get("allIpPrefixesV4Info").getAsJsonObject());
            List<IsisConfiguration> isisConfigurations = getIsisConfigurations(configObject.get("isisIds").getAsJsonObject(), allIpPrefixesV4Info);
            configurations.put(uniqueName,new Configuration(uniqueName,allIfaceConfigs,isisConfigurations));
        }
        return configurations;
    }

    /** 解析接口配置 **/
    private static List<Interface> getAllIfaceConfigs(JsonObject allInterfacesObject){
        List<Interface> allIfaceConfigs = new ArrayList<>();
        List<JsonElement> elements = new ArrayList<>(allInterfacesObject.asMap().values());
        for (JsonElement element: elements){
            JsonObject ifaceConfigObject = (JsonObject) element;
            String name = ifaceConfigObject.get("name").getAsString();
            String vpnName = ifaceConfigObject.get("vpnName").getAsString();
            JsonArray phyIfOrEthTrunkArray = ifaceConfigObject.get("phyIfOrEthTrunk").getAsJsonArray();
            Set<String> phyIfOrEthTrunk = phyIfOrEthTrunkArray.asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
            Prefix originalAddress = null;
            if (!ifaceConfigObject.get("originalAddress").isJsonNull()){
                String[] prefixParts = ifaceConfigObject.get("originalAddress").getAsString().split("/");
                Ip prefixIp = Ip.parse(prefixParts[0]);
                Ip prefixMask = Ip.parse(prefixParts[1]);
                originalAddress = Prefix.create(prefixIp,prefixMask);
            }
            Boolean isShutdown = ifaceConfigObject.get("isShutdown").isJsonNull() ? null : ifaceConfigObject.get("isShutdown").getAsBoolean();
            Integer isisEnable = ifaceConfigObject.get("isisEnable").isJsonNull() ? null : ifaceConfigObject.get("isisEnable").getAsInt();
            String isisSilent2ZeroCost = ifaceConfigObject.get("isisSilent2ZeroCost").getAsJsonObject().asMap().keySet().stream().findFirst().get();
            Integer isisCost = ifaceConfigObject.get("isisCost").isJsonNull() ? null : ifaceConfigObject.get("isisCost").getAsInt();
            Integer tagValue = ifaceConfigObject.get("tagValue").isJsonNull() ? null : ifaceConfigObject.get("tagValue").getAsInt();
            Boolean circuitTypeP2P = ifaceConfigObject.get("circuitTypeP2P").getAsBoolean();
            Integer vlanTypeDotLq = ifaceConfigObject.get("vlanTypeDotLq").isJsonNull() ? null : ifaceConfigObject.get("vlanTypeDotLq").getAsInt();
            String ipv6MtuAndSpread = ifaceConfigObject.get("ipv6MtuAndSpread").getAsJsonObject().asMap().keySet().stream().findFirst().get();

            allIfaceConfigs.add(new Interface(name,vpnName, phyIfOrEthTrunk, originalAddress,isShutdown,isisEnable, isisSilent2ZeroCost, isisCost,tagValue,circuitTypeP2P,vlanTypeDotLq,ipv6MtuAndSpread));
        }
        return allIfaceConfigs;
    }

    /** 解析前缀匹配信息 **/
    private static List<IpPrefixesV4Info> getAllIpPrefixesV4Info(JsonObject allIpPrefixesV4InfoObject){
        List<IpPrefixesV4Info> allIpPrefixesV4Info = new ArrayList<>();
        List<JsonElement> elements = new ArrayList<>(allIpPrefixesV4InfoObject.asMap().values());
        for (JsonElement element: elements) {
            JsonObject ifaceConfigObject = (JsonObject) element;
            String name = ifaceConfigObject.get("name").getAsString();
            Integer id = ifaceConfigObject.get("id").getAsInt();
            JsonArray ipPrefixNodeListObject = ifaceConfigObject.get("ipPrefixNodeModelList").getAsJsonArray();
            List<IpPrefixNodeModel> ipPrefixNodeList = new ArrayList<>();
            for (JsonElement ipPrefixNodeElement: ipPrefixNodeListObject){
                JsonObject ipPrefixNodeObject = ipPrefixNodeElement.getAsJsonObject();
                Integer index = ipPrefixNodeObject.get("index").getAsInt();
                String matchMode = ipPrefixNodeObject.get("matchMode").getAsString();
                Prefix ipMask = Prefix.parse(ipPrefixNodeObject.get("ipMask").getAsString());
                Integer greaterEqual = ipPrefixNodeObject.get("greaterEqual").isJsonNull() ? null : ipPrefixNodeObject.get("greaterEqual").getAsInt();
                Integer lessEqual = ipPrefixNodeObject.get("lessEqual").isJsonNull() ? null : ipPrefixNodeObject.get("lessEqual").getAsInt();
                ipPrefixNodeList.add(new IpPrefixNodeModel(index,matchMode,ipMask,greaterEqual,lessEqual));
            }
            allIpPrefixesV4Info.add(new IpPrefixesV4Info(id,name,ipPrefixNodeList));
        }

        return allIpPrefixesV4Info;
    }

    /** 解析ISIS配置信息 **/
    private static List<IsisConfiguration> getIsisConfigurations(JsonObject isisConfigsObject,List<IpPrefixesV4Info> allIpPrefixesV4Info){
        List<IsisConfiguration> isisConfigurations = new ArrayList<>();
        List<JsonElement> elements = new ArrayList<>(isisConfigsObject.asMap().values());
        for (JsonElement element: elements) {
            JsonObject isisConfigObject = (JsonObject) element;
            Integer isisId = isisConfigObject.get("isisId").getAsInt();
            String isisLevel = isisConfigObject.get("isisLevel").getAsString();
            String vpnName = isisConfigObject.get("vpnName").getAsString();
            String costStyle = isisConfigObject.get("costStyle").getAsString();
            String areaId = isisConfigObject.get("areaId").getAsString();
            String systemId = isisConfigObject.get("systemId").getAsString();
            Boolean summary = isisConfigObject.get("summary").isJsonNull() ? null : isisConfigObject.get("summary").getAsBoolean();
            Integer loadBalancingNum = isisConfigObject.get("loadBalancingNum").isJsonNull() ? null : isisConfigObject.get("loadBalancingNum").getAsInt();
            Integer circuitCost = isisConfigObject.get("circuitCost").isJsonNull() ? null : isisConfigObject.get("circuitCost").getAsInt();
            List<IsisRouteImport> routeImports = isisConfigObject.get("importRoutes").isJsonNull() ? null : getRouteImports(isisConfigObject.get("importRoutes").getAsJsonArray(),allIpPrefixesV4Info);
            String networkEntity = isisConfigObject.get("networkEntity").getAsString();
            isisConfigurations.add(new IsisConfiguration(isisId,isisLevel,vpnName,costStyle,areaId,systemId,summary,loadBalancingNum,circuitCost,routeImports, networkEntity));
        }

        return isisConfigurations;
    }

    private static List<IsisRouteImport> getRouteImports(JsonArray routeImportsObject,List<IpPrefixesV4Info> allIpPrefixesV4Info){
        List<IsisRouteImport> routeImports = new ArrayList<>();
        for (JsonElement routeImportElement : routeImportsObject){
            JsonObject routeImportObject = (JsonObject) routeImportElement;
            String protocol = routeImportObject.get("protocol").getAsString();
            Integer protocolId = routeImportObject.get("protocolId").isJsonNull()? null : routeImportObject.get("protocolId").getAsInt();
            Integer tagValue = routeImportObject.get("tagValue").isJsonNull() ? null : routeImportObject.get("tagValue").getAsInt();
            Integer cost = routeImportObject.get("cost").isJsonNull() ? null : routeImportObject.get("cost").getAsInt();
            Boolean inheritCost = routeImportObject.get("inheritCost").isJsonNull() ? null : routeImportObject.get("inheritCost").getAsBoolean();
            RoutePolicy routePolicy = routeImportObject.get("routePolicyModel").isJsonNull() ? null : getRoutePolicy(routeImportObject.get("routePolicyModel").getAsJsonObject(),allIpPrefixesV4Info);
            Integer isisCost4ImportRoute = routeImportObject.get("isisCost4ImportRoute").isJsonNull() ? null : routeImportObject.get("isisCost4ImportRoute").getAsInt();
            routeImports.add(new IsisRouteImport(protocol,protocolId,tagValue,cost,inheritCost,routePolicy,isisCost4ImportRoute));
        }
        return routeImports;
    }

    private static RoutePolicy getRoutePolicy(JsonObject routePolicyObject,List<IpPrefixesV4Info> allIpPrefixesV4Info){
        Integer id = routePolicyObject.get("id").getAsInt();
        String routePolicyName = routePolicyObject.get("routePolicyName").getAsString();
        List<RoutePolicyNode> routePolicyNodes = new ArrayList<>();
        JsonArray routePolicyNodeList = routePolicyObject.get("routePolicyNodeModelList").getAsJsonArray();
        for (JsonElement routePolicyNodeElement: routePolicyNodeList) {
            JsonObject routePolicyNodeObject = (JsonObject) routePolicyNodeElement;
            Integer nodeNum = routePolicyNodeObject.get("nodeNum").getAsInt();
            String policyMatchMode = routePolicyNodeObject.get("policyMatchMode").getAsString();
            IpPrefixesV4Info ipPrefixesV4Info = null;
            if (!routePolicyNodeObject.get("ifMatchIpPrefix").isJsonNull()){
                JsonObject ifMatchIpPrefixObject = routePolicyNodeObject.get("ifMatchIpPrefix").getAsJsonObject();
                int ipPrefixId =  ifMatchIpPrefixObject.get("id").getAsInt();
                for (IpPrefixesV4Info ipPrefixesV4InfoElement : allIpPrefixesV4Info){
                    if (ipPrefixesV4InfoElement.getId() == ipPrefixId){
                        ipPrefixesV4Info = ipPrefixesV4InfoElement;
                    }
                }
            }
            Boolean ifMatchTag = routePolicyNodeObject.get("ifMatchTag").isJsonNull() ? null : routePolicyNodeObject.get("ifMatchTag").getAsBoolean();
            Integer applyTag = routePolicyNodeObject.get("applyTag").isJsonNull() ? null : routePolicyNodeObject.get("applyTag").getAsInt();
            routePolicyNodes.add(new RoutePolicyNode(nodeNum,policyMatchMode,ipPrefixesV4Info,ifMatchTag,applyTag));
        }
        return new RoutePolicy(id,routePolicyName,routePolicyNodes);
    }


}
