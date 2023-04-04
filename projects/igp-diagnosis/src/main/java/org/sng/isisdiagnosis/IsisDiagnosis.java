package org.sng.isisdiagnosis;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.sng.datamodel.*;
import org.sng.datamodel.configuration.Configuration;
import org.sng.datamodel.configuration.Interface;
import org.sng.datamodel.configuration.IsisConfiguration;
import org.sng.datamodel.configuration.IsisRouteImport;
import org.sng.datamodel.isis.IsisEdge;
import org.sng.datamodel.isis.IsisEdgeValue;
import org.sng.datamodel.isis.IsisNode;

import java.util.*;
import java.util.stream.Collectors;

public class IsisDiagnosis {

    private final Layer1Topology _layer1Topology;
    private final Map<String, Configuration> _configurations;
    private final MutableValueGraph<IsisNode,IsisEdgeValue> _newCommonGraph;
    private final Map<Prefix, Set<String>> _directRouteDevicesMap;

    private final Map<Prefix, List<IsisEdge>> _prefixEdgesMap;

    /** 针对每个前缀的ISIS图 **/
    private final Map<Prefix,MutableValueGraph<IsisNode, IsisEdgeValue>> _prefixFwdGraphMap;

    private final Set<Pair<String,String>> _edgesNotPeering;

    public IsisDiagnosis(Layer1Topology layer1Topology, Map<String, Configuration> configurations,
                         ValueGraph<IsisNode,IsisEdgeValue> commonFwdGraph, Map<Prefix, List<IsisEdge>> prefixEdgesMap,
                         Map<Prefix, Set<String>> directRouteDevicesMap) {
        _layer1Topology = layer1Topology;

        _configurations = configurations;

        // 每个前缀对应的路由导入结果
        _prefixEdgesMap = prefixEdgesMap;

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
        // todo: 目的IP多前缀
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

        // 如果路由可达，诊断转发错误。目前仅考虑了多源发前缀错误，由错误路由导入导致，修复方案为删除对应路由导入。
        if (routeReachability){
            List<IsisEdge> deleteImports = forwardingDiagnosis(srcNodes,dstNode,dstPrefix,prefixFwdGraph);
            for (IsisEdge edge: deleteImports){
                System.out.println(edge.getSource().getDevName()+"-("+edge.getSource().getIsisId()+")\t"
                        +edge.getTarget().getDevName()+"-("+edge.getTarget().getIsisId()+")");
            }
        }
        // 否则，诊断路由错误
        else {
            // 在ISIS图上加上没有生效的引入进程
            MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge = Graphs.copyOf(prefixFwdGraph);
            List<IsisEdge> invalidImportEdges = getInvalidImportEdge(prefixFwdGraph,dstPrefix);
            for (IsisEdge invalidImportEdge: invalidImportEdges){
                prefixFwdGraphWithImportEdge.putEdgeValue(invalidImportEdge.getSource(),invalidImportEdge.getTarget(),invalidImportEdge.getEdgeValue());
            }
            boolean hasRoutePath = isSrcToPrefixConnected(srcNodes,dstNode,prefixFwdGraphWithImportEdge);
            // 如果前缀没有路径到达源节点，构建路径（BFS）
            if(!hasRoutePath){
                List<IsisEdge> candidateEdges = computeCandidateEdges(_edgesNotPeering,prefixFwdGraphWithImportEdge);
                List<Set<IsisEdge>> connectEdges = new ArrayList<>(findMinimalConnectEdges(srcNodes, dstNode, prefixFwdGraph, candidateEdges));
                connectEdgeDiagnosis(connectEdges);

            }
            // 否则，诊断路由导入错误（路由路径上某条边由于导入失败断开）
            else {
                List<IsisEdge> breakPoints = findImportBreakPoints(srcNodes,dstNode,dstPrefix,prefixFwdGraph,prefixFwdGraphWithImportEdge);

            }
        }
    }

