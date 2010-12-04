/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jme3.gde.lightwave;

import com.jme3.export.binary.BinaryExporter;
import com.jme3.gde.core.assets.AssetData;
import com.jme3.gde.core.assets.ProjectAssetManager;
import com.jme3.gde.core.assets.SpatialAssetDataObject;
import com.jme3.scene.Spatial;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

public final class ConvertLWO implements ActionListener {

    private final List<DataObject> context;

    public ConvertLWO(List<DataObject> context) {
        this.context = context;
    }

    public void actionPerformed(ActionEvent ev) {
        for (DataObject dataObject : context) {
            try {
                final ProjectAssetManager manager = dataObject.getLookup().lookup(ProjectAssetManager.class);
                if (manager == null) {
                    StatusDisplayer.getDefault().setStatusText("Project has no AssetManager!");
                    continue;
                }
                FileObject file = dataObject.getPrimaryFile();
                Spatial model = (Spatial) ((SpatialAssetDataObject) dataObject).loadAsset();
                //export model
                String outputPath = file.getParent().getPath() + File.separator + file.getName() + ".j3o";
                BinaryExporter exp = BinaryExporter.getInstance();
                File outFile = new File(outputPath);
                exp.save(model, outFile);
                //store original asset path interface properties
                DataObject targetModel = DataObject.find(FileUtil.toFileObject(outFile));
                AssetData properties = targetModel.getLookup().lookup(AssetData.class);
                if (properties != null) {
                    properties.loadProperties();
                    properties.setProperty("ORIGINAL_PATH", manager.getRelativeAssetPath(file.getPath()));
                    properties.saveProperties();
                }
                StatusDisplayer.getDefault().setStatusText("Created file " + file.getName() + ".j3o");
                //update the tree
                dataObject.getPrimaryFile().getParent().refresh();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
