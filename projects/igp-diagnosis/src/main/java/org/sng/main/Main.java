package org.sng.main;

import com.google.common.graph.ValueGraph;
import com.google.gson.JsonObject;
import org.sng.isisdiagnosis.IsisErrorFlow;
import org.sng.datamodel.Layer1Topology;
import org.sng.datamodel.Prefix;
import org.sng.datamodel.configuration.Configuration;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;
import org.sng.isisdiagnosis.IsisDiagnosis;
import org.sng.parse.JsonParser;

import java.io.IOException;
import java.util.*;

public class Main {
    static String baseDirectory = "networks/provenanceInfo/isis/case3/";

    public static void main(String[] args) throws IOException {

        // 初始化
        IsisDiagnosis isisDiagnosis = initIgpDiagnosis();

        // 设置错误流
        List<IsisErrorFlow> isisErrorFlowList = new ArrayList<>();
//        isisErrorFlowList.add(new IsisErrorFlow("CSG1-1-1", Prefix.parse("70.0.0.11/32")));
//        isisErrorFlowList.add(new IsisErrorFlow("CSG1-2-1", Prefix.parse("70.0.0.6/32")));
//        isisErrorFlowList.add(new IsisErrorFlow("CSG1-1-1", Prefix.parse("110.0.0.8/32")));
        isisErrorFlowList.add(new IsisErrorFlow("ASG1", Prefix.parse("70.0.0.6/32")));

        // ISIS 诊断
        for (IsisErrorFlow isisErrorFlow : isisErrorFlowList){
            System.out.println(isisErrorFlow.getPrefix());
            isisDiagnosis.igpDiagnosis(isisErrorFlow);
        }
    }

    /** 从文件中初始化IGP诊断所需信息 **/
    private static IsisDiagnosis initIgpDiagnosis() throws IOException {
        // 获取物理拓扑
        String layer1TopologyFilePath = baseDirectory + "connectInterfaceInfo.json";
        JsonObject layer1TopologyJsonObject = JsonParser.getJsonObject(layer1TopologyFilePath);
        Layer1Topology layer1Topology = JsonParser.getLayer1Toplogy(layer1TopologyJsonObject);

        //获取配置数据结构
        String configFilePath = baseDirectory + "configurationInfo.json";
        JsonObject configFileObject = JsonParser.getJsonObject(configFilePath);
        Map<String, Configuration> configurations = JsonParser.getConfigurations(configFileObject);

        // 获取直连路由源发信息
        String directRoutesFilePath = baseDirectory + "relatedStaticAndDirectInfo.json";
        JsonObject directRoutesJsonObject = JsonParser.getJsonObject(directRoutesFilePath);
        Map<Prefix, Set<String>> directRouteDevicesMap = JsonParser.getDirectRouteOrigins(directRoutesJsonObject);

        // 获取ISIS邻居图，以及每个prefix的路由导入边
        String isisInfoFilePath = baseDirectory + "isisProtocolInfo.json";
        JsonObject isisInfoJsonObject = JsonParser.getJsonObject(isisInfoFilePath);
        ValueGraph<IsisNode, IsisEdgeValue> commonFwdGraph = JsonParser.parseIsisCommonGraph(isisInfoJsonObject.get("isisNodes").getAsJsonObject());
        Map<Prefix, List<IsisEdge>> prefixEdgesMap = JsonParser.
                parsePrefixImportEdges(isisInfoJsonObject.get("dstPrefix2ImportNodes").getAsJsonObject(), commonFwdGraph.nodes(),configurations);

        // 获取配置行定位所需信息
        Map<String,String> filePathMap = new HashMap<>();
        configurations.keySet().forEach(deviceName -> filePathMap.put(deviceName,baseDirectory+deviceName+".cfg"));
        return new IsisDiagnosis(layer1Topology,configurations,commonFwdGraph,prefixEdgesMap,directRouteDevicesMap,filePathMap);

    }
}
