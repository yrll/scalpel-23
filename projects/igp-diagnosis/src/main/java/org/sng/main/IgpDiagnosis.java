package org.sng.main;

import com.google.common.graph.Graphs;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.sng.datamodel.*;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;

import java.util.*;
import java.util.stream.Collectors;

public class IgpDiagnosis {

    private final MutableValueGraph<IsisNode,IsisEdgeValue> _newCommonGraph;
    private final Map<Prefix, Set<String>> _directRouteDevicesMap;

    /** 针对每个前缀的ISIS图 **/
    private final Map<Prefix,MutableValueGraph<IsisNode, IsisEdgeValue>> _prefixFwdGraphMap;

    private final Set<Pair<String,String>> _edgesNotPeering;

    public IgpDiagnosis(Layer1Topology layer1Topology, ValueGraph<IsisNode,IsisEdgeValue> commonFwdGraph,
                        Map<Prefix, List<IsisEdge>> prefixEdgesMap, Map<Prefix, Set<String>> directRouteDevicesMap) {

        // 发布直连路由的设备
        _directRouteDevicesMap = directRouteDevicesMap;

        // 获取还没有建立ISIS peer的边（减少搜索空间）
        _edgesNotPeering = getL1EdgesNotPeering(layer1Topology, commonFwdGraph);

        // 为没有ISIS进程的节点开启进程，保证能够找到连通方案
        _newCommonGraph = Graphs.copyOf(commonFwdGraph);
        enableIsisProcess(layer1Topology,_newCommonGraph);

        // 针对前缀计算ISIS图
        _prefixFwdGraphMap = getPrefixFwdGraphMap(_newCommonGraph, prefixEdgesMap);
    }


    public void igpDiagnosis(ErrorFlow errorFlow){
        // todo: 多源发节点 + 目的IP多前缀
        // 获取目的节点
        String srcDevice = errorFlow.getSrcDevice();
        Prefix dstPrefix = errorFlow.getPrefix();
        String dstDevice =  getDstDevice(dstPrefix);

        // 如果目的前缀没有被发布，加上对应的前缀节点
        if (!_prefixFwdGraphMap.containsKey(dstPrefix)){
            MutableValueGraph<IsisNode,IsisEdgeValue> dstPrefixGraph = Graphs.copyOf(_newCommonGraph);
            dstPrefixGraph.addNode(IsisNode.creatDirectNode(dstDevice));
            _prefixFwdGraphMap.put(dstPrefix,dstPrefixGraph);
        }
        MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph = _prefixFwdGraphMap.get(dstPrefix);

        // 获取源节点和目的前缀
        List<IsisNode> srcNodes = getSrcNodes(srcDevice, prefixFwdGraph.nodes());
        IsisNode dstNode = getDstNode(dstDevice, prefixFwdGraph.nodes());

        // 判断源节点和目的前缀在ISIS graph上可达（路由可达）
        boolean routeReachability = isSrcToPrefixConnected(srcNodes,dstNode,prefixFwdGraph);

        // 如果路由可达，诊断转发错误
        //todo: 基于common graph找最短路径

        // 否则，诊断路由错误
        List<Set<IsisEdge>> repairs = new ArrayList<>();
        if (!routeReachability){
            MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge = Graphs.copyOf(prefixFwdGraph);
            boolean hasRoutePath = isSrcToPrefixConnected(srcNodes,dstNode,prefixFwdGraphWithImportEdge);
            // 如果前缀没有路径到达源节点，构建路径
            if(!hasRoutePath){
                List<IsisEdge> candidateEdges = computeCandidateEdges(_edgesNotPeering,prefixFwdGraphWithImportEdge);
                repairs.addAll(findMinimalRepairs(srcNodes,dstNode,prefixFwdGraph,candidateEdges));
            }
            // 否则，诊断路由导入错误
            else {

            }
        }

        // 输出
        int i = 0;
        for (Set<IsisEdge> repair : repairs){
            i++;
            System.out.println("repair"+i);
            for (IsisEdge isisEdge: repair){
                System.out.println(isisEdge.getSource().getDevName()+"-ISIS"+isisEdge.getSource().getIsisId()+"-->"
                        +isisEdge.getTarget().getDevName()+"-ISIS"+isisEdge.getTarget().getIsisId());
            }
        }

    }

