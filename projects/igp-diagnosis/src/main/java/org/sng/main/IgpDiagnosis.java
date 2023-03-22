package org.sng.main;

import com.google.common.graph.Graphs;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.sng.datamodel.Ip;
import org.sng.datamodel.Layer1Node;
import org.sng.datamodel.Layer1Topology;
import org.sng.datamodel.Prefix;
import org.sng.datamodel.ibgp.IBgpNode;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;

import java.util.*;
import java.util.stream.Collectors;

public class IgpDiagnosis {

    // ISIS adjacency graph
    private final ValueGraph<IsisNode,IsisEdgeValue> _commonFwdGraph;
    private final MutableValueGraph<IsisNode,IsisEdgeValue> _newCommonGraph;

    // ISIS forwarding graph for each prefix
    private final Map<Prefix,MutableValueGraph<IsisNode, IsisEdgeValue>> _prefixFwdGraphMap;

    private final Set<Pair<String,String>> _edgesNotPeering;



    public IgpDiagnosis(Layer1Topology layer1Topology, ValueGraph<IsisNode,IsisEdgeValue> commonFwdGraph, Map<Prefix, List<IsisEdge>> prefixEdgesMap) {
        _commonFwdGraph = commonFwdGraph;

        // get physical edge that have not established ISIS neighbor (help finding repair plan)
        _edgesNotPeering = getL1EdgesNotPeering(layer1Topology, commonFwdGraph);

        // generate new common graph to make sure that can compute a repair plan
        _newCommonGraph = Graphs.copyOf(commonFwdGraph);
        enableIsisProcess(layer1Topology,_newCommonGraph);

        // get forwarding graph for each prefix
        _prefixFwdGraphMap = getPrefixFwdGraphMap(_newCommonGraph, prefixEdgesMap);
    }


    public void igpDiagnosis(Map<IBgpNode,IBgpNode> peerMap, List<Prefix> origins){
        //todo: diagnose duplicate subnets (error due to route priority)
        // diagnose multiple prefixes? (such as /32 and /24)

        // diagnose reachability for each iBGP peer
        for (Map.Entry<IBgpNode,IBgpNode> entry: peerMap.entrySet()){
            IBgpNode srcIBgpNode = entry.getKey();
            IBgpNode dstIBgpNode = entry.getValue();
            Ip dstIp = dstIBgpNode.getIp();

            // get prefix of dst iBGP ip
            Prefix dstPrefix = getDstPrefix(dstIp,origins);

            // if prefix not imported, creat direct prefix node and corresponding graph
            if (!_prefixFwdGraphMap.containsKey(dstPrefix)){
                MutableValueGraph<IsisNode,IsisEdgeValue> dstPrefixGraph = Graphs.copyOf(_newCommonGraph);
                dstPrefixGraph.addNode(IsisNode.creatDirectNode(dstIBgpNode.getDevName()));
                _prefixFwdGraphMap.put(dstPrefix,dstPrefixGraph);
            }
            MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph = _prefixFwdGraphMap.get(dstPrefix);

            // compute connection between src processes and dst prefix
            List<IsisNode> srcNodes = getSrcNodes(srcIBgpNode.getDevName(), prefixFwdGraph.nodes());
            IsisNode dstNode = getDstNode(dstIBgpNode.getDevName(), prefixFwdGraph.nodes());
            boolean canReach = isSrcToPrefixConnected(srcNodes,dstNode,prefixFwdGraph);

            // find repair plans and diagnose errors
            int depth = 0;
            List<Set<IsisEdge>> repairPlans = new ArrayList<>();
            if (!canReach){
                repairPlans.addAll(findMinimalDisconnectionRepairs(srcNodes,dstNode,prefixFwdGraph));
                depth = repairPlans.get(0).size();
            }
            System.out.println(dstPrefix+"\t"+canReach+"\t"+depth);
        }
    }

    private boolean isSrcToPrefixConnected(List<IsisNode> srcNodes, IsisNode dstNode,
                                                  MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        boolean canReach = true;

        if (srcNodes.size() == 1 && srcNodes.get(0).getIsisId() == IsisNode.NEW_ISIS_PROCESS){
            canReach = false;
        }

        if (canReach){
            Set<IsisNode> reachableNodes = new HashSet<>();
            for (IsisNode srcNode : srcNodes){
                reachableNodes.addAll(Graphs.reachableNodes(prefixFwdGraph.asGraph(),srcNode));
            }
            if (!reachableNodes.contains(dstNode)){
                canReach = false;
            }
        }
        return canReach;
    }

