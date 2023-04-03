package org.sng.main.localization;

import java.util.List;
import java.util.Map;

/*
 * Localize "prefer" errors
 * ==> Why a route is less prefered than the other route?
 * 1) incorrect policy / redundant policy
 * 2) policy missing
 */
public class RouteSelectionLocalizer implements Localizer {

    String node;
    String lessPreferRouteFromNeighbor;
    String PreferedRouteFromNeighbor;

    String lessPreferRouteImPolicy;
    String lessPreferRouteExPolicy;

    String PreferRouteImPolicy;
    String PreferRouteExPolicy;

    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getErrorConfigLines'");
    }
    
}