    private String getDstDevice(Prefix dstPrefix){
        Set<String> dstDevices = _directRouteDevicesMap.get(dstPrefix);
        String dstDevice = null;
        if (dstDevices.size() == 0){
            System.out.println("没有设备源发该前缀，选择目的设备");
            Scanner scan = new Scanner(System.in);
            dstDevice =  scan.next();

        }
        else if (dstDevices.size() > 1){
            System.out.println("多个设备源发该前缀，在下列设备中选择目的设备");
            for (String devcie: dstDevices){
                System.out.println(devcie);
            }
            Scanner scan = new Scanner(System.in);
            dstDevice =  scan.next();
        }
        else {
            dstDevice = new ArrayList<>(dstDevices).get(0);
        }
        return dstDevice;
    }

    private boolean isSrcToPrefixConnected(List<IsisNode> srcNodes, IsisNode dstNode,
                                                  MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        boolean canReach = true;

        if (srcNodes.size() == 1 && srcNodes.get(0).getIsisId() == IsisNode.NEW_ISIS_PROCESS){
            canReach = false;
        }

        if (canReach){
            Set<IsisNode> reachableNodes = new HashSet<>();
            // 从dst找src，因为图的边是路由传播方向
            for (IsisNode srcNode : srcNodes){
                reachableNodes.addAll(reverseReachableNodes(prefixFwdGraph,srcNode));
            }
            if (!reachableNodes.contains(dstNode)){
                canReach = false;
            }
        }
        return canReach;
    }

    /** BFS构建连通路径 **/
    private List<Set<IsisEdge>> findMinimalRepairs(List<IsisNode> srcNodes, IsisNode dstNode,
                                                   MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                                   List<IsisEdge> candidateEdges){
        int maxDepth = 10;
        List<Set<IsisEdge>> repairs = new ArrayList<>();

        // 求初始状态的连通域
        Set<IsisNode> srcReachableNodes = new HashSet<>();
        for (IsisNode srcNode : srcNodes){
            srcReachableNodes.addAll(reverseReachableNodes(prefixFwdGraph,srcNode));
        }

        // 从源节点开始查找
        Queue<Pair<Set<IsisNode>,Set<IsisEdge>>> taskQueue = new LinkedList<>();
        Set<IsisEdge> edges= new HashSet<>();
        taskQueue.add(new ImmutablePair<>(srcReachableNodes,edges));

        // BFS主循环，每次循环选择一个边连接
        // todo: DFS，启发式选择修复方案
        for (int depth = 0; depth < maxDepth && !taskQueue.isEmpty() ; depth++){
            Queue<Pair<Set<IsisNode>,Set<IsisEdge>>> currentTaskQueue = new LinkedList<>(taskQueue);
            taskQueue.clear();
            while (!currentTaskQueue.isEmpty()){
                // 已经找到修复方案，不再搜索
                if (repairs.size() !=0){
                    break;
                }

                Pair<Set<IsisNode>,Set<IsisEdge>> task = currentTaskQueue.poll();
                Set<IsisNode> srcComponent = task.getLeft();
                Set<IsisEdge> currentEdges = task.getRight();

                // 遍历每条候选边
                for(IsisEdge candidateEdge: candidateEdges){
                    IsisNode candidateEdgeTail = candidateEdge.getTarget();
                    IsisNode candidateEdgeHead = candidateEdge.getSource();
                    // 如果候选边的尾部节点在src的连通域中，加上该边，判断是否能够连通。这里考虑尾部节点是因为图中的方向是路由传播方向
                    if (srcComponent.contains(candidateEdgeTail)){
                        Set<IsisNode> headComponent = reverseReachableNodes(prefixFwdGraph,candidateEdgeHead);
                        Set<IsisNode> newSrcComponent = new HashSet<>(srcComponent);
                        newSrcComponent.addAll(headComponent);
                        Set<IsisEdge> newEdges = new HashSet<>(currentEdges);
                        newEdges.add(candidateEdge);
                        // 加上边之后，源节点和目的前缀连通，即找到了连通方案
                        if (newSrcComponent.contains(dstNode)){
                            repairs.add(newEdges);
                        }
                        // 否则，在下次迭代过程中查找
                        else {
                            taskQueue.add(new ImmutablePair<>(newSrcComponent,newEdges));
                        }

                    }
                }
            }
        }

        return repairs;
    }

    /** 在ISIS邻居图的基础上，对每个prefix加上对应的路由导入边 **/
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

    /** 获取没有建立ISIS peer的物理连接 **/
    private Set<Pair<String,String>> getL1EdgesNotPeering(Layer1Topology layer1Topology,
                                                                 ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        // 获取物理拓扑和ISIS图的边
        Set<Pair<String,String>> l1DevicePairs = layer1Topology.getGraph().edges().stream()
                .map(layer1Edge ->
                        new ImmutablePair<>(layer1Edge.getNode1().getHostname(),layer1Edge.getNode2().getHostname()))
                .collect(Collectors.toSet());
        Set<Pair<String,String>> isisDevicePairs = isisFwdGraph.asGraph().edges().stream()
                .map(isisNodeEndpointPair ->
                        new ImmutablePair<>(isisNodeEndpointPair.source().getDevName(),isisNodeEndpointPair.target().getDevName()))
                .collect(Collectors.toSet());

        // 做差集
        Set<Pair<String, String>> edgesNotPeering = new HashSet<>(l1DevicePairs);
        edgesNotPeering.removeAll(isisDevicePairs);

        return edgesNotPeering;
    }


