package org.geotools.tutorial;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.JProgressWindow;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.util.InternationalString;
import org.opengis.util.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeometryCRS {

	final static private Logger LOGGER = LoggerFactory.getLogger(GeometryCRS.class);

	private File sourceFile;
	private SimpleFeatureSource featureSource;
	private MapContent map;

	public static void main(String[] args) throws Exception {
		GeometryCRS lab = new GeometryCRS();
		lab.displayShapefile();
	}

	private void displayShapefile() throws Exception {
		sourceFile = JFileDataStoreChooser.showOpenFile("shp", null);
		if (sourceFile == null) {
			return;
		}
		FileDataStore store = FileDataStoreFinder.getDataStore(sourceFile);
		featureSource = store.getFeatureSource();

		// Create a map context and add our shapefile to it
		map = new MapContent();
		Style style = SLD.createSimpleStyle(featureSource.getSchema());
		Layer layer = new FeatureLayer(featureSource, style);
		map.layers().add(layer);

		// Create a JMapFrame with custom toolbar buttons
		JMapFrame mapFrame = new JMapFrame(map);
		mapFrame.enableToolBar(true);
		mapFrame.enableStatusBar(true);

		JToolBar toolbar = mapFrame.getToolBar();
		toolbar.addSeparator();
		toolbar.add(new JButton(new ValidateGeometryAction()));
		toolbar.add(new JButton(new ExportShapefileAction()));

		// Display the map frame. When it is closed the application will exit
		mapFrame.setSize(800, 600);
		mapFrame.setVisible(true);
	}

	class ValidateGeometryAction extends SafeAction {
		ValidateGeometryAction() {
			super("Validate geometry");
			putValue(Action.SHORT_DESCRIPTION, "Check each geometry");
		}

		public void action(ActionEvent e) throws Throwable {
			int numInvalid = validateFeatureGeometry(null);
			String msg;
			if (numInvalid == 0) {
				msg = "All feature geometries are valid";
			} else {
				msg = "Invalid geometries: " + numInvalid;
			}
			JOptionPane.showMessageDialog(null, msg, "Geometry results", JOptionPane.INFORMATION_MESSAGE);
		}

		private int validateFeatureGeometry(ProgressListener progress) throws Exception {
			final SimpleFeatureCollection featureCollection = featureSource.getFeatures();

			// Rather than use an iterator, create a FeatureVisitor to check each fature
			class ValidationVisitor implements FeatureVisitor {
				public int numInvalidGeometries = 0;

				public void visit(Feature f) {
					SimpleFeature feature = (SimpleFeature) f;
					Geometry geom = (Geometry) feature.getDefaultGeometry();
					if (geom != null && !geom.isValid()) {
						numInvalidGeometries++;
						System.out.println("Invalid Geoemtry: " + feature.getID());
					}
				}
			}

			ValidationVisitor visitor = new ValidationVisitor();

			// Pass visitor and the progress bar to feature collection
			featureCollection.accepts(visitor, progress);
			return visitor.numInvalidGeometries;
		}
	}

	class ValidateGeometryAction2 extends SafeAction {
		ValidateGeometryAction2() {
			super("Validate geometry");
			putValue(Action.SHORT_DESCRIPTION, "Check each geometry");
		}

		public void action(ActionEvent e) throws Throwable {
			// Here we use the SwingWorker helper class to run the validation routine in a
			// background thread, otherwise the GUI would wait and the progress bar would
			// not be
			// displayed properly
			SwingWorker worker = new SwingWorker<String, Object>() {
				protected String doInBackground() throws Exception {
					// For shapefiles with many features its nice to display a progress bar
					final JProgressWindow progress = new JProgressWindow(null);
					progress.setTitle("Validating feature geometry");

					int numInvalid = validateFeatureGeometry(progress);
					if (numInvalid == 0) {
						return "All feature geometries are valid";
					} else {
						return "Invalid geometries: " + numInvalid;
					}
				}

				private int validateFeatureGeometry(ProgressListener progress) throws Exception {
					final SimpleFeatureCollection featureCollection = featureSource.getFeatures();

					// Rather than use an iterator, create a FeatureVisitor to check each fature
					class ValidationVisitor implements FeatureVisitor {
						public int numInvalidGeometries = 0;

						public void visit(Feature f) {
							SimpleFeature feature = (SimpleFeature) f;
							Geometry geom = (Geometry) feature.getDefaultGeometry();
							if (geom != null && !geom.isValid()) {
								numInvalidGeometries++;
								System.out.println("Invalid Geoemtry: " + feature.getID());
							}
						}
					}

					ValidationVisitor visitor = new ValidationVisitor();

					// Pass visitor and the progress bar to feature collection
					featureCollection.accepts(visitor, progress);
					return visitor.numInvalidGeometries;
				}
				
				protected void done() {
					try {
						Object result = get();
						JOptionPane.showMessageDialog(null, result, "Geometry results", JOptionPane.INFORMATION_MESSAGE);
					} catch (Exception ignore) {
					}
				}
			};
			// This statement runs the validation method in a background thread
			worker.execute();
			
			
		}
	}

	class ExportShapefileAction extends SafeAction {
		ExportShapefileAction() {
			super("Export...");
			putValue(Action.SHORT_DESCRIPTION, "Export using current crs");
		}

		public void action(ActionEvent e) throws Throwable {
			exportToShapefile();
		}

		private void exportToShapefile() throws Exception {
			SimpleFeatureType schema = featureSource.getSchema();
			JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
			chooser.setDialogTitle("Save reprojected shapefile");
			chooser.setSaveFile(sourceFile);
			int returnVal = chooser.showSaveDialog(null);
			if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
				return;
			}
			File file = chooser.getSelectedFile();
			if (file.equals(sourceFile)) {
				JOptionPane.showMessageDialog(null, "Cannot replace " + file);
				return;
			}

			CoordinateReferenceSystem dataCRS = schema.getCoordinateReferenceSystem();
			CoordinateReferenceSystem worldCRS = map.getCoordinateReferenceSystem();
			boolean lenient = true; // allow for some error due to different datums
			MathTransform transform = CRS.findMathTransform(dataCRS, worldCRS, lenient);

			SimpleFeatureCollection featureCollection = featureSource.getFeatures();

			DataStoreFactorySpi factory = new ShapefileDataStoreFactory();
			Map<String, Serializable> create = new HashMap<>();
			create.put("url", file.toURI().toURL());
			create.put("create spatial index", Boolean.TRUE);
			DataStore dataStore = factory.createNewDataStore(create);
			SimpleFeatureType featureType = SimpleFeatureTypeBuilder.retype(schema, worldCRS);
			dataStore.createSchema(featureType);

			// Get the name of the new Shapefile, which will be used to open the
			// FeatureWriter
			String createdName = dataStore.getTypeNames()[0];

			Transaction transaction = new DefaultTransaction("Reproject");
			try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = dataStore.getFeatureWriterAppend(createdName, transaction); SimpleFeatureIterator iterator = featureCollection.features()) {
				while (iterator.hasNext()) {
					// copy the contents of each feature and transform the geometry
					SimpleFeature feature = iterator.next();
					SimpleFeature copy = writer.next();
					copy.setAttributes(feature.getAttributes());

					Geometry geometry = (Geometry) feature.getDefaultGeometry();
					Geometry geometry2 = JTS.transform(geometry, transform);

					copy.setDefaultGeometry(geometry2);
					writer.write();
				}
				transaction.commit();
				JOptionPane.showMessageDialog(null, "Export to shapefile complete");
			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();
				JOptionPane.showMessageDialog(null, "Export to shapefile failed");
			} finally {
				transaction.close();
			}
		}
	}
	
	private void test(File file) throws IOException {
		Query query = new Query("typeName");
        query.setCoordinateSystemReproject(map.getCoordinateReferenceSystem());

        SimpleFeatureCollection featureCollection = featureSource.getFeatures(query);

        // And create a new Shapefile with the results
        DataStoreFactorySpi factory = new ShapefileDataStoreFactory();

        Map<String, Serializable> create = new HashMap<>();
        create.put("url", file.toURI().toURL());
        create.put("create spatial index", Boolean.TRUE);
        DataStore newDataStore = factory.createNewDataStore(create);

        newDataStore.createSchema(featureCollection.getSchema());
        Transaction transaction = new DefaultTransaction("Reproject");
        SimpleFeatureStore featureStore =
                (SimpleFeatureStore) newDataStore.getFeatureSource("typeName");
        featureStore.setTransaction(transaction);
        try {
            featureStore.addFeatures(featureCollection);
            transaction.commit();
            JOptionPane.showMessageDialog(
                    null,
                    "Export to shapefile complete",
                    "Export",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception problem) {
            transaction.rollback();
            problem.printStackTrace();
            JOptionPane.showMessageDialog(
                    null, "Export to shapefile failed", "Export", JOptionPane.ERROR_MESSAGE);
        } finally {
            transaction.close();
        }
	}
}
