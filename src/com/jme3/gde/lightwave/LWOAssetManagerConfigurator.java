package com.jme3.gde.lightwave;

import com.jme3.asset.AssetManager;
import com.jme3.gde.core.assets.AssetManagerConfigurator;

/**
 *
 * @author Stan Hebben
 */
@org.openide.util.lookup.ServiceProvider(service = AssetManagerConfigurator.class)
public class LWOAssetManagerConfigurator implements AssetManagerConfigurator {
    @Override
    public void prepareManager(AssetManager manager) {
        manager.registerLoader(com.jme3.scene.plugins.LWOLoader.class, "lwo");
    }
}