    /** 为没有ISIS进程的节点开启进程，保证能够找到连通路径。例如S-B-D，但B没有开启ISIS进程 **/
    private void enableIsisProcess(Layer1Topology layer1Topology,
                                   MutableValueGraph<IsisNode, IsisEdgeValue> newCommonGraph){
        Set<String> enabledDevice = newCommonGraph.nodes().stream().map(IsisNode::getDevName).collect(Collectors.toSet());
        Set<String> allDevice = layer1Topology.getGraph().nodes().stream().map(Layer1Node::getHostname).collect(Collectors.toSet());

        allDevice.removeAll(enabledDevice);
        for (String device: allDevice){
            // todo: 现在只考虑添加一个进程
            newCommonGraph.addNode(IsisNode.creatNewIsisNode(device));
        }
    }

    /** 基于物理拓扑和进程，计算候选边集合，用于构建路由传播路径 **/
    private List<IsisEdge> computeCandidateEdges(Set<Pair<String,String>> edgesNotPeering,
                                                MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge){
        List<IsisEdge> candidateEdges = new ArrayList<>();

        // 先获取每个节点的进程
        Map<String,List<IsisNode>> deviceProcessesMap = new HashMap<>();
        for (IsisNode isisNode: prefixFwdGraphWithImportEdge.nodes()){
            String deviceName = isisNode.getDevName();
            if (deviceProcessesMap.containsKey(deviceName)){
                deviceProcessesMap.get(deviceName).add(isisNode);
            }
            else {
                List<IsisNode> processes = new ArrayList<>();
                processes.add(isisNode);
                deviceProcessesMap.put(deviceName,processes);
            }
        }

        // 为没有建立ISIS邻居的边建立边
        for (Pair<String,String> edgeNotPeering :  edgesNotPeering){
            List<IsisEdge> peerEdges  = new ArrayList<>();
            List<IsisNode> srcProcesses = deviceProcessesMap.get(edgeNotPeering.getLeft());
            List<IsisNode> dstProcesses = deviceProcessesMap.get(edgeNotPeering.getRight());

            boolean hasSameId = false;
            for (IsisNode srcProcess: srcProcesses){
                for (IsisNode dstProcess: dstProcesses){
                    if (!hasSameId && srcProcess.getIsisId() == dstProcess.getIsisId()){
                        hasSameId = true;
                    }
                    peerEdges.add(new IsisEdge(srcProcess,dstProcess,null));
                    peerEdges.add(new IsisEdge(dstProcess,srcProcess,null));
                }
            }
            // 启发算法： 如果两个节点有相同ID的进程，只连接相同ID的
            if(hasSameId){
                for (int i = peerEdges.size()-1; i>=0 ;i--){
                    IsisEdge peerEdge = peerEdges.get(i);
                    if(peerEdge.getSource().getIsisId() != peerEdge.getTarget().getIsisId()){
                        peerEdges.remove(i);
                    }
                }
            }
            candidateEdges.addAll(peerEdges);
        }

        //为每个节点的不同进程建立边
        List<IsisEdge> importEdges = new ArrayList<>();
        for(Map.Entry<String,List<IsisNode>> deviceProcesses: deviceProcessesMap.entrySet()){
            List<IsisNode> processes =  deviceProcesses.getValue();
            for(int i = 0; i< processes.size() ; i++){
                for (int j = i + 1; j<processes.size(); j++){
                    if(!prefixFwdGraphWithImportEdge.hasEdgeConnecting(processes.get(i),processes.get(j))){
                        importEdges.add(new IsisEdge(processes.get(i),processes.get(j),null));
                    }
                    if(!prefixFwdGraphWithImportEdge.hasEdgeConnecting(processes.get(j),processes.get(i))){
                        importEdges.add(new IsisEdge(processes.get(j),processes.get(i),null));
                    }
                }
            }
        }
        candidateEdges.addAll(importEdges);

        return candidateEdges;
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

    /** 反向可达节点搜索 (BFS) **/
     private Set<IsisNode> reverseReachableNodes(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph, IsisNode dstNode){
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

    /** 根据IP获取源前缀 **/
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

}
