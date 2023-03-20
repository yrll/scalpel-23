package org.sng.main;

import com.google.common.graph.Graphs;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Prefix;
import org.sng.datamodel.isis.IBgpNode;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;
import org.sng.parse.JsonParser;

import java.io.IOException;
import java.util.*;

public class IgpDiagnosis {
    public static void main(String[] args) throws IOException {

        // get common graph and import edges of each prefix
        String isisInfoFilePath = "C:\\Users\\lovex\\Desktop\\isisProtocolInfo.json";
        JsonObject jsonObject = JsonParser.getJsonObject(isisInfoFilePath);
        ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph = JsonParser.parseIsisCommonGraph(jsonObject.get("isisNodes").getAsJsonObject());
        Map<Prefix, List<IsisEdge>> prefixEdgesMap = JsonParser.
                parsePrefixImportEdges(jsonObject.get("dstPrefix2ImportNodes").getAsJsonObject(), isisFwdGraph.nodes());

        // get forwarding graph for each prefix
        Map<Prefix,MutableValueGraph> prefixFwdGraphMap = getPrefixFwdGraphMap(isisFwdGraph,prefixEdgesMap);

        //todo: diagnose duplicate subnets (error due to route priority)

        // diagnose reachability for each iBGP peer
        Map<IBgpNode,IBgpNode> peerMap = new HashMap<>();
        List<Prefix> origins = new ArrayList<>();
        peerMap.put(new IBgpNode(Ip.parse("70.0.0.6"),"CSG1-1-1"),
                new IBgpNode(Ip.parse("110.0.0.8"),"ASG1"));
        origins.add(Prefix.parse("110.0.0.8/32"));

        for (Map.Entry<IBgpNode,IBgpNode> entry: peerMap.entrySet()){
            IBgpNode srcIBgpNode = entry.getKey();
            IBgpNode dstIBgpNode = entry.getValue();
            Ip dstIp = dstIBgpNode.getIp();

            // get import prefixes for dst iBGP ip
            List<Prefix> dstPrefixList = getDstPrefixList(dstIp,origins);

            // compute connection for src to each prefix
            for (Prefix dstPrefix : dstPrefixList){
                MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph = prefixFwdGraphMap.get(dstPrefix);
                isSrcToPrefixConnected(srcIBgpNode,dstIBgpNode,prefixFwdGraph);
            }
        }

    }

    // complete forwarding graph for each prefix.
    private static Map<Prefix,MutableValueGraph> getPrefixFwdGraphMap(ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph,
                                                           Map<Prefix, List<IsisEdge>> prefixEdgesMap){
        Map<Prefix,MutableValueGraph> prefixFwdGraphMap = new HashMap<>();
        for (Map.Entry<Prefix, List<IsisEdge>> entry: prefixEdgesMap.entrySet()){
            Prefix prefix =  entry.getKey();
            MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph = Graphs.copyOf(isisFwdGraph);
            entry.getValue().forEach(isisEdge ->
                    prefixFwdGraph.putEdgeValue(isisEdge.getSource(),isisEdge.getTarget(),isisEdge.getEdgeValue()));
            prefixFwdGraphMap.put(prefix,prefixFwdGraph);
        }
        return prefixFwdGraphMap;
    }

    // get origin prefixes for dst ip
    private static List<Prefix> getDstPrefixList(Ip dstIp, List<Prefix> origins){
        List<Prefix> dstPrefixList = new ArrayList<>();
        for (Prefix origin : origins){
            if (origin.containsIp(dstIp)){
                dstPrefixList.add(origin);
            }
        }

        if (dstPrefixList.size() == 0){
            System.out.println("dst ip\t"+dstIp+"\thas not any origin prefix\t");
        }

        if (dstPrefixList.size() > 1){
            System.out.println("dst ip has multiple prefixes\t"+dstPrefixList);
        }

        return dstPrefixList;
    }

    // judge if any src process can reach dst nodes
    private static boolean isSrcToPrefixConnected(IBgpNode srcIBgpNode, IBgpNode dstIBgpNode,
                                                       MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        List<IsisEdge> disconnections = new ArrayList<>();

        // get src processes
        List<IsisNode> srcNodes = getSrcNodes(srcIBgpNode.getDevName(), prefixFwdGraph.nodes());
        if (srcNodes.size() == 0){
            throw new RuntimeException("Device "+ srcIBgpNode.getDevName() +" does not have any ISIS process");
        }

        // get dst node of this prefix
        IsisNode dstNode = getDstNode(dstIBgpNode.getDevName(), prefixFwdGraph.nodes());
        if (dstNode == null){
            IsisNode directNode = IsisNode.creatDirectNode(dstNode.getDevName());
            //todo: choose announce into what process? here dstNode is null
            disconnections.add(new IsisEdge(dstNode,directNode,null));
        }

        // test if any src process can reach dst nodes.
        boolean canReach = false;
        for (IsisNode srcNode : srcNodes){
            Set<IsisNode> reachableNodes =  Graphs.reachableNodes(prefixFwdGraph.asGraph(),srcNode);
            if (reachableNodes.contains(dstNode)){
                canReach = true;
            }
        }

        if (!canReach){
            List<Set<IsisEdge>> repairPlans = findMinimalRepairs(srcNodes,dstNode,prefixFwdGraph);
            System.out.println(repairPlans.size());
        }

        return false;
    }