    private String getDstDevice(Prefix dstPrefix){
        Set<String> dstDevices = _directRouteDevicesMap.get(dstPrefix);
        String dstDevice;
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

    /** 判断源节点能否到达目的前缀 **/
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

    /** 根据配置得到所有的进程间路由引入，对比ISIS转发图得到未生效的路由引入 **/
    private List<IsisEdge> getInvalidImportEdge(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph, Prefix dstPrefix){
        List<IsisEdge> existingImportEdges = _prefixEdgesMap.get(dstPrefix);
        List<IsisEdge> invalidImportEdges = new ArrayList<>();
        for (Map.Entry<String,Configuration> configEntry: _configurations.entrySet()){
            String deviceName = configEntry.getKey();
            Configuration configuration = configEntry.getValue();
            for (IsisConfiguration isisConfiguration:configuration.getIsisConfigurations()){
                IsisNode dstProcess = getNodeFromGrah(deviceName,isisConfiguration.getIsisId(),prefixFwdGraph);
                // ISIS图中包含了所有配置的进程
                assert dstProcess != null;
                for (IsisRouteImport isisRouteImport : isisConfiguration.getImportRoutes()){
                    // todo：目前只考虑ISIS进程间交互
                    if (isisRouteImport.getProtocol().equals("ISIS")){
                        IsisNode srcProcess = getNodeFromGrah(deviceName,isisRouteImport.getProtocolId(),prefixFwdGraph);
                        IsisEdge isisEdge = new IsisEdge(srcProcess,dstProcess,new IsisEdgeValue(null,null,isisRouteImport.getCost()));
                        if (!existingImportEdges.contains(isisEdge)){
                            invalidImportEdges.add(isisEdge);
                        }
                    }
                }
            }
        }
        return invalidImportEdges;
    }

    /** BFS构建连通路径 **/
    private List<Set<IsisEdge>> findMinimalConnectEdges(List<IsisNode> srcNodes, IsisNode dstNode,
                                                   MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                                   List<IsisEdge> candidateEdges){
        int maxDepth = 5;
        List<Set<IsisEdge>> connectEdges = new ArrayList<>();

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
                if (connectEdges.size() !=0){
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
                            connectEdges.add(newEdges);
                        }
                        // 否则，在下次迭代过程中查找
                        else {
                            taskQueue.add(new ImmutablePair<>(newSrcComponent,newEdges));
                        }

                    }
                }
            }
        }

        return connectEdges;
    }

    private List<IsisEdge> findImportBreakPoints(List<IsisNode> srcNodes, IsisNode dstNode, Prefix dstPrefix,
                                                 MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                                 MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge){
        List<IsisEdge> breakPoints = new ArrayList<>();
        List<List<IsisNode>> paths = new ArrayList<>();
        for (IsisNode srcNode: srcNodes){
            paths.addAll(computeForwardingPath(srcNode,dstPrefix,prefixFwdGraphWithImportEdge));
        }
        Set<IsisNode> routeReachableNodes = Graphs.reachableNodes(prefixFwdGraph.asGraph(),dstNode);
        for (List<IsisNode> path : paths){
            // 前缀不是指定节点原发的
            if (!path.get(path.size()-1).equals(dstNode)){
                continue;
            }
            IsisNode head = path.get(0);
            for (int i = 1; i < path.size(); i++ ){
                IsisNode tail = path.get(i);
                if (routeReachableNodes.contains(tail) && !routeReachableNodes.contains(head)){
                    breakPoints.add(new IsisEdge(tail,head,null));
                    break;
                }
                head = tail;
            }
        }
        return breakPoints;
    }

    private List<IsisEdge> forwardingDiagnosis(List<IsisNode> srcNodes, IsisNode dstNode, Prefix dstPrefix
            , MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        List<List<IsisNode>> paths = new ArrayList<>();
        for (IsisNode srcNode: srcNodes){
            paths.addAll(computeForwardingPath(srcNode,dstPrefix,prefixFwdGraph));
        }
        //如果最优路径的终点不是目的节点，找到对应的import节点并删除
        List<IsisEdge> repairs = new ArrayList<>();
        PriorityQueue<List<IsisNode>> pq = new PriorityQueue<>(paths.size(), Comparator.comparingInt(path -> computePathWeight(path,prefixFwdGraph)));
        pq.addAll(paths);
        while (!pq.isEmpty()){
            List<IsisNode> path = pq.poll();
            IsisEdge importEdge = getImportNodeOfPath(path);
            if (!path.get(path.size()-1).equals(dstNode)){
                repairs.add(importEdge);
                printPath(path,prefixFwdGraph);
            }
            else {
                break;
            }
        }
        return repairs;
    }

    /** 给定源节点，计算到达目的前缀转发路径 **/
    private List<List<IsisNode>> computeForwardingPath(IsisNode srcNode, Prefix dstPrefix,ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        //找到_newCommonGraph内对应的源和目的节点
        IsisNode srcNodeInCommonGraph = getNodeFromGrah(srcNode.getDevName(),srcNode.getIsisId(),_newCommonGraph);

        List<List<IsisNode>> paths = new ArrayList<>();
        Set<IsisNode> sameInstanceNodes = reverseReachableNodes(_newCommonGraph,srcNodeInCommonGraph);
        for (IsisEdge importEdge: _prefixEdgesMap.get(dstPrefix)){
            IsisNode importNode = importEdge.getTarget();
            // 找到引入节点，使用SPF计算源节点到各引入节点的路径
            if (sameInstanceNodes.contains(importNode)){
                List<IsisNode> shortestPath = dijkstra(srcNode,importNode,isisFwdGraph);
                IsisNode originNode = importEdge.getSource();
                shortestPath.add(originNode);
                // 如果引入节点是源发节点，返回结果
                if (originNode.getIsisId() == IsisNode.DIRECT ){
                        paths.add(shortestPath);
                }
                // 否则，递归查找下一个ISIS实例的路径，与当前的路径进行组合
                else {
                    List<List<IsisNode>> followPaths = computeForwardingPath(originNode,dstPrefix,isisFwdGraph);
                    for (List<IsisNode> followPath: followPaths){
                        List<IsisNode> combinePath = new ArrayList<>(shortestPath);
                        combinePath.addAll(followPath);
                        paths.add(combinePath);
                    }
                }
            }
        }
        return paths;
    }

    /** 使用Dijkstra算法寻找在同一个ISIS实例下的最短路径 **/
    private List<IsisNode> dijkstra(IsisNode srcNode, IsisNode dstNode, ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        List<IsisNode> path = new ArrayList<>();
        Map<IsisNode,Integer>  distances = new HashMap<>();
        Map<IsisNode,IsisNode> prevMap = new HashMap<>();
        for (IsisNode node: isisFwdGraph.nodes()){
            distances.put(node,Integer.MAX_VALUE);
            prevMap.put(node,null);
        }
        distances.put(srcNode,0);
        PriorityQueue<IsisNode> pq = new PriorityQueue<>(isisFwdGraph.nodes().size(), Comparator.comparingInt(distances::get));
        pq.add(srcNode);

        while (!pq.isEmpty()){
            IsisNode currentNode  = pq.poll();
            if (currentNode.equals(dstNode)){
                break;
            }
            for (EndpointPair<IsisNode> adjacentEdge : isisFwdGraph.incidentEdges(currentNode)) {
                IsisNode nextNode = adjacentEdge.source();
                // 只考虑同一实例内的节点
                if (currentNode.getDevName().equals(nextNode.getDevName())){
                    continue;
                }
                int weight = isisFwdGraph.edgeValue(adjacentEdge).get().getCost();
                int tentativeDistance = distances.get(currentNode) + weight;
                if (tentativeDistance < distances.get(nextNode)) {
                    distances.put(nextNode, tentativeDistance);
                    prevMap.put(nextNode, currentNode);
                    pq.remove(nextNode);
                    pq.add(nextNode);
                }
            }
        }

        for (IsisNode node = dstNode; node != null; node = prevMap.get(node)) {
            path.add(node);
        }
        Collections.reverse(path);
        return path;
    }

    private int computePathWeight(List<IsisNode> path,ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        int weight = 0;
        IsisNode head = path.get(0);
        for (int i = 1; i < path.size(); i++ ){
            IsisNode tail = path.get(i);
            weight = weight +isisFwdGraph.edgeValue(tail,head).get().getCost();
            // 到达了路由导入节点
            if (head.getDevName().equals(tail.getDevName())){
                break;
            }
            head = tail;
        }
        return weight;
    }

    /** 获取当前路径上的第一个导入节点 **/
    private IsisEdge getImportNodeOfPath(List<IsisNode> path){
        IsisEdge importEdge = null;
        IsisNode head = path.get(0);
        for (int i = 1; i < path.size(); i++ ){
            IsisNode tail = path.get(i);
            if (head.getDevName().equals(tail.getDevName())){
                importEdge = new IsisEdge(tail,head,null);
                break;
            }
            head = tail;
        }
        return importEdge;
    }

    private void printPath(List<IsisNode> path,ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        IsisNode head = path.get(0);
        System.out.print(head.getDevName()+"-"+head.getIsisId());
        for (int i = 1; i < path.size(); i++ ){
            System.out.print("-->(");
            IsisNode tail = path.get(i);
            int cost = isisFwdGraph.edgeValue(tail,head).get().getCost();
            System.out.print(cost+")-->"+tail.getDevName()+"-"+tail.getIsisId());
            if (head.getDevName().equals(tail.getDevName())){
                break;
            }
            head = tail;
        }
        System.out.print("\n");
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
        // todo: 这里假设了ISIS图中包含了所有开启的ISIS进程，即与配置的是一致的
        allDevice.removeAll(enabledDevice);
        for (String device: allDevice){
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

    /** 根据目的设备名称，找到目的前缀 **/
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

    private IsisNode getNodeFromGrah(String devName,Integer isisID, ValueGraph<IsisNode, IsisEdgeValue> graph){
        IsisNode node = null;
        for (IsisNode isisNode: graph.nodes()){
            if (isisNode.getDevName().equals(devName) && isisNode.getIsisId() == isisID){
                node = isisNode;
            }
        }
        return node;
    }

    /** 反向可达节点搜索 (BFS) **/
     private Set<IsisNode> reverseReachableNodes(ValueGraph<IsisNode, IsisEdgeValue> graph, IsisNode node){
        Set<IsisNode> reachableNodes  = new HashSet<>();
        reachableNodes.add(node);
        Queue<IsisNode> queue = new LinkedList<>();
        queue.add(node);
        while (!queue.isEmpty()){
            IsisNode current = queue.poll();
            Set<IsisNode> predecessors = graph.predecessors(current);
            for (IsisNode predecessor : predecessors){
                if (!reachableNodes.contains(predecessor)){
                    queue.add(predecessor);
                    reachableNodes.add(predecessor);
                }
            }
        }
        return reachableNodes;
    }

    /** 根据配置找到添加ISIS邻居或进程间路由导入的修复方案 **/
    private void connectEdgeDiagnosis(List<Set<IsisEdge>> connectEdges){
        int i =0;
        for (Set<IsisEdge> connectEdgeList:connectEdges){
            i++;
            System.out.println("-------------修复方案"+i+"-------------");
            for (IsisEdge edge: connectEdgeList){
                IsisNode head = edge.getSource();
                IsisNode tail = edge.getTarget();
                // 修改配置使得ISIS进程间路由导入成功
                if (head.getDevName().equals(tail.getDevName())){
                    continue;
                }
                // 修改配置使得ISIS邻居能够建立
                else {
                    System.out.println(head.getDevName()+"与"+tail.getDevName()+"建立ISIS邻居:");
                    List<String> errorConfigs = isisIpv4PeerDiagnosis(head,tail);
                    for (String errorConfig : errorConfigs){
                        System.out.println("*\t"+errorConfig);
                    }
                }
            }
        }
    }

    private List<String> isisIpv4PeerDiagnosis(IsisNode head, IsisNode tail){
        String headDeviceName = head.getDevName();
        String tailDevName = tail.getDevName();

        Configuration headConfig = _configurations.get(headDeviceName);
        Configuration tailConfig = _configurations.get(tailDevName);
        List<Layer1Edge> layer1Edges = getLayer1Edges(headDeviceName, tailDevName);

        // 返回最小的配置变更方案
        List<String> errorConfigs = new ArrayList<>();
        Set<Layer1Edge> ethTrunks = new HashSet<>();

        // 考虑多个物理连接情况
        for (Layer1Edge layer1Edge: layer1Edges){
            Interface headIfaceConfig = getIfaceConfig(layer1Edge.getNode1().getInterfaceName(),headConfig);
            Interface tailIfaceConfig = getIfaceConfig(layer1Edge.getNode2().getInterfaceName(),tailConfig);
            List<IsisErrorType.IsisPeerErrorType> errorTypeList = new ArrayList<>();

            // 有一边端口没有配置
            if (headIfaceConfig == null || tailIfaceConfig == null){
                if (headIfaceConfig == null ) {
                    errorTypeList.add(IsisErrorType.IsisPeerErrorType.IS_HEAD_INTERFACE_UP);
                }
                if (tailIfaceConfig  == null){
                    errorTypeList.add(IsisErrorType.IsisPeerErrorType.IS_TAIL_IFACE_STATE_UP);
                }
            }
            // 只有一个端口配置了Eth-trunk
            else if (headIfaceConfig.getPhyIfOrEthTrunk().size() != tailIfaceConfig.getPhyIfOrEthTrunk().size()){
                if (headIfaceConfig.getPhyIfOrEthTrunk().size() == 0){
                    errorTypeList.add(IsisErrorType.IsisPeerErrorType.HEAD_ETH_TRUNK_COINCIDE);
                }
                if (tailIfaceConfig.getPhyIfOrEthTrunk().size() == 0){
                    errorTypeList.add(IsisErrorType.IsisPeerErrorType.TAIL_ETH_TRUNK_COINCIDE);
                }
            }
            // 两端配置一样，判断接口配置
            else {
                // 如果配置了eth-trunk，取出对应配置用于判断
                boolean isEthTrunkValid = true;
                if (!headIfaceConfig.getPhyIfOrEthTrunk().isEmpty() && !tailIfaceConfig.getPhyIfOrEthTrunk().isEmpty()){
                    String headEthTrunkName = headIfaceConfig.getPhyIfOrEthTrunk().stream().findFirst().get();
                    String tailEthTrunkName = tailIfaceConfig.getPhyIfOrEthTrunk().stream().findFirst().get();
                    Layer1Edge ethTrunkEdge = new Layer1Edge(new Layer1Node(head.getDevName(),headEthTrunkName), new Layer1Node(tail.getDevName(),tailEthTrunkName));
                    // eth-trunk口已经判断过
                    if (ethTrunks.contains(ethTrunkEdge)){
                        continue;
                    }
                    headIfaceConfig = getIfaceConfig(headEthTrunkName,headConfig);
                    tailIfaceConfig = getIfaceConfig(tailEthTrunkName,tailConfig);
                    if (headIfaceConfig == null || tailIfaceConfig == null){
                        if (headIfaceConfig == null ) {
                            errorTypeList.add(IsisErrorType.IsisPeerErrorType.IS_HEAD_INTERFACE_UP);
                        }
                        if (tailIfaceConfig  == null){
                            errorTypeList.add(IsisErrorType.IsisPeerErrorType.IS_TAIL_IFACE_STATE_UP);
                        }
                        isEthTrunkValid = false;
                    }
                    ethTrunks.add(ethTrunkEdge);
                }

                // 保证Interface配置存在
                if (isEthTrunkValid){
                    // 考虑端口的子接口
                    List<Interface> headIfaceSubIfaces = new ArrayList<>();
                    List<Interface> tailIfaceSubIfaces = new ArrayList<>();
                    headIfaceSubIfaces.add(headIfaceConfig);
                    tailIfaceSubIfaces.add(tailIfaceConfig);
                    if (!headIfaceConfig.getIsShutdown()){
                        String headIfaceName = headIfaceConfig.getName();
                        headIfaceSubIfaces.addAll(headConfig.getInterfaces().stream().filter(i -> i.getName().contains(headIfaceName+".")).collect(Collectors.toList()));
                    }
                    if (!tailIfaceConfig.getIsShutdown()){
                        String tailIfaceName = tailIfaceConfig.getName();
                        tailIfaceSubIfaces.addAll(tailConfig.getInterfaces().stream().filter(i -> i.getName().contains(tailIfaceName+".")).collect(Collectors.toList()));
                    }
                    int errorWeight = Integer.MAX_VALUE;
                    for (Interface headSubIface: headIfaceSubIfaces){
                        for (Interface tailSubIface: tailIfaceSubIfaces){
                            List<IsisErrorType.IsisPeerErrorType> subIfacePeerErrors = isisIpv4PeerIfaceDiagnosis(headConfig,headSubIface,tailConfig,tailSubIface);
                            if(subIfacePeerErrors.size() < errorWeight){
                                errorTypeList = subIfacePeerErrors;
                            }
                        }
                    }
                }
            }

            // 启发算法：在多种错误情况中选择错误数量最少的
            if (errorConfigs.size() == 0 || errorTypeList.size() < errorConfigs.size()){
                errorConfigs.clear();
                for (IsisErrorType.IsisPeerErrorType errorType: errorTypeList){
                    errorConfigs.addAll(mapIsisPeerErrorToConfig(errorType,headConfig, headIfaceConfig,tailConfig,tailIfaceConfig));
                }
            }
        }

        return errorConfigs;
    }

    private List<IsisErrorType.IsisPeerErrorType> isisIpv4PeerIfaceDiagnosis(Configuration headConfig,Interface headIfaceConfig,
                                                                             Configuration tailConfig, Interface tailIfaceConfig){
        Map<IsisErrorType.IsisPeerErrorType,Boolean> predicates = new HashMap<>();
        boolean interfaceMtuCompare = headIfaceConfig.getIpv6MtuAndSpread().equals(tailIfaceConfig.getIpv6MtuAndSpread());
        boolean isisHeadEnable = headIfaceConfig.getIsisEnable() !=null;
        boolean isisTailEnable = tailIfaceConfig.getIsisEnable() !=null;
        boolean isisHeadProcConfigValid = true;
        boolean isisTailProcConfigValid = true;
        if (isisHeadEnable){
            int headIsisId = headIfaceConfig.getIsisEnable();
            isisHeadProcConfigValid = headConfig.getIsisConfigurations().stream().anyMatch(isisConfig -> (isisConfig.getIsisId()== headIsisId && isisConfig.getNetworkEntity()!=null));
        }
        if (isisTailEnable){
            int tailIsisId = tailIfaceConfig.getIsisEnable();
            isisTailProcConfigValid = tailConfig.getIsisConfigurations().stream().anyMatch(isisConfig -> (isisConfig.getIsisId()== tailIsisId && isisConfig.getNetworkEntity()!=null));
        }
        boolean circuitTypeCoincide = headIfaceConfig.getCircuitTypeP2P().equals(tailIfaceConfig.getCircuitTypeP2P());
        boolean isisPeerNoSilent = !headIfaceConfig.getIsisSilent2ZeroCost() && !tailIfaceConfig.getIsisSilent2ZeroCost();
        boolean isHeadIfaceShutDown = !headIfaceConfig.getIsShutdown();
        boolean isTailIfaceShutDown = !tailIfaceConfig.getIsShutdown();
        boolean isHeadIfaceStateUp = headIfaceConfig.getOriginalAddress()!=null;
        boolean isTailIfaceStateUp = tailIfaceConfig.getOriginalAddress()!=null;
        boolean isSameVlan = Objects.equals(headIfaceConfig.getVlanTypeDotLq(), tailIfaceConfig.getVlanTypeDotLq());
        boolean isSameSubnet = true;
        if (isHeadIfaceStateUp && isTailIfaceStateUp){
            Prefix headIfaceSubnet = headIfaceConfig.getOriginalAddress();
            Prefix tailIfaceSubnet = tailIfaceConfig.getOriginalAddress();
            isSameSubnet = headIfaceSubnet.containsIp(tailIfaceSubnet.getStartIp()) && tailIfaceSubnet.containsIp(headIfaceSubnet.getStartIp());
        }
        predicates.put(IsisErrorType.IsisPeerErrorType.INTERFACE_MTU_COMPARE,interfaceMtuCompare);
        predicates.put(IsisErrorType.IsisPeerErrorType.ISIS_HEAD_ENABLE,isisHeadEnable);
        predicates.put(IsisErrorType.IsisPeerErrorType.ISIS_TAIL_ENABLE,isisTailEnable);
        predicates.put(IsisErrorType.IsisPeerErrorType.CIRCUIT_TYPE_COINCIDE,circuitTypeCoincide);
        predicates.put(IsisErrorType.IsisPeerErrorType.ISIS_HEAD_PROC_CONFIG_VALID,isisHeadProcConfigValid);
        predicates.put(IsisErrorType.IsisPeerErrorType.ISIS_TAIL_PROC_CONFIG_VALID,isisTailProcConfigValid);
        predicates.put(IsisErrorType.IsisPeerErrorType.ISIS_PEER_NO_SILENT,isisPeerNoSilent);
        predicates.put(IsisErrorType.IsisPeerErrorType.IS_HEAD_IFACE_SHUT_DOWN,isHeadIfaceShutDown);
        predicates.put(IsisErrorType.IsisPeerErrorType.IS_TAIL_IFACE_SHUT_DOWN,isTailIfaceShutDown);
        predicates.put(IsisErrorType.IsisPeerErrorType.IS_HEAD_IFACE_STATE_UP,isHeadIfaceStateUp);
        predicates.put(IsisErrorType.IsisPeerErrorType.IS_TAIL_IFACE_STATE_UP,isTailIfaceStateUp);
        predicates.put(IsisErrorType.IsisPeerErrorType.IS_SAME_SUBNET,isSameSubnet);
        predicates.put(IsisErrorType.IsisPeerErrorType.IS_SAME_VLAN,isSameVlan);

        List<IsisErrorType.IsisPeerErrorType> errors = predicates.entrySet().stream().filter(e->!e.getValue())
                .map(Map.Entry::getKey).collect(Collectors.toList());
        return errors;
    }

    private List<String> mapIsisPeerErrorToConfig(IsisErrorType.IsisPeerErrorType errorType, Configuration headConfig, Interface headIfaceConfig,
                                                  Configuration tailConfig, Interface tailIfaceConfig){
        List<String> errorConfigs = new ArrayList<>();
        switch (errorType){
            case IS_HEAD_INTERFACE_UP:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"端口没有配置");
                break;
            case IS_TAIL_INTERFACE_UP:
                errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口没有配置");
                break;
            case HEAD_ETH_TRUNK_COINCIDE:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"端口没有配置Eth-Trunk口");
                break;
            case TAIL_ETH_TRUNK_COINCIDE:
                errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口没有配置Eth-Trunk口");
                break;
            case INTERFACE_MTU_COMPARE:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口MTU配置不一致");
                break;
            case ISIS_HEAD_ENABLE:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"端口没有使能ISIS进程");
                break;
            case ISIS_TAIL_ENABLE:
                errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口没有使能ISIS进程");
                break;
            case CIRCUIT_TYPE_COINCIDE:
                errorConfigs.add((headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口网络类型配置不一致"));
                break;
            case ISIS_HEAD_PROC_CONFIG_VALID:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"配置的进程不存在");
                break;
            case ISIS_TAIL_PROC_CONFIG_VALID:
                errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"配置的进程不存在");
                break;
            case ISIS_PEER_NO_SILENT:
                if (headIfaceConfig.getIsisSilent2ZeroCost() == true){
                    errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"为抑制状态");
                }
                if (tailIfaceConfig.getIsisSilent2ZeroCost() == true){
                    errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"为抑制状态");
                }
                break;
            case IS_HEAD_IFACE_SHUT_DOWN:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"shutdown");
                break;
            case IS_TAIL_IFACE_SHUT_DOWN:
                errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"shutdown");
                break;
            case IS_HEAD_IFACE_STATE_UP:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"没有配置网段");
                break;
            case IS_TAIL_IFACE_STATE_UP:
                errorConfigs.add(tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"没有配置网段");
                break;
            case IS_SAME_VLAN:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"VLAN不一致");
                break;
            case IS_SAME_SUBNET:
                errorConfigs.add(headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口网段不一致");
                break;
            default:
        }
        return errorConfigs;
    }


    private List<Layer1Edge> getLayer1Edges(String headDeviceName, String tailDeviceName){
        List<Layer1Edge> layer1Edges = new ArrayList<>();
        for (Layer1Edge edge : _layer1Topology.getGraph().edges()){
            if (edge.getNode1().getHostname().equals(headDeviceName) && edge.getNode2().getHostname().equals(tailDeviceName)){
                layer1Edges.add(edge);
            }
        }
        assert !layer1Edges.isEmpty();
        return layer1Edges;
    }

    private Interface getIfaceConfig(String ifaceName,Configuration configuration){
        Interface ifaceConfig = null;
        for (Interface iface : configuration.getInterfaces()){
            if (iface.getName().equals(ifaceName)){
                ifaceConfig = iface;
                break;
            }
        }
        assert ifaceConfig != null;
        return ifaceConfig;
    }

    private List<String> isisRouteImportDiagnosis(IsisNode head, IsisNode tail) {
        return null;
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