    // find minimal repair to connect src and dst by BFS
    private List<Set<IsisEdge>> findMinimalDisconnectionRepairs(List<IsisNode> srcNodes, IsisNode dstNode,
                                                          MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        int maxDepth = 10;
        List<Set<IsisEdge>> repairPlans = new ArrayList<>();
        Set<IsisNode> srcReachableNodes = new HashSet<>();
        for (IsisNode srcNode : srcNodes){
            srcReachableNodes.addAll(Graphs.reachableNodes(prefixFwdGraph.asGraph(),srcNode));
        }
        Set<IsisNode> dstReachableNodes =  reachableNodesToDst(prefixFwdGraph,dstNode);

        Queue<Pair<Set<IsisNode>,Set<IsisEdge>>> taskQueue = new LinkedList<>();
        Set<IsisEdge> edges= new HashSet<>();
        taskQueue.add(new ImmutablePair<>(srcReachableNodes,edges));

        // BFS main loop
        for (int depth = 0; depth < maxDepth && !taskQueue.isEmpty() ; depth++){
            Queue<Pair<Set<IsisNode>,Set<IsisEdge>>> currentTaskQueue = new LinkedList<>(taskQueue);
            taskQueue.clear();
            while (!currentTaskQueue.isEmpty()){
                // has found minimal repair plans, break
                if (repairPlans.size() !=0){
                    break;
                }

                Pair<Set<IsisNode>,Set<IsisEdge>> task = currentTaskQueue.poll();
                Set<IsisNode> srcComponent = task.getLeft();
                Set<IsisEdge> currentEdges = task.getRight();
                // find edge to connect src and dst
                List<IsisEdge> repairEdgesOfSrcAndDst = findRepairEdgeBetweenComponents(srcComponent,dstReachableNodes,prefixFwdGraph);

                // if found, record all possible repair plans
                if (!repairEdgesOfSrcAndDst.isEmpty()){
                    for (IsisEdge repairEdgeOfSrcAndDst : repairEdgesOfSrcAndDst){
                        Set<IsisEdge> repairPlan = new HashSet<>(currentEdges);
                        repairPlan.add(repairEdgeOfSrcAndDst);
                        repairPlans.add(repairPlan);
                    }
                }
                // else, connect src component with a left node, then find the repair plan in next iteration
                else {
                    // connect with a left node and branch
                    Set<IsisNode> leftNodes = getLeftNodes(prefixFwdGraph,srcReachableNodes,dstReachableNodes);
                    List<IsisEdge> repairEdgesOfSrcAndLeft = findRepairEdgeBetweenComponents(srcComponent,leftNodes, prefixFwdGraph);
                    for (IsisEdge repairEdgeOfSrcAndLeft: repairEdgesOfSrcAndLeft){
                        Set<IsisNode> leftNodeComponent = Graphs.reachableNodes(prefixFwdGraph.asGraph(),
                                repairEdgeOfSrcAndLeft.getTarget());
                        Set<IsisNode> newSrcComponent = new HashSet<>(leftNodeComponent);
                        Set<IsisEdge> newEdges = new HashSet<>(currentEdges);
                        newEdges.add(repairEdgeOfSrcAndLeft);
                        taskQueue.add(new ImmutablePair<>(newSrcComponent,newEdges));
                    }
                }
            }
        }
        return repairPlans;
    }


    // find edge that connects two components (route import or establish ISIS neighbor)
    private List<IsisEdge> findRepairEdgeBetweenComponents(Set<IsisNode> srcReachableNodes, Set<IsisNode> dstReachableNodes,
                                                           MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        List<IsisEdge> repairEdges = new ArrayList<>();
        for (IsisNode srcReachableNode: srcReachableNodes){
            for (IsisNode dstReachableNode: dstReachableNodes){
                // route import between ISIS processes
                if (srcReachableNode.getDevName().equals(dstReachableNode.getDevName())){
                    repairEdges.add(new IsisEdge(srcReachableNode,dstReachableNode,null));
                }
                // establish ISIS neighbor between devices
                else {
                    if (!_edgesNotPeering.contains(new ImmutablePair<>(srcReachableNode.getDevName(),dstReachableNode.getDevName()))){
                        continue;
                    }
                    // heuristic: if two devices have processes with same ID, only connect them.
                    if (hasProcessesWithSameId(srcReachableNode.getDevName(),dstReachableNode.getDevName(),prefixFwdGraph.nodes())){
                        if (srcReachableNode.getIsisId() == dstReachableNode.getIsisId()){
                            repairEdges.add(new IsisEdge(srcReachableNode,dstReachableNode,null));
                        }
                    }
                    // Otherwise, connect two processes directly
                    else {
                        repairEdges.add(new IsisEdge(srcReachableNode,dstReachableNode,null));
                    }
                }
            }
        }
        return repairEdges;
    }

    // complete forwarding graph for each prefix.
    private Map<Prefix,MutableValueGraph<IsisNode, IsisEdgeValue>> getPrefixFwdGraphMap(ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph,
                                                                      Map<Prefix, List<IsisEdge>> prefixEdgesMap){
        Map<Prefix,MutableValueGraph<IsisNode, IsisEdgeValue>> prefixFwdGraphMap = new HashMap<>();
        for (Map.Entry<Prefix, List<IsisEdge>> entry: prefixEdgesMap.entrySet()){
            Prefix prefix =  entry.getKey();
            MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph = Graphs.copyOf(isisFwdGraph);
            entry.getValue().forEach(isisEdge ->
                    prefixFwdGraph.putEdgeValue(isisEdge.getSource(),isisEdge.getTarget(),isisEdge.getEdgeValue()));
            prefixFwdGraphMap.put(prefix,prefixFwdGraph);
        }
        return prefixFwdGraphMap;
    }