    // find minimal repair to connect src and dst by BFS
    private static List<Set<IsisEdge>> findMinimalRepairs(List<IsisNode> srcNodes, IsisNode dstNode,
                                       MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        int maxDepth = 20;
        List<Set<IsisEdge>> repairPlans = new ArrayList<>();
        for (IsisNode srcNode : srcNodes){
            Set<IsisNode> srcReachableNodes =  Graphs.reachableNodes(prefixFwdGraph.asGraph(),srcNode);
            Set<IsisNode> dstReachableNodes =  reachableNodesToDst(prefixFwdGraph,dstNode);

            Queue<Pair<Set<IsisNode>,Set<IsisEdge>>> taskQueue = new LinkedList<>();
            Set<IsisEdge> edges= new HashSet<>();
            taskQueue.add(new ImmutablePair<>(srcReachableNodes,edges));

            // BFS main loop
            for (int depth = 0; depth < maxDepth && !taskQueue.isEmpty() ; depth++){
                while (!taskQueue.isEmpty()){
                    // has found repair plans, break
                    if (repairPlans.size() !=0){
                        break;
                    }

                    Pair<Set<IsisNode>,Set<IsisEdge>> task = taskQueue.poll();
                    Set<IsisNode> srcComponent = task.getLeft();
                    Set<IsisEdge> currentEdges = task.getRight();
                    // find edge to connect src and dst
                    List<IsisEdge> possibleEdgesOfSrcAndDst = findEdgeBetweenComponents(srcComponent,dstReachableNodes);

                    // if found, record all possible repair plans
                    if (!possibleEdgesOfSrcAndDst.isEmpty()){
                        for (IsisEdge possibleEdgeOfSrcAndDst : possibleEdgesOfSrcAndDst){
                            Set<IsisEdge> repairPlan = new HashSet<>(currentEdges);
                            repairPlan.add(possibleEdgeOfSrcAndDst);
                            repairPlans.add(repairPlan);
                        }
                    }
                    // else, connect src component with a left node, then find the repair plan in next iteration
                    else {
                        // has found repair plans in other task, do not try deeper search
                        if (repairPlans.size() != 0){
                            continue;
                        }
                        // connect with a left node and branch
                        Set<IsisNode> leftNodes = getLeftNodes(prefixFwdGraph,srcReachableNodes,dstReachableNodes);
                        List<IsisEdge> possibleEdgesOfSrcAndLeft = findEdgeBetweenComponents(srcComponent,leftNodes);
                        for (IsisEdge possibleEdgeOfSrcAndLeft: possibleEdgesOfSrcAndLeft){
                            Set<IsisNode> newSrcComponent = new HashSet<>();
                            Set<IsisNode> leftNodeComponent = Graphs.reachableNodes(prefixFwdGraph.asGraph(),
                                    possibleEdgeOfSrcAndLeft.getTarget());
                            newSrcComponent.addAll(leftNodeComponent);
                            Set<IsisEdge> newEdges = new HashSet<>(currentEdges);
                            newEdges.add(possibleEdgeOfSrcAndLeft);
                            taskQueue.add(new ImmutablePair<>(newSrcComponent,newEdges));
                        }
                    }
                }
            }
        }
        return repairPlans;
    }


    // find disconnection
    private static List<IsisEdge> findEdgeBetweenComponents(Set<IsisNode> srcReachableNodes,Set<IsisNode> dstReachableNodes){
        List<IsisEdge> possibleEdges = new ArrayList<>();
        for (IsisNode srcReachableNode: srcReachableNodes){
            for (IsisNode dstReachableNode: dstReachableNodes){
                //todo : physical topo
                if (srcReachableNode.getDevName() == dstReachableNode.getDevName()){
                    possibleEdges.add(new IsisEdge(srcReachableNode,dstReachableNode,null));
                }
            }
        }
        return possibleEdges;
    }

    private static IsisNode getDstNode(String devName, Set<IsisNode> nodes){
        IsisNode dstNode = null;
        for (IsisNode node: nodes){
            if (node.getDevName().equals(devName) && node.getIsisId() == IsisNode.DIRECT){
                dstNode = node;
                break;
            }
        }
        return dstNode;
    }

    private static List<IsisNode> getSrcNodes(String devName, Set<IsisNode> nodes){
        List<IsisNode> srcNodes = new ArrayList<>();
        for (IsisNode node: nodes){
            if (node.getDevName().equals(devName)){
                srcNodes.add(node);
            }
        }
        return srcNodes;
    }

    private static Set<IsisNode> reachableNodesToDst(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph, IsisNode dstNode){
        Set<IsisNode> reachableNodes  = new HashSet<>();
        Queue<IsisNode> queue = new LinkedList<>();
        queue.add(dstNode);
        while (!queue.isEmpty()){
            IsisNode current = queue.poll();
            Set<IsisNode> predecessors = prefixFwdGraph.predecessors(current);
            for (IsisNode predecessor : predecessors){
                if (!reachableNodes.contains(predecessor)){
                    queue.add(predecessor);
                    reachableNodes.add(predecessor);
                }
            }
        }
        return reachableNodes;
    }

    // get nodes which are not connected to dst and src, sorted by the number of nodes it can reach
    private static Set<IsisNode> getLeftNodes(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                              Set<IsisNode> srcReachableNodes, Set<IsisNode> dstReachableNodes){

        Set<IsisNode> leftNodes = new HashSet<>(prefixFwdGraph.nodes());
        leftNodes.removeAll(srcReachableNodes);
        leftNodes.removeAll(dstReachableNodes);
        return leftNodes;
    }

}
