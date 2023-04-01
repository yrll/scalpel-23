import os
import linecache
import re
import json
import time
import string

rmaps = {}
prefix_map = {}
inv_prefix_map = {}
ip_prefixname = {}     #record the ip of interface in the cfg file that is not a peer--.cfg

def main():
    #file_processing("./example1/Arnes_abs_order_4_2022-09-09/configs/Divaca.cfg")

    f = open("./test_name.txt", 'w')      # 先创建一个空的文本
    num = 0
    path = "C:\\Users\\31139\\PycharmProjects\\config_translation\\configs\\"     # 指定需要读取文件的目录
    files = os.listdir(path)    # 采用listdir来读取所有文件
    files.sort()  # 排序
    #print(files)
    files_name = []  # 创建一个空列表
    for file_ in files:  # 循环读取每个文件名
        if not os.path.isdir(path + file_):  # 判断该文件是否是一个文件夹
            f_name = str(file_)
            files_name.append(path + f_name)  # 把当前文件名返加到列表里
            f.write(f_name + '\n')  # 写入之前的文本中
    print("====file name")
    print(files_name)  # 看一下列表里的内容

    for file_name in files_name:
        file_processing(file_name)

    json_file = {}
    json_file["rmaps"] = rmaps
    json_file["prefix_map"] = prefix_map
    json_file["inv_prefix_map"] = inv_prefix_map

    with open("./res.json", "w") as write_f:
        json.dump(json_file, write_f, indent=2, ensure_ascii=False)

    print("加载入文件完成...")


