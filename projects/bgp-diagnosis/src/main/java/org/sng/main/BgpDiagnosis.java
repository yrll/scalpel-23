package org.sng.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.sng.datamodel.Prefix;
import org.sng.main.InputData.NetworkType;
import org.sng.main.common.BgpTopology;
import org.sng.main.common.Interface;
import org.sng.main.common.Layer2Topology;
import org.sng.main.conditions.BgpCondition;
import org.sng.main.diagnosis.BgpForwardingTree;
import org.sng.main.diagnosis.Generator;
import org.sng.main.diagnosis.Node;
import org.sng.main.diagnosis.VpnInstance;
import org.sng.main.diagnosis.BgpForwardingTree.TreeType;
import org.sng.main.localization.Violation;
import org.sng.main.util.ConfigTaint;
import org.sng.main.util.KeyWord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class BgpDiagnosis {
    public static String caseType;
    public static NetworkType networkType;
    private Generator errGenerator;
    private Generator corGenerator;
    private BgpTopology bgpTopology;

    public static String cfgRootPath;
    public static Map<String, String> cfgPathMap;
    public static InputData inputData;

    private String dstDev;
    private String dstIpString;
    private String dstVpnName;
    private boolean ifMpls;
    
    public BgpDiagnosis(String caseType, NetworkType errorType) {
        // input数据初始化
        this.caseType = caseType;
        this.networkType = errorType;
        inputData = new InputData();
        // 输入1：配置根目录
        cfgRootPath = InputData.getCfgRootPath(caseType, errorType);
        cfgPathMap = genCfgPathEachNode();
        // 输入2：BGP peer Info路径
        String peerInfoPath = inputData.getPeerInfoPath(caseType, errorType);
        // 输入3：错误流的Provenance Info
        // error trace: (errorDstDevName, errorDstPrefix) and prov files path
        dstDev = inputData.getErrorDstName(caseType, errorType);
        dstIpString = inputData.getErrorDstIp(caseType, errorType);
        dstVpnName = inputData.getErrorVpnName(dstIpString, errorType);
        ifMpls = false;
        String errBgpProvFilePath = inputData.getErrorProvFilePath(caseType, errorType, KeyWord.PROV_INFO_FILE_NAME);
        String errStaticProvFilePath = inputData.getErrorProvFilePath(caseType, errorType, KeyWord.RELATED_STATIC_INFO_FILE);
        
        // 输入5：Interface信息还原Layer2 Topology（物理拓扑），为了之后找到静态路由的下一跳设备名称
        String infInfoFilePath = inputData.getErrorProvFilePath(caseType, errorType, KeyWord.INTERFACE_INFO_FILE_PATH);
        Layer2Topology layer2Topology = Layer2Topology.fromJson(infInfoFilePath);

        // 中间状态1：构建BGP拓扑（Layer3 topology）generate the BGP topology using peer Info
        BgpTopology bgpTopology = new BgpTopology();
        bgpTopology.genBgpPeersFromJsonFile(peerInfoPath);
        // 中间状态2：BGP & Static的原始（错误）路由传播与转发的树 
        // generate the error traffic forwarding tree (paths)
        System.out.println("ERROR TREE GENERATE...");
        if ((dstDev==null || dstDev.equals("")) || (dstIpString==null || dstIpString.equals(""))) {
            throw new IllegalArgumentException("NULL error destination Ip or NULL error destination Node!");
        } else {
            errGenerator = new Generator(dstDev, dstIpString, bgpTopology, dstVpnName);
            errGenerator.setLayer2Topology(layer2Topology);
            errGenerator.serializeTreeFromJson(errStaticProvFilePath, TreeType.STATIC);
            errGenerator.serializeTreeFromJson(errBgpProvFilePath, TreeType.BGP);
            ifMpls = errGenerator.ifMpls();
            bgpTopology = errGenerator.getBgpTopology();
        }
        
        
        // 输出1：The path to save BGP condition json files
        String conditionPath = inputData.getConditionFilePath(caseType, errorType);
        
        
    }

    public Generator genCorrecGenerator() {
        if (corGenerator!=null) {
            return corGenerator;
        }
        // 输入4(可选)：正确流的Provenance Info
        // correct trace: (correctDstDevName, correctDstPrefix) and prov files path
        String corDstNode = inputData.getCorrectDstName(caseType, networkType);
        String corDstIp = inputData.getCorrectDstIp(caseType, networkType);
        String corBgpProvFilePath = inputData.getCorrectProvFilePath(caseType, networkType, KeyWord.PROV_INFO_FILE_NAME);
        String corStaticProvFilePath = inputData.getCorrectProvFilePath(caseType, networkType, KeyWord.RELATED_STATIC_INFO_FILE);

        // 中间状态2：BGP & Static的参考（正确）路由传播与转发的树 
        // generate the correct traffic forwarding tree (paths)
        System.out.println("REFERENCE TREE GENERATE...");
        if ((corDstNode==null || corDstNode.equals("")) || (corDstIp==null || corDstIp.equals(""))) {
            System.out.print("NULL reference destination Ip or NULL reference destination Node!");
        } else {
            corGenerator = new Generator(corDstNode, corDstIp, bgpTopology, "LTE_RAN");
            corGenerator.serializeTreeFromJson(corStaticProvFilePath, TreeType.STATIC);
            corGenerator.serializeTreeFromJson(corBgpProvFilePath, TreeType.BGP);
        }
        return corGenerator;
    }

    public Generator getErrGenerator() {
        return errGenerator;
    }

    public BgpForwardingTree diagnose(Set<String> reachNodes, Set<Interface> failedInfaces, boolean ifSave) {
        // use the correct traffic as a reference to generate the policy-compliant "Forwarding Tree"
        String conditionPath = inputData.getConditionFilePath(caseType, networkType);
        BgpForwardingTree reqTree = errGenerator.getBgpTree();
        
        // 模块1：BGP路由可达诊断
        if (isOldBgpTreeCorrect(reachNodes)) {
            // STEP1: 分析是否错在BGP上（是否现有静态路由可以修正）
            serializeToFile(conditionPath, new HashMap<>());
        } else {
            // STEP2: 生成BGP的路由转发树
            if (corGenerator!=null) {
                reqTree = errGenerator.genBgpTree(reachNodes, failedInfaces, corGenerator.getBgpTree());
            } else {
                reqTree = errGenerator.genBgpTree(reachNodes, failedInfaces, null);
            }
            Map<String, BgpCondition> conditions = reqTree.genBgpConditions(errGenerator.getBgpTopology());
            
            if (ifSave) {
                serializeToFile(conditionPath, conditions);
            }
        }
        // vpn一致性检查
        return reqTree;
    }

    // public Generator get

    public Map<String, Map<Integer, String>> localize(Set<String> reachNodes, boolean ifSave, Generator generator) {
        // STEP 1: 解析CPV返回的violated_rules，定位BGP协议相关的配置错误行
        String violatedRulePath = inputData.getViolateRulePath(caseType, networkType);
        Map<String, Map<Integer, String>> errlines = getErrorLinesEachNode(violatedRulePath, generator);
        if (ifSave) {
            serializeToFile(inputData.getResultFilePath(caseType, networkType), errlines);
            // 如果原始BGP Tree上node路由可达，这里reachNodes会被删完，所以输入拷贝的那份
            // TODO 再检查一次static的错误，在newBgpTree的基础上
        }
        // STEP 2: 根据BGP路由收敛的结果诊断静态路由的错误（还需完善，这个对静态路由的检查是互相依赖的，所以如果全部查到，应该要等BGP、IGP都收敛后才行）
        localizeInconsistentStatic(new HashSet<>(reachNodes), generator, ifSave);
        // STEP 3: 根据BGP路由收敛的结果诊断BGP VPN上
        localizeInconsitentCrossFromVpn(generator.getBgpTree());
        // STEP 4: 检查隧道使能

        return errlines;
    }

    /*
     * 最精确的检测方法是static的下一跳和BGP的转发路径不成环，但是由于无法提前得知BGP的实际转发路径，
     * 所以用了一个更强的条件：static下一跳和newBgpTree的下一跳一致
     */
    public Map<String, Map<Integer, String>> localizeInconsistentStatic(Set<String> reachNodes, Generator generator, boolean ifSave) {
        Map<String, Map<Integer, String>> lineMap = new HashMap<>();
        Set<String> checkedNodes = new HashSet<>();

        for (String node : reachNodes) {
            if (checkedNodes.contains(node) || node.equals(generator.getDstDevName())) {
                continue;
            } 
            // TODO 如果mpls，检查bestForwardingPath上的所有节点就行
            Set<String> nodesInSameAS = generator.getBgpTopology().getAllNodesInSameAs(node);
            for (String nodeInSameAS : nodesInSameAS) {
                if (nodeInSameAS.equals(generator.getDstDevName())) {
                    continue;
                }
                if (generator.getStaticTree().getNextHop(nodeInSameAS)!=null) {
                    String staticNextHop = generator.getStaticTree().getNextHop(nodeInSameAS);
                    // bgp的next-hop应该是一个远端节点？邻接的下一跳节点还是需要迭代查IGP的路径？
                    String bgpNextHop = generator.getBgpTree().getBestNextHop(nodeInSameAS); 
                    // TODO 确定bestBgp来自EBGP/IBGP/LOCAL，比较其和Static的优先级
                    if (bgpNextHop!=null && !staticNextHop.equals(bgpNextHop)) {
                        int staticPref = generator.getStaticTree().getBestRoute(nodeInSameAS).getPref();
                        int bgpRoutePref = generator.getBgpTree().getRouteTypePref(generator.getBgpTopology().getNodesRelation(nodeInSameAS, bgpNextHop));
                        if (staticPref >= bgpRoutePref) {
                            lineMap.put(nodeInSameAS, ConfigTaint.staticRouteLinesFinder(nodeInSameAS, generator.getStaticTree().getBestRoute(nodeInSameAS).getPrefix()));
                        }
                    }
                }
                checkedNodes.add(nodeInSameAS);
            }
        }
        if (ifSave) {
            if (lineMap.size()>0) {
                serializeToFile(inputData.getPreResultFilePath(caseType, networkType), lineMap);
            } 
        }
        return lineMap;
    }

    public Map<String, String> genCfgPathEachNode() {
        cfgPathMap = new HashMap<>();
        File rootFile = new File(cfgRootPath);
        File[] files = rootFile.listFiles();
        for (File file : files) {
            System.out.println(file.getName());
            cfgPathMap.put(file.getName().split("\\.")[0], file.getAbsolutePath());
        }
        return cfgPathMap;
    }

    public static String fromJsonToString(String filePath) {
        File file = new File(filePath);
        String jsonStr = "";
        if(file.exists()){
            try {
                jsonStr = FileUtils.readFileToString(file,"UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return jsonStr;
    }


    public static <T> void serializeToFile(String filePath,T object) {
        Gson gson = new GsonBuilder().serializeNulls().create();
        String jsonString = gson.toJson(object);
        // System.out.println(jsonString);
        try{
            File file = new File(filePath);
            if(!file.getParentFile().exists()){
                //若父目录不存在则创建父目录
                file.getParentFile().mkdirs();
            }
            if(file.exists()){
                file.delete();
            }
            file.createNewFile();
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(jsonString);
            writer.flush();
            writer.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public static Map<String, Map<Integer, String>> getErrorLinesEachNode(String filePath, Generator generator) {
        Map<String, Map<Integer, String>> errMap = new HashMap<>();
        // 输入是violated condition文件的路径
        Map<String, Violation> violations = genViolationsFromFile(filePath);
        if (violations!=null && violations.size()>0) {
            violations.forEach((node, vio)->{
                errMap.put(node, vio.localize(node, generator));
            });
            return errMap.entrySet().stream().filter(m->m.getValue()!=null && m.getValue().size()>0).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));  
        } else {
            return errMap;
        }
        
        
    }

    // 把peer connection这种双向的错误分发到两端的设备上
    public static Map<String, Violation> genViolationsFromFile(String filePath) {
        String jsonStr = fromJsonToString(filePath);
        System.out.println(jsonStr);
        if (jsonStr==null || jsonStr.equals("")) {
            throw new IllegalArgumentException("There is no violated rules file! " + "(" + filePath + ")");
        }
        String jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject().get(KeyWord.DEV2_VIOLATE_RULES).toString();
        Map<String, Violation> violations = new Gson().fromJson(jsonObject, new TypeToken<Map<String, Violation>>() {}.getType());


        for (String node : new HashSet<>(violations.keySet())) {
            Violation vio = violations.get(node);
            if (Violation.ifSetValid(vio.getViolateEbgpPeers())) {
                vio.getViolateEbgpPeers().forEach(neighbor->{
                    if (!violations.containsKey(neighbor)) {
                        violations.put(neighbor, new Violation());
                    }
                    violations.get(neighbor).addViolateEbgpPeer(node);
                });
            }
            if (Violation.ifSetValid(vio.getViolateIbgpPeers())) {
                vio.getViolateIbgpPeers().forEach(neighbor->{
                    if (!violations.containsKey(neighbor)) {
                        violations.put(neighbor, new Violation());
                    }
                    violations.get(neighbor).addViolateIbgpPeer(node);
                });
            }
        }
        return violations;
    }


    public boolean isOldBgpTreeCorrect(Set<String> nodes) {
        // 判断所有任一node，及其AS内其他所有节点是否都有forwarding path
        boolean flag = true;
        Set<String> checkedNodes = new HashSet<>();
        for (String node : nodes) {
            if (errGenerator.ifMpls()) {
                if (errGenerator.getBgpTree().getForwardingPath(node, errGenerator.getDstDevName())==null) {
                    flag = false;
                    break;
                }
            } else {
                Set<String> nodesInSameAS = errGenerator.getBgpTopology().getAllNodesInSameAs(node);
                if (nodesInSameAS!=null) {
                    for (String nodeInSameAS : nodesInSameAS) {
                        if (checkedNodes.contains(nodeInSameAS)) {
                            continue;
                        }
                        if (errGenerator.getBgpTree().getForwardingPath(nodeInSameAS, errGenerator.getDstDevName())==null) {
                            flag = false;
                            break;
                        }
                        checkedNodes.add(nodeInSameAS);
                    }
                    if (!flag) {
                        break;
                    }
                }
            }
        }
        return flag;
    }

    public Map<String, Set<Node>> genIgpConstraints(BgpForwardingTree newTree, boolean ifSave) {
        Map<String, Set<Node>> reachNodes = errGenerator.computeReachIgpNodes(newTree);
        if (reachNodes!=null) {
            if (ifSave) {
                String filePath = inputData.getIgpRequirementFilePath(caseType, networkType);
                serializeToFile(filePath, reachNodes);
            }
            return reachNodes;
        }
        return null;
    }

    public Map<VpnInstance, VpnInstance> localizeInconsitentCrossFromVpn(BgpForwardingTree bgpForwardingTree) {
        Map<VpnInstance, VpnInstance> inconsistentVpnMap = new HashMap<>();
        Set<String> checkedNodes = new HashSet<>();
        for (String node : bgpForwardingTree.getRouteReachableNodesInTree()) {
            if (node.equals(dstDev) || checkedNodes.contains(node)) {
                continue;
            }
            // 判断除dst外所有节点的路径上节点上下游的vpn是否可以匹配
            List<String> path;
            if (ifMpls) {
                path = bgpForwardingTree.getForwardingPath(node, dstDev); 
            } else {
                path = bgpForwardingTree.getBestRouteFromPath(node, dstDev);
            }
            // path的getter确保至少有一个节点在路径上
            ListIterator pathIter = path.listIterator(path.size()-1);
            String upstreamNode = path.get(path.size()-1);
            VpnInstance upstreamVpnInstance = ConfigTaint.getVpnInstance(upstreamNode, dstVpnName);
            while (pathIter.hasPrevious()) {
                String curNode = (String) pathIter.previous();
                VpnInstance curVpnInstance = ConfigTaint.getVpnInstance(curNode, upstreamVpnInstance.getVpnName());
                if (!curVpnInstance.canCrossFrom(upstreamVpnInstance)) {
                    inconsistentVpnMap.put(curVpnInstance, upstreamVpnInstance);
                }
                upstreamNode = curNode;
                upstreamVpnInstance = curVpnInstance;
                checkedNodes.add(curNode);
            }
        }
        return inconsistentVpnMap;
    }

    public Generator getNewGenerator() {
        String dstNode = inputData.getErrorDstName(caseType, networkType);
        String dstIp = inputData.getErrorDstIp(caseType, networkType);
        String dstVpnName = inputData.getErrorVpnName(dstIp, networkType);
        String bgpProvFilePath = inputData.getErrorProvFilePath(caseType, networkType, KeyWord.PROV_INFO_FILE_NAME);
        String staticProvFilePath = inputData.getErrorProvFilePath(caseType, networkType, KeyWord.RELATED_STATIC_INFO_FILE);
        
        errGenerator = new Generator(dstNode, dstIp, errGenerator.getBgpTopology(), dstVpnName);
        errGenerator.setLayer2Topology(errGenerator.getLayer2Topology());
        errGenerator.serializeTreeFromJson(staticProvFilePath, TreeType.STATIC);
        errGenerator.serializeTreeFromJson(bgpProvFilePath, TreeType.BGP);

        return errGenerator;
    }

    public Map<String, Map<Integer, String>> getMissingLdpLines() {
        return null;
    }
}
