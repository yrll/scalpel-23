import java.io.*;
import java.util.*;

public class GetPolicyUtil {

    private String dev;
    private String policy;

    private Map<Integer,String> route_policy_map;

    private Set<Integer> index = new HashSet<>();

    private List<String> policy_set = new ArrayList<>();

    public String getDev() {
        return dev;
    }

    public void setDev(String dev) {
        this.dev = dev;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public Set<Integer> get_index(){
        this.index = route_policy_map.keySet();
        return index;
    }

    public List<String> get_policys() {
        for (int key : route_policy_map.keySet()) {
            String value = route_policy_map.get(key);
            this.policy_set.add(value);
        }
        return policy_set;
    }

    public void write(){
        try {
            String filepath = "route_policy_"+getDev()+"_"+getPolicy()+".txt";
            File file = new File(filepath);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fileWriter);
            for (Integer lineno:index){
                bw.write(lineno.toString()+" ");
            }
            bw.newLine();
            for (String content:policy_set) {
                bw.write(content);
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<Integer, String> getresult(String dev, String policyName){

        String fileName = "src\\ISIS\\Case1.1\\" +dev + ".cfg";//fileName改成自己存放配置的目录
        System.out.println("filename:"+fileName);

        String route_policy = "route-policy "+policyName;
        System.out.println("route-policy:"+route_policy);

        File file = new File(fileName);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            int line = 1;
            boolean flag = false;
            Map<Integer,String> route_policy_map = new LinkedHashMap<>();
            ArrayList<String> if_match_list = new ArrayList<String>();
            while ((tempString = reader.readLine()) != null) {
                tempString = tempString.trim();
                if(tempString.startsWith(route_policy)) {//输出route-policy那行
                    System.out.println("line " + line + ": " + tempString);
                    route_policy_map.put(line,tempString);
                    flag = true;
                }
                else {
                    if(flag && tempString.equals("#")){//输出到#那行
//                        System.out.println("line " + line + ": " + tempString);
//                        route_policy_map.put(line,tempString);
                        flag = false;
                    }else if(flag){//#输出route-policy到#之间的内容
                        System.out.println("line " + line + ": " + tempString);
                        route_policy_map.put(line,tempString);
                        if(tempString.startsWith("if-match")){
                            tempString = tempString.replaceAll("if-match","ip");
                            if_match_list.add(tempString);
                        }
                    }
                    else{
                        String modified_tempString = tempString;
                        if(tempString.contains("basic")){
                            modified_tempString = tempString.replace("basic ","");
                        }
                        else if(tempString.contains("advanced")){
                            modified_tempString = tempString.replace("advanced ","");
                        }
                        for(String keyword:if_match_list){
                            if(modified_tempString.startsWith(keyword)){
                                System.out.println("line " + line + ": " + tempString);
                                route_policy_map.put(line,tempString);
                                break;
                            }

                        }
                    }
                }
                line++;
            }
//            System.out.println(route_policy_map);
            reader.close();
            return route_policy_map;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return null;
    }

    public static void main(String[] args){
//        Get_Policy_Util g1 = new Get_Policy_Util();
//        g1.get_result("ASG1", "to-csg-as-master");//输入配置文件名和策略名称policy
//        System.out.println(g1.route_policy_map);//返回路由策略和对应行号
//        System.out.println(g1.get_policys());//返回路由策略
//        System.out.println(g1.get_index());//返回行号
//        g1.write();

        System.out.println(GetPolicyUtil.getresult("ASG1", "pref-rr"));
    }

}
