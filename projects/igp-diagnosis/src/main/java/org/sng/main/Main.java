package org.sng.main;

import com.google.common.graph.ValueGraph;
import com.google.gson.JsonObject;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Layer1Edge;
import org.sng.datamodel.Layer1Topology;
import org.sng.datamodel.Prefix;
import org.sng.datamodel.ibgp.IBgpNode;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;
import org.sng.parse.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws IOException {
        //get physical topology
        Layer1Topology layer1Topology = getLayer1Topology();

        // get common graph and import edges of each prefix
        String isisInfoFilePath = "projects/networks/provenanceInfo/isis/case3/isisProtocolInfo.json";
        JsonObject jsonObject = JsonParser.getJsonObject(isisInfoFilePath);
        ValueGraph<IsisNode, IsisEdgeValue> commonFwdGraph = JsonParser.parseIsisCommonGraph(jsonObject.get("isisNodes").getAsJsonObject());
        Map<Prefix, List<IsisEdge>> prefixEdgesMap = JsonParser.
                parsePrefixImportEdges(jsonObject.get("dstPrefix2ImportNodes").getAsJsonObject(), commonFwdGraph.nodes());

        // igp diagnosis
        IgpDiagnosis igpDiagnosis = new IgpDiagnosis(layer1Topology,commonFwdGraph,prefixEdgesMap);
        Map<IBgpNode,IBgpNode> peerMap = new HashMap<>();
        List<Prefix> origins = new ArrayList<>();
        peerMap.put(new IBgpNode(Ip.parse("70.0.0.6"),"CSG1-1-1"),
                new IBgpNode(Ip.parse("110.0.0.8"),"ASG1"));
        peerMap.put(new IBgpNode(Ip.parse("110.0.0.8"),"ASG1"),
                new IBgpNode(Ip.parse("70.0.0.6"),"CSG1-1-1"));
        origins.add(Prefix.parse("110.0.0.8/32"));
        origins.add(Prefix.parse("70.0.0.6/32"));
        igpDiagnosis.igpDiagnosis(peerMap,origins);
    }

    // get physical topology
    private static Layer1Topology getLayer1Topology() {
        ArrayList<Layer1Edge> edges = new ArrayList<>();
        edges.add(new Layer1Edge("ASG1","GigabitEthernet2/0/1","CSG1-1-1","GigabitEthernet2/0/1"));
        edges.add(new Layer1Edge("CSG1-1-1","GigabitEthernet2/0/1","ASG1","GigabitEthernet2/0/1"));

        return new Layer1Topology(edges);
    }
}