##process a cfg file
def file_processing(file):
    #lines = file.readlines()
    #last_line = ""
    #next_line = ""
    #text = ""
    total_line_nums = len(open(file, 'r').readlines())
    line_num = 1   # line_num start with 0
    com_lists = []     #record the communities_list in this cfg file
    prefix_lists = []  # record the prefix-list in this cfg file
    elements = []
    network_ip = []      #record the ip after network in the peer--.cfg
    peer_ip_prefixname = {}  #record the ip of interface in the cfg file that is a peer--.cfg
    peer_tag = False  #record if this file is a peer--.cfg file
    hostname = ""     #record the hostname
    index_set = []

    # if re.search("Peer" ,file):
    #     peer_tag = True

    #element = ele()
    while line_num <= total_line_nums:
        this_line = linecache.getline(file, line_num).strip()
        if line_num == 1:
            ind_set = this_line.split(" ")
            for ind in ind_set:
                index_set.append(ind)
            print(index_set)
        if this_line.startswith("ip extcommunity-filter"):   # rercord the community list
            name = ''
            list_id = ""
            access = ""
            communities = []
            type = ""  ##basic/advanced
            rt = False

            content = this_line.split(" ")
            for item in content:
                if item == "basic" or item == "advanced":
                    nameindex = content.index(item)
                    type = item
                    name = content[nameindex+1]
                if item == "index":
                    list_id_index = content.index(item)
                    list_id = content[list_id_index + 1]
                if item == "permit" or item == "deny":
                    access = item
                    comm_index = content.index(item)+1
                if item == "rt":
                    rt = True
                    comm_index = content.index(item)+1
            for i in range(comm_index, len(content)):
                communities.append(content[i])
            com_lists.append(communities_list(name, list_id, access, type, communities, rt))
            line_num += 1
        elif this_line.startswith("ip ip-prefix"): # record the prefix-list name, access, networks, prefix_range_front, prefix_range_end
            name = ""
            index_id = ""
            access = ""
            networks = []
            prefix_range_front = ""
            prefix_range_end = ""

            content = this_line.split(" ")
            for item in content:
                if item == "ip-prefix":
                    name = content[content.index(item) + 1]
                if item == "index":
                    index_id = content[content.index(item) + 1]
                if item == "permit" or item == "deny":
                    access = item
                    networks.append(content[content.index(item) + 1])
                    prefix_range_front = content[content.index(item) + 2]
                if item == "less-equal":
                    prefix_range_end = item[content.index(item) + 1]
            prefix_lists.append(prefix(name, index_id, access, networks, prefix_range_front, prefix_range_end))
            line_num += 1
        else:
            line_num += 1

    line_num = 1
    while line_num <= total_line_nums:
        this_line = linecache.getline(file, line_num).strip()
        if this_line.startswith("route-policy"):
            f = re.match('route-policy', this_line).span()
            i = f[1] + 1
            name = ""
            access = ""
            lineno_str = ""
            lineno = 0
            matches = []
            actions = []
            #get name
            while this_line[i] != " ":
                name += this_line[i]
                i += 1
            i += 1  # skip the blank space
            # process the permit/deny operation
            while this_line[i] != " ":
                access += this_line[i]
                i += 1
            i += 1
            print(len(this_line))
            while i < len(this_line):
                if this_line[i].isdigit():
                    lineno_str += this_line[i]
                    i += 1
                else:
                    i += 1
            lineno = int(lineno_str)
            print(lineno)
            # ele1 = ele(name)
            line_num = line_num + 1
            this_line = linecache.getline(file, line_num).strip()
            while this_line.startswith("if-match") or this_line.startswith("apply"):
                if this_line.startswith("apply cost"):
                    f1 = re.match('apply cost', this_line).span()
                    k = f1[1] + 1
                    cost = 0
                    cost_str = ""
                    while k < len(this_line) and this_line[k] != " ":
                        cost_str += this_line[k]
                        k += 1
                    cost = int(cost_str)
                    action_cost = ActionSetMed(cost, index_set[line_num-2])
                    actions.append(action_cost)
                elif this_line.startswith("apply preferred-value"):
                    f1 = re.match('apply preferred-value', this_line).span()
                    k = f1[1] + 1
                    pv = 0
                    pv_str = ""
                    while k < len(this_line) and this_line[k] != " ":
                        pv_str += this_line[k]
                        k += 1
                    pv = int(pv_str)
                    action_pv = ActionSetPreferredValue(pv, index_set[line_num-2])
                    actions.append(action_pv)
                elif this_line.startswith("apply ip-address next-hop"):
                    f1 = re.match('apply ip-address next-hop', this_line).span()
                    k = f1[1] + 1
                    nh_str = ""
                    while k < len(this_line) and this_line[k] != " ":
                        nh_str += this_line[k]
                        k += 1
                    action_nh = ActionSetNextHop(nh_str, index_set[line_num-2])
                    actions.append(action_nh)

                elif this_line.startswith("if-match ip-prefix"):
                    f1 = re.match('if-match ip-prefix', this_line).span()
                    k = f1[1] + 1
                    p_list = []
                    p_list1 = []
                    match_plist = MatchIpPrefixListList(p_list, index_set[line_num-2])
                    while k < len(this_line):
                        p_name = ""
                        while k < len(this_line) and this_line[k] != " ":
                            p_name += this_line[k]
                            k += 1
                        p_list1.append(p_name)
                        k += 1
                    for prefix_name in p_list1:
                        for prefix_list in prefix_lists:
                            if prefix_name == prefix_list.name:
                                match_plist.prefix_list.append(prefix_list)
                    matches.append(match_plist)
                elif this_line.startswith("if-match extcommunity-filter"):  # express community using string ??
                    f1 = re.match('if-match extcommunity-filter', this_line).span()
                    k = f1[1] + 1
                    com_name = ""
                    while k < len(this_line):
                        com_name += this_line[k]
                        k += 1
                    # find this communitylist in com_list

                    c_list = []
                    match_com = MatchCommunitiesList(c_list, index_set[line_num-2])
                    for com_l in com_lists:
                        if com_l.name == com_name:
                            match_com.communities_lists.append(com_l)

                    #match_com = MatchCommunitiesList(list_id)
                    matches.append(match_com)

                line_num = line_num + 1
                if line_num <= total_line_nums:
                    this_line = linecache.getline(file, line_num).strip()
                else:
                    break

            val1 = val(access, lineno, matches, actions)
            add_val(val1, elements, name)
            #next line is "!"
        else:
            line_num += 1
        '''
        elif last_line == "!" and this_line.startswith("route-map"):
            last_line = this_line
        '''
    output_json(elements)
    #print(len(elements))
    #print("111")