    // get physical edges that have not established ISIS neighbor
    private Set<Pair<String,String>> getL1EdgesNotPeering(Layer1Topology layer1Topology,
                                                                 ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){

        // get device pairs of l1Topology and isis forwarding graph respectively
        Set<Pair<String,String>> l1DevicePairs = layer1Topology.getGraph().edges().stream()
                .map(layer1Edge ->
                        new ImmutablePair<>(layer1Edge.getNode1().getHostname(),layer1Edge.getNode2().getHostname()))
                .collect(Collectors.toSet());
        Set<Pair<String,String>> isisDevicePairs = isisFwdGraph.asGraph().edges().stream()
                .map(isisNodeEndpointPair ->
                        new ImmutablePair<>(isisNodeEndpointPair.source().getDevName(),isisNodeEndpointPair.target().getDevName()))
                .collect(Collectors.toSet());

        // find the edges that do not establish ISIS neighbor
        Set<Pair<String, String>> edgesNotPeering = new HashSet<>(l1DevicePairs);
        edgesNotPeering.removeAll(isisDevicePairs);

        return edgesNotPeering;
    }

    // enable ISIS process for nodes that are not enabled, to make sure that can find a repair plan
    // such as S-B-D, and B does not have any ISIS process
    private void enableIsisProcess(Layer1Topology layer1Topology,
                                   MutableValueGraph<IsisNode, IsisEdgeValue> newCommonGraph){
        Set<String> enabledDevice = newCommonGraph.nodes().stream().map(IsisNode::getDevName).collect(Collectors.toSet());
        Set<String> allDevice = layer1Topology.getGraph().nodes().stream().map(Layer1Node::getHostname).collect(Collectors.toSet());

        allDevice.removeAll(enabledDevice);
        for (String device: allDevice){
            // todo: only add one process now, maybe add multiple processes
            newCommonGraph.addNode(IsisNode.creatNewIsisNode(device));
        }
    }

    //todo: cache
    private boolean hasProcessesWithSameId(String device1, String device2, Set<IsisNode> isisNodes){
        Set<Integer> device1IsisIds = new HashSet<>();
        Set<Integer> device2IsisIds = new HashSet<>();

        for (IsisNode isisNode: isisNodes){
            if (isisNode.getDevName().equals(device1)){
                device1IsisIds.add(isisNode.getIsisId());
            }
            if (isisNode.getDevName().equals(device2)){
                device2IsisIds.add(isisNode.getIsisId());
            }
        }

        return !Collections.disjoint(device1IsisIds,device2IsisIds);
    }

    // get origin prefixes for dst ip
    private Prefix getDstPrefix(Ip dstIp, List<Prefix> origins){

        List<Prefix> dstPrefixList = new ArrayList<>();
        Prefix dstPrefix = null;

        for (Prefix origin : origins){
            if (origin.containsIp(dstIp)){
                dstPrefixList.add(origin);
                if (origin.getPrefixLength() == 32){
                    dstPrefix = origin;
                }
            }
        }

        if (dstPrefixList.size() == 0 || dstPrefix == null){
            System.out.println("dst ip\t"+dstIp+"\thas not origin prefix\t");
        }

        if (dstPrefixList.size() > 1){
            System.out.println("dst ip has multiple prefixes\t"+dstPrefixList);
        }

        return dstPrefix;
    }

    private IsisNode getDstNode(String devName, Set<IsisNode> nodes){
        IsisNode dstNode = null;
        for (IsisNode node: nodes){
            if (node.getDevName().equals(devName) && node.getIsisId() == IsisNode.DIRECT){
                dstNode = node;
                break;
            }
        }
        return dstNode;
    }

    private List<IsisNode> getSrcNodes(String devName, Set<IsisNode> nodes){
        List<IsisNode> srcNodes = new ArrayList<>();
        for (IsisNode node: nodes){
            if (node.getDevName().equals(devName)){
                srcNodes.add(node);
            }
        }
        return srcNodes;
    }

    private Set<IsisNode> reachableNodesToDst(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph, IsisNode dstNode){
        Set<IsisNode> reachableNodes  = new HashSet<>();
        reachableNodes.add(dstNode);
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
    private Set<IsisNode> getLeftNodes(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                              Set<IsisNode> srcReachableNodes, Set<IsisNode> dstReachableNodes){

        Set<IsisNode> leftNodes = new HashSet<>(prefixFwdGraph.nodes());
        leftNodes.removeAll(srcReachableNodes);
        leftNodes.removeAll(dstReachableNodes);
        return leftNodes;
    }

}
