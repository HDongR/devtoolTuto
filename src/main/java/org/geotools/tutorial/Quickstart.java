package org.geotools.tutorial;

import java.io.File;
import java.io.IOException;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.data.JFileDataStoreChooser;

public class Quickstart { 
	public static void main(String[] args) {
		// display a data store file chooser dialog for shapefiles
        File file = JFileDataStoreChooser.showOpenFile("shp", null);
        if (file == null) {
            return;
        }

       
		try {
			FileDataStore store = FileDataStoreFinder.getDataStore(file);
			SimpleFeatureSource featureSource = store.getFeatureSource();

	        // Create a map content and add our shapefile to it
	        MapContent map = new MapContent();
	        map.setTitle("Quickstart");

	        Style style = SLD.createSimpleStyle(featureSource.getSchema());
	        Layer layer = new FeatureLayer(featureSource, style);
	        map.addLayer(layer);

	        // Now display the map
	        JMapFrame.showMap(map);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
	} 
}