def get_ipmask(ip, mask, peer_tag):
    #ip_0 = ip_1 = ip_2 = ip_3 = 0
    #mask_0 = mask_1 = mask_2 = mask_2 = 0
    ip_num = []
    get_num(ip, ip_num)
    mask_num = []
    get_num(mask, mask_num)
    ipmask_num = []
    i = 0
    res = ""
    #lowbit
    mask_bit = 0
    bit_tag = False
    while i < 4:
        if peer_tag:
            ipmask_num.append(ip_num[i] & mask_num[i])       #bitwise and operate
        else:
            ipmask_num.append(ip_num[i])
        res += str(ipmask_num[i])
        if i != 3:
            res += "."
        else:
            res += "/"
        if not(bit_tag) and mask_num[i] & -mask_num[i] != 1:
            num1 = mask_num[i] & -mask_num[i]
            #print(num1)
            if num1 == 0:
                mask_bit = i * 8
            else:
                str1 = bin(num1)   #get a binary representation of a int num 0b
                len1 = len(str1) - 2  #minus the length of 0b
                #print("len1:")
                #print(len1)
                mask_bit = (i + 1) * 8 - len1 + 1
            bit_tag = True
        i += 1

    res += str(mask_bit)
    return res

def get_num(str, str_num):
    i = 0
    while i < len(str):
        str1 = ""
        while i < len(str) and str[i] != ".":
            str1 += str[i]
            i += 1
        str_num.append(int(str1))
        i += 1

def add_val(val, elements, name):
    tag = False
    for element in elements:
        if name == element.name:
            tag = True
            element.ele_vals.append(val)
    if not tag:
        ele_vals = []
        ele_vals.append(val)
        element = ele(name, ele_vals)
        elements.append(element)


def output_json(elements):      #output to json, process one configuration file
    #rmap_content = {}  # save a element
    for element in elements:   #process a route map with same name
        name = element.name
        one_name_rmap_content = []
        for one_ele in element.ele_vals:
            map_content = {}
            map_content["name"] = name
            map_content["access"] = one_ele.access
            map_content["lineno"] = one_ele.lineno
            if len(one_ele.matches) == 0:
                map_content["matches"] = None    #null ???
            else:
                matches = []
                for match in one_ele.matches:
                    matches_content = {}
                    matches_content["match_type"] = match.match_type
                    matches_content["cfg_lines"] = match.cfg_lines
                    if match.match_type == "MatchIpPrefixListList":
                        match_prefix_list = {}
                        match_prefix_list["name"] = match.prefix_list[0].name
                        match_prefix_list["index_id"] = match.prefix_list[0].index_id
                        match_prefix_list["access"] = match.prefix_list[0].access
                        match_prefix_list["networks"] = match.prefix_list[0].networks    ###????  directly list = list???
                        match_prefix_list["prefix_range_front"] = match.prefix_list[0].prefix_range_front
                        if match.prefix_list[0].prefix_range_end != "":
                            match_prefix_list["prefix_range_end"] = match.prefix_list[0].prefix_range_end
                        matches_content["prefix_list"] = match_prefix_list
                        matches.append(matches_content)
                    elif match.match_type == "MatchCommunitiesList":
                        match_com_list = {}
                        match_com_list["name"] = match.communities_lists[0].name
                        match_com_list["list_id"] = match.communities_lists[0].list_id
                        match_com_list["access"] = match.communities_lists[0].access
                        match_com_list["communities"] = match.communities_lists[0].communities
                        match_com_list["type"] = match.communities_lists[0].type
                        match_com_list["rt"] = match.communities_lists[0].rt
                        matches_content["communities_list"] = match_com_list
                        matches.append(matches_content)
                    elif match.match_type == "MatchNextHop":
                        matches_content["nexthop"] = match.nexthop      ##nexthop mask >>>>>
                        matches.append(matches_content)
                    map_content["matches"] = matches
            if len(one_ele.actions) == 0:
                map_content["actions"] = None
            else:
                actiones = []
                for action1 in one_ele.actions:
                    act_content = {}
                    act_content["action"] = action1.action
                    act_content["cfg_lines"] = action1.cfg_lines
                    if action1.action == "ActionSetLocalPref":
                        act_content["value"] = action1.value
                        actiones.append(act_content)
                    elif action1.action == "ActionSetCommunity":
                        act_content["communities"] = action1.communities
                        act_content["additive"] = action1.addictive
                        actiones.append(act_content)
                    elif action1.action == "ActionSetMed":
                        act_content["value"] = action1.value
                        actiones.append(act_content)
                    elif action1.action == "ActionSetPreferredValue":
                        act_content["value"] = action1.value
                        actiones.append(act_content)
                    elif action1.action == "ActionSetNextHop":
                        act_content["next_hop"] = action1.next_hop
                        actiones.append(act_content)
                map_content["actions"] = actiones
            one_name_rmap_content.append(map_content)
        rmaps[name] = one_name_rmap_content     #all content of one table


