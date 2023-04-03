package org.sng.parse;


import com.google.common.graph.ValueGraph;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.sng.datamodel.Prefix;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;
import org.sng.datamodel.isis.IsisTopology;

import java.io.File;
import java.io.IOException;
import java.util.*;

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


}
