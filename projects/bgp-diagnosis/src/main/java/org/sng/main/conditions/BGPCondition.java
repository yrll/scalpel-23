package org.sng.main.conditions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.sng.datamodel.Prefix;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/*
 * Each BGP Condition is for a router (for single prefix)
 */
public class BgpCondition {

    @Expose(serialize = false)
    private transient Prefix _network;

    @SerializedName("vpnName")
    private String _vpnName;

    @SerializedName("ipPrefix")
    private String _networkString;

    @SerializedName("redistribution")
    private boolean _redistribution;

    @SerializedName("rrClients")
    private List<String> _rrClients;

    @SerializedName("selectionRoute")
    private SelectionRoute _route;

    @SerializedName("propNeighbors")
    private List<String> _propNeighbors;

    @SerializedName("acptNeighbors")
    private List<String> _acptNeighbors;

    @SerializedName("ibgpPeers")
    private List<String> _ibgpPeers;

    @SerializedName("ebgpPeers")
    private List<String> _ebgpPeers;


    public BgpCondition(Builder builder) {
        this._network = builder._network;
        this._networkString = this._network.toString();
        this._redistribution = builder._redistribution;
        this._route = builder._route;
        this._propNeighbors = builder._propNeighbors;
        this._acptNeighbors = builder._acptNeighbors;
        this._rrClients = builder._rrClients;
        this._ibgpPeers = builder._ibgpPeers;
        this._ebgpPeers = builder._ebgpPeers;
        this._vpnName = builder._vpnName;
    }

    public static Map<String, BgpCondition> deserialize(String filePath) {
        File file = new File(filePath);
        String jsonStr;
        try {
            jsonStr = FileUtils.readFileToString(file,"UTF-8");
            Map<String, BgpCondition> conds = new Gson().fromJson(jsonStr, new TypeToken<Map<String, BgpCondition>>() {}.getType());
            return conds;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }


    public void setRedistribute(boolean value) {
        this._redistribution = value;
    }


    public static class Builder {
        private Prefix _network;

        private boolean _redistribution;

        private List<String> _rrClients;

        private SelectionRoute _route;

        private List<String> _propNeighbors;

        private List<String> _acptNeighbors;

        private List<String> _ibgpPeers;

        private List<String> _ebgpPeers;

        private String _vpnName;
    
        public Builder(Prefix network) {
            this._network = network;
        }

        public Builder vpnName(String name) {
            this._vpnName = name;
            return this;
        }
        
        public Builder redistribution(boolean flag) {
            this._redistribution = flag;
            return this;
        }

        public Builder rrClient(List<String> rrClients) {
            this._rrClients = rrClients;
            return this;
        }

        public Builder selectionRoute(SelectionRoute route) {
            this._route = route;
            return this;
        }

        public Builder propNeighbors(List<String> nodes) {
            this._propNeighbors = nodes;
            return this;
        }

        public Builder acptNeighbors(List<String> nodes) {
            this._acptNeighbors = nodes;
            return this;
        }

        public Builder ibgpPeers(List<String> peers) {
            this._ibgpPeers = peers;
            return this;
        }

        public Builder ebgpPeers(List<String> peers) {
            this._ebgpPeers = peers;
            return this;
        } 


        public BgpCondition build() {
            return new BgpCondition(this);
        }
    }

}
