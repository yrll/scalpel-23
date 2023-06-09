package org.sng.isisdiagnosis;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.sng.datamodel.*;
import org.sng.datamodel.configuration.*;
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

    private final ConfigLocalization _configLocalization;


    public IsisDiagnosis(Layer1Topology layer1Topology, Map<String, Configuration> configurations,
                         ValueGraph<IsisNode,IsisEdgeValue> commonFwdGraph, Map<Prefix, List<IsisEdge>> prefixEdgesMap,
                         Map<Prefix, Set<String>> directRouteDevicesMap, Map<String, String> filePathMap) {
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

        // 配置行定位类初始化
        _configLocalization = new ConfigLocalization(filePathMap);
    }


    public void igpDiagnosis(IsisErrorFlow isisErrorFlow){
        List<IsisRepairOption> repairOptions;

        // todo: 目的IP多前缀
        // 获取错误流相关信息
        String srcDevice = isisErrorFlow.getSrcDevice();
        Prefix dstPrefix = isisErrorFlow.getPrefix();
        String dstDevice =  getDstDevice(dstPrefix);

        // 如果目的前缀没有被发布，加上对应的前缀节点
        if (!_prefixFwdGraphMap.containsKey(dstPrefix)){
            MutableValueGraph<IsisNode,IsisEdgeValue> dstPrefixGraph = Graphs.copyOf(_newCommonGraph);
            // todo: 静态路由
            dstPrefixGraph.addNode(IsisNode.creatDirectEnableNode(dstDevice));
            _prefixFwdGraphMap.put(dstPrefix,dstPrefixGraph);
        }
        MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph = _prefixFwdGraphMap.get(dstPrefix);

        // 获取源节点和目的前缀
        List<IsisNode> srcNodes = getSrcNodes(srcDevice, prefixFwdGraph.nodes());
        IsisNode dstNode = getDstNode(dstDevice, prefixFwdGraph.nodes());
        Interface originIface = null;
        if (dstNode.getIsisId() == IsisNode.DIRECT_ENABLE){
            originIface = getPrefixOriginIface(dstDevice,dstPrefix);
        }

        // 判断源节点和目的前缀在ISIS graph上可达（路由可达）
        boolean routeReachability = isSrcToPrefixConnected(srcNodes,dstNode,prefixFwdGraph);

        // 如果路由可达，诊断转发错误。目前仅考虑了多源发前缀错误，由错误路由导入导致，修复方案为删除对应路由导入。
        if (routeReachability){
            repairOptions = forwardingDiagnosis(srcNodes,dstNode,dstPrefix,prefixFwdGraph);
            if (!repairOptions.isEmpty()){
                System.out.println(IsisErrorType.ErrorType.ISIS_ROUTE_IMPORT_UNWANTED_ERROR.errorString());
            }
        }
        // 否则，诊断路由错误
        else {
            // 在ISIS图上加上没有生效的引入进程
            MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge = Graphs.copyOf(prefixFwdGraph);
            List<IsisEdge> invalidImportEdges = getInvalidImportEdge(prefixFwdGraph,dstNode,dstPrefix);
            deleteInvalidEdges(invalidImportEdges,dstNode,originIface);
            for (IsisEdge invalidImportEdge: invalidImportEdges){
                prefixFwdGraphWithImportEdge.putEdgeValue(invalidImportEdge.getSource(),invalidImportEdge.getTarget(),invalidImportEdge.getEdgeValue());
            }
            boolean hasRoutePath = isSrcToPrefixConnected(srcNodes,dstNode,prefixFwdGraphWithImportEdge);
            // 如果前缀仍没有路径到达源节点，构建路径（BFS）
            if(!hasRoutePath){
                List<IsisEdge> candidateEdges = computeCandidateEdges(_edgesNotPeering,prefixFwdGraphWithImportEdge);
                deleteInvalidEdges(candidateEdges,dstNode,originIface);
                Set<Set<IsisEdge>> connectEdges = new HashSet<>(findMinimalConnectEdges(srcNodes, dstNode, prefixFwdGraph, candidateEdges));
                System.out.println(IsisErrorType.ErrorType.ISIS_NO_ROUTE_PATH_ERROR.errorString());
                repairOptions = connectEdgeDiagnosis(srcNodes, connectEdges,dstPrefix,originIface,prefixFwdGraph,prefixFwdGraphWithImportEdge);
            }
            // 否则，诊断路由导入错误（路由路径上某条边由于导入失败断开）
            else {
                System.out.println(IsisErrorType.ErrorType.ISIS_ROUTE_IMPORT_FAIL_ERROR.errorString());
                repairOptions = routeImportDiagnosis(srcNodes,dstNode,dstPrefix,originIface,prefixFwdGraph,prefixFwdGraphWithImportEdge);
            }
        }
        System.out.println(repairOptions);
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

    /** 获取源发直连路由的端口 **/
    private Interface getPrefixOriginIface(String dstDevice, Prefix dstPrefix){
        List<Interface> originIfaces = _configurations.get(dstDevice).getInterfaces().values().stream()
                .filter(iface -> dstPrefix.equals(iface.getOriginalAddress()))
                .collect(Collectors.toList());
        if (originIfaces.isEmpty()){
            throw new RuntimeException("节点没有端口源发该前缀");
        }
        if (originIfaces.size() !=1 ){
            throw new RuntimeException("多个端口配置同样的前缀");
        }
        return originIfaces.get(0);
    }

    /** 判断源节点能否到达目的前缀 **/
    private boolean isSrcToPrefixConnected(List<IsisNode> srcNodes, IsisNode dstNode,
                                                  MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        boolean canReach = false;

        if (srcNodes.size() == 1 && srcNodes.get(0).getIsisId() == IsisNode.NEW_ISIS_PROCESS){
            return false;
        }
        for (IsisNode srcNode : srcNodes){
            Set<IsisNode> reachableNodes = reverseReachableNodes(prefixFwdGraph,srcNode);
            if (reachableNodes.contains(dstNode)){
                canReach = true;
            }
        }
        return canReach;
    }

    /** 根据配置得到所有的同设备进程间路由引入，对比ISIS转发图得到未生效的路由引入 **/
    private List<IsisEdge> getInvalidImportEdge(MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph, IsisNode dstNode ,Prefix dstPrefix){
        List<IsisEdge> existingImportEdges = _prefixEdgesMap.containsKey(dstPrefix) ? _prefixEdgesMap.get(dstPrefix) : new ArrayList<>();
        List<IsisEdge> invalidImportEdges = new ArrayList<>();
        for (Map.Entry<String,Configuration> configEntry: _configurations.entrySet()){
            String deviceName = configEntry.getKey();
            Configuration configuration = configEntry.getValue();
            for (IsisConfiguration isisConfiguration:configuration.getIsisConfigurations().values()){
                IsisNode dstProcess = getNodeFromGrah(deviceName,isisConfiguration.getIsisId(),prefixFwdGraph);
                // ISIS图中包含了所有配置的进程
                assert dstProcess != null;
                for (IsisRouteImport isisRouteImport : isisConfiguration.getImportRoutes()){
                    if ("ISIS".equals(isisRouteImport.getProtocol())){
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
    private Set<Set<IsisEdge>> findMinimalConnectEdges(List<IsisNode> srcNodes, IsisNode dstNode,
                                                   MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                                   List<IsisEdge> candidateEdges){
        int maxDepth = 5;
        Set<Set<IsisEdge>> connectEdges = new HashSet<>();

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
        // todo:启发式权重
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

    /** 考虑重分发路由不能导入到同设备其他进程 **/
    private boolean isForwardingPathValid(List<IsisNode> path, IsisNode dstNode){
        if (!IsisNode.NOT_ISIS_PROTOCOL_IDS.contains(dstNode.getIsisId())){
            return true;
        }
        int routeImportNum = (int) path.stream().filter(node -> node.getDevName().equals(dstNode.getDevName())).count();
        return routeImportNum <= 2;
    }

    /** 过滤无效的路由导入 **/
    private void deleteInvalidEdges(List<IsisEdge> edges, IsisNode dstNode, Interface originIface){
        Set<IsisEdge> invalidEdges = new HashSet<>();
        for (IsisEdge edge:edges){
            IsisNode srcProcess = edge.getSource();
            IsisNode dstProcess = edge.getTarget();
            // 目前 只考虑源发路由节点的进程
            if(!srcProcess.getDevName().equals(dstNode.getDevName()) || !dstProcess.getDevName().equals(dstNode.getDevName())){
                continue;
            }
            // 目的进程不能是源发的路由
            if (dstProcess.equals(dstNode)){
                invalidEdges.add(edge);
                continue;
            }
            // 重发布的路由不能被引入到其他进程，直接删除所有进程间导入
            if (IsisNode.NOT_ISIS_PROTOCOL_IDS.contains(dstNode.getIsisId())){
                if (dstProcess.getIsisId() != dstNode.getIsisId() && srcProcess.getIsisId() != dstNode.getIsisId()){
                    invalidEdges.add(edge);
                }
            }
            // isis enable只能引入到一个路由进程
            else {
                if (originIface.getIsisEnable() != null){
                    if (srcProcess.equals(dstNode) && dstProcess.getIsisId() != originIface.getIsisEnable()){
                        invalidEdges.add(edge);
                    }
                }
            }
        }
        edges.removeAll(invalidEdges);
    }

    /** 路由不可达诊断，找出路径断开点，诊断断开原因 **/
    private List<IsisRepairOption> routeImportDiagnosis(List<IsisNode> srcNodes, IsisNode dstNode, Prefix dstPrefix,Interface originIface,
                                                        MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                                        MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge){
        Map<List<IsisNode>,IsisEdge> pathBreakPointMap = findImportBreakPoints(srcNodes,dstNode,prefixFwdGraph,prefixFwdGraphWithImportEdge);
        List<IsisRepairOption> repairOptions = new ArrayList<>();
        for (Map.Entry<List<IsisNode>,IsisEdge> pathBreakPoint: pathBreakPointMap.entrySet()){
            List<IsisNode> path = pathBreakPoint.getKey();
            IsisEdge breakPoint = pathBreakPoint.getValue();
            String errorReason = "修复路由传播路径: "+ getPathString(path);
            List<IsisRepairOption.IsisError> isisErrors = isisRouteImportDiagnosis(breakPoint.getSource(),breakPoint.getTarget(),dstPrefix,originIface,prefixFwdGraph);
            repairOptions.add(new IsisRepairOption(errorReason,isisErrors));
        }
        return repairOptions;
    }

    /** 找到路由导入失败的边 **/
    private Map<List<IsisNode>,IsisEdge> findImportBreakPoints(List<IsisNode> srcNodes, IsisNode dstNode,
                                                 MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph,
                                                 MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge){
        Map<List<IsisNode>,IsisEdge> pathBreakPointMap = new HashMap<>();
        List<List<IsisNode>> paths = new ArrayList<>();
        for (IsisNode srcNode: srcNodes){
            paths.addAll(computeForwardingPath(srcNode,prefixFwdGraphWithImportEdge));
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
                    pathBreakPointMap.put(path,new IsisEdge(tail,head,prefixFwdGraphWithImportEdge.edgeValue(tail,head).get()));
                    break;
                }
                head = tail;
            }
        }
        return pathBreakPointMap;
    }

    /** 路由可达但转发不可达诊断 **/
    private List<IsisRepairOption> forwardingDiagnosis(List<IsisNode> srcNodes, IsisNode dstNode, Prefix dstPrefix,
                                                       MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        List<IsisRepairOption> repairOptions = new ArrayList<>();
        List<IsisEdge> errorImports = findErrorPrefixImports(srcNodes,dstNode,prefixFwdGraph);
        if (errorImports.size() > 0){
            List<IsisRepairOption.IsisError> isisErrors = new ArrayList<>();
            String errorReason = IsisErrorType.ErrorType.ISIS_ROUTE_IMPORT_UNWANTED_ERROR.errorString();
            for (IsisEdge isisEdge : errorImports){
                isisErrors.add(mapIsisRouteImportUnwantedErrorToConfig(IsisErrorType.IsisRouteImportUnwantedError.ISIS_UNWANTED_IMPORT,
                        isisEdge.getSource(),isisEdge.getTarget(),dstPrefix));
            }
            repairOptions.add(new IsisRepairOption(errorReason,isisErrors));
        }
        return repairOptions;
    }

    /** 多源发前缀错误诊断，找到错误前缀引入 **/
    private List<IsisEdge> findErrorPrefixImports(List<IsisNode> srcNodes, IsisNode dstNode, MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraph){
        List<IsisEdge> errorImports = new ArrayList<>();
        List<List<IsisNode>> paths = new ArrayList<>();
        for (IsisNode srcNode: srcNodes){
            paths.addAll(computeForwardingPath(srcNode,prefixFwdGraph));
        }
        //如果最优路径的终点不是目的节点，找到对应的import节点并删除
        PriorityQueue<List<IsisNode>> pq = new PriorityQueue<>(paths.size(), Comparator.comparingInt(path -> computePathCost(path,prefixFwdGraph)));
        pq.addAll(paths);
        while (!pq.isEmpty()){
            List<IsisNode> path = pq.poll();
            IsisEdge importEdge = getImportNodeOfPath(path);
            if (!path.get(path.size()-1).equals(dstNode)){
                errorImports.add(importEdge);
            }
            else {
                break;
            }
        }
        return errorImports;
    }

    /** 给定源节点，计算到达目的前缀转发路径 **/
    private List<List<IsisNode>> computeForwardingPath(IsisNode srcNode,ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        //找到同一ISIS实例的路由引入边
        List<List<IsisNode>> paths = new ArrayList<>();
        List<IsisEdge> importEdges = getImportEdgesOfSameIsisInstance(srcNode,isisFwdGraph);
        for (IsisEdge importEdge: importEdges){
            IsisNode importNode = importEdge.getTarget();
            List<IsisNode> shortestPath = dijkstra(srcNode,importNode,isisFwdGraph);
            IsisNode originNode = importEdge.getSource();
            shortestPath.add(originNode);
            // 如果引入节点是源发节点，返回结果
            if (IsisNode.PREFIX_ORIGIN_IDS.contains(originNode.getIsisId())){
                if (isForwardingPathValid(shortestPath,originNode)){
                    paths.add(shortestPath);
                }
            }
            // 否则，递归查找下一个ISIS实例的路径，与当前的路径进行组合
            else {
                List<List<IsisNode>> followPaths = computeForwardingPath(originNode,isisFwdGraph);
                for (List<IsisNode> followPath: followPaths){
                    List<IsisNode> combinePath = new ArrayList<>(shortestPath);
                    followPath.remove(originNode);
                    combinePath.addAll(followPath);
                    paths.add(combinePath);
                }
            }
        }
        return paths;
    }

    /** 找到与节点同一ISIS实例内的所有路由导入边 (BFS) **/
    private List<IsisEdge> getImportEdgesOfSameIsisInstance(IsisNode srcNode, ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
        List<IsisEdge> importEdges = new ArrayList<>();
        Set<IsisNode> reachableNodes  = new HashSet<>();
        reachableNodes.add(srcNode);
        Queue<IsisNode> queue = new LinkedList<>();
        queue.add(srcNode);
        while (!queue.isEmpty()){
            IsisNode current = queue.poll();
            Set<IsisNode> predecessors = isisFwdGraph.predecessors(current);
            for (IsisNode predecessor : predecessors){
                // 遇到引入边，加入到结果中，并且不再进一步搜索该节点
                if(predecessor.getDevName().equals(current.getDevName())){
                    importEdges.add(new IsisEdge(predecessor,current,isisFwdGraph.edgeValue(predecessor,current).get()));
                }
                // 否则 ，迭代搜索
                else {
                    if (!reachableNodes.contains(predecessor)){
                        queue.add(predecessor);
                        reachableNodes.add(predecessor);
                    }
                }
            }
        }
        return importEdges;
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

    /** 计算路径cost **/
    private int computePathCost(List<IsisNode> path,ValueGraph<IsisNode, IsisEdgeValue> isisFwdGraph){
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

    private String getPathString(List<IsisNode> path){
        StringBuilder pathStringBuilder = new StringBuilder();
        IsisNode head = path.get(0);
        pathStringBuilder.append(head.getDevName()).append("@").append(head.getIsisId());
        for (int i = 1; i < path.size(); i++ ){
            pathStringBuilder.append("-->");
            IsisNode tail = path.get(i);
            pathStringBuilder.append(tail.getDevName()).append("@").append(tail.getIsisId());
        }
       return pathStringBuilder.toString();
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
                    peerEdges.add(new IsisEdge(srcProcess,dstProcess,IsisEdgeValue.creatNewEdgeValue()));
                    peerEdges.add(new IsisEdge(dstProcess,srcProcess,IsisEdgeValue.creatNewEdgeValue()));
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
                        importEdges.add(new IsisEdge(processes.get(i),processes.get(j),IsisEdgeValue.creatNewEdgeValue()));
                    }
                    if(!prefixFwdGraphWithImportEdge.hasEdgeConnecting(processes.get(j),processes.get(i))){
                        importEdges.add(new IsisEdge(processes.get(j),processes.get(i),IsisEdgeValue.creatNewEdgeValue()));
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
            if (node.getDevName().equals(devName) && IsisNode.PREFIX_ORIGIN_IDS.contains(node.getIsisId())){
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

    private IsisNode getNodeFromGrah(String devName,Integer isisId, ValueGraph<IsisNode, IsisEdgeValue> graph){
        IsisNode node = null;
        for (IsisNode isisNode: graph.nodes()){
            if (isisNode.getDevName().equals(devName) && isisNode.getIsisId() == isisId){
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
    private List<IsisRepairOption> connectEdgeDiagnosis(List<IsisNode> srcNodes, Set<Set<IsisEdge>> connectEdges, Prefix dstPrefix,Interface originIface,
                                                        MutableValueGraph<IsisNode,IsisEdgeValue> prefixFwdGraph,
                                                        MutableValueGraph<IsisNode, IsisEdgeValue> prefixFwdGraphWithImportEdge){
        List<IsisRepairOption> repairOptions = new ArrayList<>();
        for (Set<IsisEdge> connectEdgeList:connectEdges){
            // 计算得到修复后的路由传播路径
            MutableValueGraph<IsisNode,IsisEdgeValue> repairedGraph = Graphs.copyOf(prefixFwdGraphWithImportEdge);
            connectEdgeList.forEach(connectEdge -> repairedGraph.putEdgeValue(connectEdge.getSource(),connectEdge.getTarget(),connectEdge.getEdgeValue()));
            List<List<IsisNode>> repairPaths = new ArrayList<>();
            srcNodes.forEach(reachableSrcNode -> repairPaths.addAll(computeForwardingPath(reachableSrcNode,repairedGraph)));
            StringBuilder errorReason = new StringBuilder("路由无传播路径，构建路径：");
            for (List<IsisNode> repairPath : repairPaths){
                errorReason.append(getPathString(repairPath)).append("|");
            }

            // 计算路径对应的修复方案
            List<IsisRepairOption.IsisError> isisErrors = new ArrayList<>();
            for (IsisEdge edge: connectEdgeList){
                IsisNode head = edge.getSource();
                IsisNode tail = edge.getTarget();
                // ISIS路由导入失败原因诊断
                if (head.getDevName().equals(tail.getDevName())){
                    isisErrors.addAll(isisRouteImportDiagnosis(head,tail,dstPrefix,originIface,prefixFwdGraph));
                }
                // ISIS邻居建立失败原因诊断
                else {
                    isisErrors.addAll(isisIpv4PeerDiagnosis(head,tail));
                }
            }
            repairOptions.add(new IsisRepairOption(errorReason.toString(),isisErrors));
        }
        return repairOptions;
    }

    private List<IsisRepairOption.IsisError> isisIpv4PeerDiagnosis(IsisNode head, IsisNode tail){
        String headDeviceName = head.getDevName();
        String tailDevName = tail.getDevName();

        Configuration headConfig = _configurations.get(headDeviceName);
        Configuration tailConfig = _configurations.get(tailDevName);
        List<Layer1Edge> layer1Edges = getLayer1Edges(headDeviceName, tailDevName);

        // 返回最小的配置变更方案
        List<IsisRepairOption.IsisError> minimalIsisErrors = new ArrayList<>();
        Set<Layer1Edge> ethTrunks = new HashSet<>();

        // 考虑多个物理连接情况
        for (Layer1Edge layer1Edge: layer1Edges){
            Interface headIfaceConfig = headConfig.getInterfaces().get(layer1Edge.getNode1().getInterfaceName());
            Interface tailIfaceConfig = tailConfig.getInterfaces().get(layer1Edge.getNode2().getInterfaceName());
            List<IsisRepairOption.IsisError> isisErrors = new ArrayList<>();
            // 有一边端口没有配置
            if (headIfaceConfig == null || tailIfaceConfig == null){
                isisErrors.addAll(getInterfaceUpErrors(headConfig,headIfaceConfig,tailConfig,tailIfaceConfig));
            }
            // 只有一个端口配置了Eth-trunk
            else if (headIfaceConfig.getPhyIfOrEthTrunk().size() != tailIfaceConfig.getPhyIfOrEthTrunk().size()){
                isisErrors.addAll(getEthTrunkCoincideErrors(headConfig,headIfaceConfig,tailConfig,tailIfaceConfig));
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
                    Interface headEthTrunkIface = headConfig.getInterfaces().get(headEthTrunkName);
                    Interface tailEthTrunkIface = tailConfig.getInterfaces().get(tailEthTrunkName);

                    if (headEthTrunkIface == null || tailEthTrunkIface == null){
                        isisErrors.addAll(getInterfaceUpErrors(headConfig,headEthTrunkIface,tailConfig,tailEthTrunkIface));
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
                        headIfaceSubIfaces.addAll(headConfig.getInterfaces().values().stream().filter(i -> i.getName().contains(headIfaceName+".")).collect(Collectors.toList()));
                    }
                    if (!tailIfaceConfig.getIsShutdown()){
                        String tailIfaceName = tailIfaceConfig.getName();
                        tailIfaceSubIfaces.addAll(tailConfig.getInterfaces().values().stream().filter(i -> i.getName().contains(tailIfaceName+".")).collect(Collectors.toList()));
                    }
                    // 对每对子接口进行配置检查，选择错误最少的
                    int errorWeight = Integer.MAX_VALUE;
                    List<IsisErrorType.IsisPeerFailErrorType> errorTypeList = new ArrayList<>();
                    Interface effectiveHeadIface = null;
                    Interface effectiveTailIface = null;
                    for (Interface headSubIface: headIfaceSubIfaces){
                        for (Interface tailSubIface: tailIfaceSubIfaces){
                            List<IsisErrorType.IsisPeerFailErrorType> subIfacePeerErrors = isisIpv4PeerIfaceDiagnosis(headConfig,headSubIface,tailConfig,tailSubIface);
                            if(subIfacePeerErrors.size() < errorWeight){
                                effectiveHeadIface = headSubIface;
                                effectiveTailIface = tailSubIface;
                                errorTypeList = subIfacePeerErrors;
                            }
                        }
                    }
                    for (IsisErrorType.IsisPeerFailErrorType errorType:errorTypeList){
                        isisErrors.add(mapIsisPeerFailErrorToConfig(errorType,headConfig, effectiveHeadIface,tailConfig,effectiveTailIface));
                    }
                }
            }

            // 启发算法：在多种错误情况中选择错误数量最少的
            if (minimalIsisErrors.size() == 0 || isisErrors.size() < minimalIsisErrors.size()){
                minimalIsisErrors = isisErrors;
            }
        }
        return minimalIsisErrors;
    }

    /** 获取端口未开启错误信息 **/
    private List<IsisRepairOption.IsisError>  getInterfaceUpErrors(Configuration headConfig, Interface headIfaceConfig, Configuration tailConfig,Interface tailIfaceConfig){
        List<IsisRepairOption.IsisError> ifaceUpRelatedErrors = new ArrayList<>();
        if (headIfaceConfig == null ) {
            ifaceUpRelatedErrors.add(mapIsisPeerFailErrorToConfig(IsisErrorType.IsisPeerFailErrorType.IS_HEAD_INTERFACE_UP,
                    headConfig,null,tailConfig,tailIfaceConfig));
        }
        if (tailIfaceConfig  == null){
            ifaceUpRelatedErrors.add(mapIsisPeerFailErrorToConfig(IsisErrorType.IsisPeerFailErrorType.IS_TAIL_INTERFACE_UP,
                    headConfig,headIfaceConfig,tailConfig,null));
        }
        return ifaceUpRelatedErrors;
    }

    private List<IsisRepairOption.IsisError>  getEthTrunkCoincideErrors(Configuration headConfig, Interface headIfaceConfig, Configuration tailConfig,Interface tailIfaceConfig){
        List<IsisRepairOption.IsisError> ethTrunkCoincideRelatedErrors = new ArrayList<>();

        if (headIfaceConfig.getPhyIfOrEthTrunk().size() == 0){
            ethTrunkCoincideRelatedErrors.add(mapIsisPeerFailErrorToConfig(IsisErrorType.IsisPeerFailErrorType.HEAD_ETH_TRUNK_COINCIDE,
                    headConfig,headIfaceConfig,tailConfig,tailIfaceConfig));
        }
        if (tailIfaceConfig.getPhyIfOrEthTrunk().size() == 0){
            ethTrunkCoincideRelatedErrors.add(mapIsisPeerFailErrorToConfig(IsisErrorType.IsisPeerFailErrorType.TAIL_ETH_TRUNK_COINCIDE,
                    headConfig,headIfaceConfig,tailConfig,tailIfaceConfig));
        }
        return ethTrunkCoincideRelatedErrors;
    }

    /** 在两个端口进程生效的前提下，诊断导致ISIS邻居失效的错误端口配置 **/
    private List<IsisErrorType.IsisPeerFailErrorType> isisIpv4PeerIfaceDiagnosis(Configuration headConfig,Interface headIfaceConfig,
                                                                             Configuration tailConfig, Interface tailIfaceConfig){
        Map<IsisErrorType.IsisPeerFailErrorType,Boolean> predicates = new HashMap<>();
        boolean interfaceMtuCompare = headIfaceConfig.getIpv6MtuAndSpread().equals(tailIfaceConfig.getIpv6MtuAndSpread());
        boolean isisHeadEnable = headIfaceConfig.getIsisEnable() !=null;
        boolean isisTailEnable = tailIfaceConfig.getIsisEnable() !=null;
        boolean isisHeadProcConfigValid = true;
        boolean isisTailProcConfigValid = true;
        if (isisHeadEnable){
            int headIsisId = headIfaceConfig.getIsisEnable();
            isisHeadProcConfigValid = headConfig.getIsisConfigurations().containsKey(headIsisId) &&
                    headConfig.getIsisConfigurations().get(headIsisId).getNetworkEntity() != null;
        }
        if (isisTailEnable){
            int tailIsisId = tailIfaceConfig.getIsisEnable();
            isisTailProcConfigValid = tailConfig.getIsisConfigurations().containsKey(tailIsisId) &&
                    tailConfig.getIsisConfigurations().get(tailIsisId).getNetworkEntity() != null;
        }
        boolean circuitTypeCoincide = headIfaceConfig.getCircuitTypeP2P().equals(tailIfaceConfig.getCircuitTypeP2P());
        boolean isisHeadPeerNoSilent =  !headIfaceConfig.getIsisSilent2ZeroCost();
        boolean isisTailPeerNoSilent =  !tailIfaceConfig.getIsisSilent2ZeroCost();

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
        predicates.put(IsisErrorType.IsisPeerFailErrorType.INTERFACE_MTU_COMPARE,interfaceMtuCompare);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.ISIS_HEAD_ENABLE,isisHeadEnable);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.ISIS_TAIL_ENABLE,isisTailEnable);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.CIRCUIT_TYPE_COINCIDE,circuitTypeCoincide);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.ISIS_HEAD_PROC_CONFIG_VALID,isisHeadProcConfigValid);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.ISIS_TAIL_PROC_CONFIG_VALID,isisTailProcConfigValid);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.ISIS_HEAD_PEER_NO_SILENT,isisHeadPeerNoSilent);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.ISIS_TAIL_PEER_NO_SILENT,isisTailPeerNoSilent);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.IS_HEAD_IFACE_SHUT_DOWN,isHeadIfaceShutDown);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.IS_TAIL_IFACE_SHUT_DOWN,isTailIfaceShutDown);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.IS_HEAD_IFACE_STATE_UP,isHeadIfaceStateUp);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.IS_TAIL_IFACE_STATE_UP,isTailIfaceStateUp);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.IS_SAME_SUBNET,isSameSubnet);
        predicates.put(IsisErrorType.IsisPeerFailErrorType.IS_SAME_VLAN,isSameVlan);

        return predicates.entrySet().stream().filter(e->!e.getValue())
                .map(Map.Entry::getKey).collect(Collectors.toList());
    }

    /** 将ISIS邻居建立错误配置类型映射到配置行 **/
    private IsisRepairOption.IsisError mapIsisPeerFailErrorToConfig(IsisErrorType.IsisPeerFailErrorType errorType, Configuration headConfig, Interface headIfaceConfig,
                                                  Configuration tailConfig, Interface tailIfaceConfig){
        String errorReason = null;
        List<IsisRepairOption.ErrorConfig> errorConfigs = new ArrayList<>();
        switch (errorType){
            case IS_HEAD_INTERFACE_UP:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"端口没有配置";
                errorConfigs.add(_configLocalization.generateInterfaceUp(headConfig.getSysName(), headIfaceConfig.getName()));
                break;
            case IS_TAIL_INTERFACE_UP:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口没有配置";
                errorConfigs.add(_configLocalization.generateInterfaceUp(tailConfig.getSysName(),tailIfaceConfig.getName()));
                break;
            case HEAD_ETH_TRUNK_COINCIDE:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"端口没有配置Eth-Trunk口";
                errorConfigs.add( _configLocalization.generateEthTrunk(headConfig.getSysName()));
                break;
            case TAIL_ETH_TRUNK_COINCIDE:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口没有配置Eth-Trunk口";
                errorConfigs.add(_configLocalization.generateEthTrunk(tailConfig.getSysName()));
                break;
            case INTERFACE_MTU_COMPARE:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口MTU配置不一致";
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(headConfig.getSysName(),
                        headIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.MTU));
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(tailConfig.getSysName(),
                        tailIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.MTU));
                break;
            case ISIS_HEAD_ENABLE:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"端口没有使能ISIS进程";
                errorConfigs.add( _configLocalization.generateIsisEnable(headConfig.getSysName(), headIfaceConfig.getIsisEnable()));
                break;
            case ISIS_TAIL_ENABLE:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口没有使能ISIS进程";
                errorConfigs.add(_configLocalization.generateIsisEnable(tailConfig.getSysName(), tailIfaceConfig.getIsisEnable()));
                break;
            case CIRCUIT_TYPE_COINCIDE:
                errorReason = (headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口网络类型配置不一致");
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(headConfig.getSysName(),
                        headIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.NETWORK_TYPE));
                errorConfigs.add((_configLocalization.localizeInterfaceConfig(tailConfig.getSysName(),
                        tailIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.NETWORK_TYPE)));
                break;
            case ISIS_HEAD_PROC_CONFIG_VALID:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"配置的ISIS进程不存在";
                errorConfigs.add(_configLocalization.generateIsisProcess(headConfig.getSysName(), headIfaceConfig.getIsisEnable()));
                break;
            case ISIS_TAIL_PROC_CONFIG_VALID:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"配置的ISIS进程不存在";
                errorConfigs.add( _configLocalization.generateIsisProcess(tailConfig.getSysName(), tailIfaceConfig.getIsisEnable()));
                break;
            case ISIS_HEAD_PEER_NO_SILENT:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"为抑制状态";
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(headConfig.getSysName(),
                        headIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.ISIS_SILENT));
                break;
            case ISIS_TAIL_PEER_NO_SILENT:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"为抑制状态";
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(tailConfig.getSysName(),
                        tailIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.ISIS_SILENT));
                break;
            case IS_HEAD_IFACE_SHUT_DOWN:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"shutdown";
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(headConfig.getSysName(),
                        headIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.SHUTDOWN));
                break;
            case IS_TAIL_IFACE_SHUT_DOWN:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"shutdown";
                errorConfigs.add( _configLocalization.localizeInterfaceConfig(tailConfig.getSysName(),
                        tailIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.SHUTDOWN));
                break;
            case IS_HEAD_IFACE_STATE_UP:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"没有配置网段";
                errorConfigs.add(_configLocalization.generateIpAddress(headConfig.getSysName()));
                break;
            case IS_TAIL_IFACE_STATE_UP:
                errorReason = tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"没有配置网段";
                errorConfigs.add(_configLocalization.generateIpAddress(tailConfig.getSysName()));
                break;
            case IS_SAME_VLAN:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"VLAN不一致";
                errorConfigs.add( _configLocalization.localizeInterfaceConfig(headConfig.getSysName(),
                        headIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.VLAN_TYPE));
                errorConfigs.add((_configLocalization.localizeInterfaceConfig(tailConfig.getSysName(),
                        tailIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.VLAN_TYPE)));
                break;
            case IS_SAME_SUBNET:
                errorReason = headConfig.getSysName()+"-"+headIfaceConfig.getName()+"与"
                        +tailConfig.getSysName()+"-"+tailIfaceConfig.getName()+"端口网段不一致";
                errorConfigs.add(_configLocalization.localizeInterfaceConfig(headConfig.getSysName(),
                        headIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.IP_ADDRESS));
                errorConfigs.add((_configLocalization.localizeInterfaceConfig(tailConfig.getSysName(),
                        tailIfaceConfig.getName(),ConfigLocalization.INTERFACE_CONFIG_TYPE.IP_ADDRESS)));
                break;
            default:
        }
        return new IsisRepairOption.IsisError(errorReason,errorConfigs);
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

    private List<IsisRepairOption.IsisError> isisRouteImportDiagnosis(IsisNode head, IsisNode tail, Prefix dstPrefix,Interface originIface,
                                                                      MutableValueGraph<IsisNode,IsisEdgeValue> prefixFwdGraph) {
        Configuration configuration = _configurations.get(head.getDevName());
        IsisConfiguration tailIsisProcess = configuration.getIsisConfigurations().get(tail.getIsisId());
        List<IsisRepairOption.IsisError> isisErrors = new ArrayList<>();
        // 若是新加的进程，不存在节点内进程路由导入。所以，进程节点存在于ISIS转发图上，也就是必有对应进程配置
        assert tailIsisProcess !=null;
        // 源发路由导入失败
        if (head.getIsisId() == IsisNode.DIRECT_ENABLE){
            // 源发端口没有加入指定ISIS进程
            if (!Objects.equals(originIface.getIsisEnable(), tail.getIsisId())){
                isisErrors.add(mapIsisRouteImportFailErrorToConfig(IsisErrorType.IsisRouteImportFailErrorType.PREFIX_ISIS_ENABLE,head,tail,originIface, null));
            }
        }
        // 进程间或协议间路由导入失败
        else {
            boolean hasRouteImport = tailIsisProcess.getImportRoutes().stream().anyMatch(routeImport ->
                    Objects.equals(routeImport.getProtocolId(), head.getIsisId()));
            // 没有配置路由导入
            if (!hasRouteImport){
                isisErrors.add(mapIsisRouteImportFailErrorToConfig(IsisErrorType.IsisRouteImportFailErrorType.ISIS_IMPORT_ENABLE,head,tail,null, null));
            }
            // 配置了导入，但没能导入成功，诊断路由策略
            else {
                IsisRouteImport isisRouteImport = tailIsisProcess.getImportRoutes().stream().filter(routeImport ->
                        Objects.equals(routeImport.getProtocolId(), head.getIsisId())).collect(Collectors.toList()).get(0);
                RoutePolicy importPolicy = isisRouteImport.getRoutePolicyModel();
                RoutePolicy.MatchedPolicy matchedPolicy = importPolicy.match(dstPrefix);
                RoutePolicyNode matchPolicyNode = matchedPolicy.getRoutePolicyNode();
                // 被策略过滤掉了
                if (matchPolicyNode != null && matchPolicyNode.getPolicyMatchMode().equals(RoutePolicyNode.DENY_MODE)){
                    isisErrors.add(mapIsisRouteImportFailErrorToConfig(IsisErrorType.IsisRouteImportFailErrorType.IMPORT_POLICY_FILTER,head,tail,null,matchedPolicy));
                }
                // 策略没有过滤，考虑多前缀错误
                else {
                    List<IsisNode> srcNodes = prefixFwdGraph.nodes().stream().filter(isisNode -> isisNode.getDevName().equals(tail.getDevName())).collect(Collectors.toList());
                    List<IsisEdge> errorImports = findErrorPrefixImports(srcNodes,head,prefixFwdGraph);
                    for (IsisEdge isisEdge : errorImports){
                        isisErrors.add(mapIsisRouteImportUnwantedErrorToConfig(IsisErrorType.IsisRouteImportUnwantedError.ISIS_UNWANTED_IMPORT,
                                isisEdge.getSource(),isisEdge.getTarget(),dstPrefix));
                    }
                }
            }
        }
        return isisErrors;
    }

    /** 将ISIS邻居建立错误配置类型映射到具体错误与配置行 **/
    private IsisRepairOption.IsisError mapIsisRouteImportFailErrorToConfig(IsisErrorType.IsisRouteImportFailErrorType errorType, IsisNode head, IsisNode tail,
                                                                                Interface originIface, RoutePolicy.MatchedPolicy matchedPolicy){
        List<IsisRepairOption.ErrorConfig> errorConfigs = new ArrayList<>();
        String errorReason = "";
        switch (errorType){
            case PREFIX_ISIS_ENABLE:
                errorReason = head.getDevName()+"端口"+originIface.getName()+"的前缀没有导入到进程"+tail.getIsisId();
                errorConfigs.add(_configLocalization.generateIsisEnable(tail.getDevName(),tail.getIsisId()));
                break;
            case ISIS_IMPORT_ENABLE:
                if (head.getIsisId() == IsisNode.DIRECT_IMPORT){
                    errorReason = "设备"+tail.getDevName()+"的ISIS进程"+tail.getIsisId()+"没有配置直连路由导入";
                }
                else if (head.getIsisId() == IsisNode.STATIC_IMPORT){
                    errorReason = "设备"+tail.getDevName()+"的ISIS进程"+tail.getIsisId()+"没有配置静态路由导入";
                }
                else {
                    errorReason ="设备"+tail.getDevName()+"的ISIS进程"+tail.getIsisId()+"没有配置对进程"+head.getIsisId()+"的路由导入";
                }
                errorReason = errorReason +" (针对目的前缀配置路由策略)";
                errorConfigs.add(_configLocalization.generateImportRoute(head.getDevName(), head.getIsisId()));
                break;
            case IMPORT_POLICY_FILTER:
                errorReason ="设备"+tail.getDevName()+"的ISIS进程"+tail.getIsisId()+"对ISIS进程"+head.getIsisId()+"的策略过滤失败";
                errorConfigs.addAll(_configLocalization.localizeMatchedPolicy(tail.getDevName(),matchedPolicy));
                break;
            default:
        }
        return new IsisRepairOption.IsisError(errorReason, errorConfigs);
    }

    /** 将ISIS邻居不该出现的邻居建立映射到配置行 **/
    private IsisRepairOption.IsisError mapIsisRouteImportUnwantedErrorToConfig(IsisErrorType.IsisRouteImportUnwantedError errorType,
                                                                               IsisNode head, IsisNode tail, Prefix dstPrefix){
        List<IsisRepairOption.ErrorConfig> errorConfigs = new ArrayList<>();
        String errorReason = "";
        switch (errorType){
            case ISIS_UNWANTED_IMPORT:
                errorReason ="在设备"+tail.getDevName()+"删除进程"+tail.getIsisId()+"对进程" +head.getIsisId()+"的导入（针对前缀）";
                if (head.getIsisId() == IsisNode.DIRECT_ENABLE){
                    Configuration deviceConfiguration = _configurations.get(head.getDevName());
                    Interface prefixIface = deviceConfiguration.getInterfaces().values().stream().
                            filter(i -> Objects.equals(i.getOriginalAddress(),dstPrefix)).findFirst().get();
                    errorConfigs.add(_configLocalization.localizeInterfaceConfig(head.getDevName(),prefixIface.getName(), ConfigLocalization.INTERFACE_CONFIG_TYPE.ISIS_ENABLE));
                }
                else {
                    errorConfigs.add(_configLocalization.localizeIsisProcessImport(tail.getDevName(),tail.getIsisId(),head.getIsisId()));
                }
                break;
            default:
        }
        return new IsisRepairOption.IsisError(errorReason,errorConfigs);
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