class ele:
    name = ""
    ele_vals = []        #name ->many val     one name to many value
    def __init__(self):
        self.name = ""
        self.ele_vals = []
    def __init__(self, name):
        self.name = name
        self.ele_vals = []
    def __init__(self, name, ele_vals):
        self.name = name
        self.ele_vals = ele_vals
    def add_val(self, ele_val):
        self.ele_vals.append(ele_val)

class val:
    access = ""
    lineno = ""
    matches = []  # match_type -> communities_list / IPprefix_list
    actions = []    # action -> action_content
    def __init__(self, access, lineno, matches, actions):
        self.access = access
        self.lineno = lineno
        self.matches = matches
        self.actions = actions
    def add_Match(self, match):
        self.matches.append(match)
    def add_action(self, action):
        self.actions.append(action)

###Match
class MatchCommunitiesList:
    match_type = "MatchCommunitiesList"
    cfg_lines = -1
    communities_lists = []
    def __init__(self):
        self.communities_lists = []
    def __init__(self, communities_lists, cfg_lines):
        self.communities_lists = communities_lists
        self.cfg_lines = cfg_lines

class communities_list:##适配华为config的格式
    name = ''
    list_id = ""
    access = ""
    communities = []
    type = ""##basic/advanced
    rt = False
    def __init__(self, name, list_id, access, type, communities, rt):
        self.name = name
        self.list_id = list_id
        self.access = access
        self.type = type
        self.communities = communities
        self.rt = rt


class MatchNextHop:
    match_type = "MatchNextHop"
    nexthop = ""
    cfg_lines = -1
    def __init__(self, nexthop, cfg_lines):
        self.nexthop = nexthop
        self.cfg_lines = cfg_lines

class MatchIpPrefixListList:
    match_type = "MatchIpPrefixListList"
    prefix_list = []
    cfg_lines = -1
    def __init__(self):
        self.prefix_list = []
    def __init__(self, prefix_list, cfg_lines):
        self.prefix_list = prefix_list
        self.cfg_lines = cfg_lines

class prefix:
    name = ""
    index_id = ""
    access = ""
    networks = []
    prefix_range_front = 0
    prefix_range_end = -1
    def __init__(self, name, index_id, access, networks, prefix_range_front, prefix_range_end):
        self.name = name
        self.index_id = index_id
        self.access = access
        self.networks = networks
        self.prefix_range_front = prefix_range_front
        self.prefix_range_end = prefix_range_end

###Action
class ActionSetLocalPref:
    action = "ActionSetLocalPref"
    cfg_lines = -1
    value = -1
    def __init__(self, value, cfg_lines):
        self.value = value
        self.cfg_lines = cfg_lines

class ActionSetCommunity:
    action = "ActionSetCommunity"
    communities = []
    cfg_lines = -1
    addictive = False
    def __init__(self,communities, addictive, cfg_lines):
        self.communities = communities
        self.addictive = addictive
        self.cfg_lines = cfg_lines

class ActionSetMed:#cost
    action = "ActionSetMed"
    value = []
    cfg_lines = -1
    def __init__(self,value, cfg_lines):
        self.value = value
        self.cfg_lines = cfg_lines

class ActionSetPreferredValue:
    action = "ActionSetPreferredValue"
    value = []
    cfg_lines = -1
    def __init__(self, value, cfg_lines):
        self.value = value
        self.cfg_lines = cfg_lines

class ActionSetNextHop:
    action = "ActionSetNextHop"
    next_hop = ""
    cfg_lines = -1
    def __init__(self, next_hop, cfg_lines):
        self.next_hop = next_hop
        self.cfg_lines = cfg_lines

time_start = time.time()
main()
time_end = time.time()
print("time used:")
print(time_end - time_start)
print(rmaps)
#print(prefix_map)
#print(inv_prefix_map)

