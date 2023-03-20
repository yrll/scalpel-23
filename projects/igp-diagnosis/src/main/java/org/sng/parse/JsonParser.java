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

    // get common ISIS forwarding graph from Json object "isisNodes"
    public static ValueGraph<IsisNode,IsisEdgeValue> parseIsisCommonGraph(JsonObject nodesJson){
        Map<Integer, IsisNode> isisNodeMap = getNodesFromJson(nodesJson);
        List<IsisNode> isisNodeList = new ArrayList<>(isisNodeMap.values());
        assert isisNodeList.size() == new HashSet<>(isisNodeList).size();
        List<IsisEdge> isisEdgeList = getEdgesFromJson(nodesJson,isisNodeMap);
        IsisTopology isisTopology = IsisTopology.creat(isisNodeList,isisEdgeList);
        return isisTopology.getGraph();
    }

    // Parse file into String format
    public static JsonObject getJsonObject(String filePath) throws IOException {
        File file = new File(filePath);
        String jsonStr =  FileUtils.readFileToString(file,"UTF-8");
        return com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
    }

    // Parse Json object "isisNodes" into a list of @IsisNodes
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

    // Parse Json object "isisNodes" into a list of @IsisEdges
    private static List<IsisEdge> getEdgesFromJson(JsonObject nodesJson, Map<Integer, IsisNode> isisNodeMap){
        List<IsisEdge> isisEdgeList = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()){

            // get source isisNode
            int sourceNodeId = Integer.parseInt(entry.getKey());
            IsisNode sourceNode = isisNodeMap.get(sourceNodeId);
            JsonObject succeedingEdges = ((JsonObject) entry.getValue()).getAsJsonObject("succeedingNode2Edge");

            // get targets and links form "succeedingNode2Edge" object
            for (Map.Entry<String, JsonElement> edgeEntry : succeedingEdges.entrySet()){
                // get target isisNode
                int targetNodeId = Integer.parseInt(edgeEntry.getKey());
                IsisNode targetNode = isisNodeMap.get(targetNodeId);

                // get edge properties (cost, physical interfaces) from "equalCostIsisEdges" object
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

    // get prefix import edge from Json object "dstPrefix2ImportNodes"
    public static Map<Prefix,List<IsisEdge>> parsePrefixImportEdges(JsonObject prefixImport, Set<IsisNode> isisNodeList){
        Map<Prefix,List<IsisEdge>> prefixEdgesMap = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : prefixImport.entrySet()){
            Prefix prefix = Prefix.parse(entry.getKey());
            List<JsonElement> importList = entry.getValue().getAsJsonArray().asList();
            List<IsisEdge> isisEdgeList = new ArrayList<>();
            for (JsonElement element: importList){
                JsonObject importInfo = (JsonObject) element;
                String devName = importInfo.get("devName").getAsString();
                int dstIsisId = importInfo.get("dstIsisProc").getAsInt();
                int cost = importInfo.get("cost").getAsInt();
                String srcProtocol = importInfo.get("srcProtocol").getAsString();

                IsisNode srcNode = getExistingNode(isisNodeList,devName,dstIsisId);

                // import from Isis process
                if (srcProtocol.equals("ISIS")){
                    int srcIsisId = importInfo.get("srcIsisProc").getAsInt();
                    IsisNode dstNode = getExistingNode(isisNodeList,devName,srcIsisId);
                    IsisEdgeValue isisEdgeValue = new IsisEdgeValue("null","null",cost);
                    isisEdgeList.add(new IsisEdge(srcNode,dstNode,isisEdgeValue));
                }
                // import from other protocol (direct)
                // todo: clarify all types of protocols
                else {
                    IsisNode dstNode = IsisNode.creatDirectNode(devName);
                    String srcDevIf = importInfo.get("srcDevIf").getAsString();
                    IsisEdgeValue isisEdgeValue = new IsisEdgeValue("null",srcDevIf,cost);
                    isisEdgeList.add(new IsisEdge(srcNode,dstNode,isisEdgeValue));
                }
            }
            prefixEdgesMap.put(prefix,isisEdgeList);
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


}
