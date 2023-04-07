package org.sng.main.localization;

import java.util.Map;

import org.sng.main.common.BgpRoute;

public class PreferenceLocalizer implements Localizer {

    BgpRoute shouldPreferRoute;
    BgpRoute actualPreferRoute;

    @Override
    public Map<Integer, String> getErrorConfigLines() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getErrorConfigLines'");
    }
    
}
