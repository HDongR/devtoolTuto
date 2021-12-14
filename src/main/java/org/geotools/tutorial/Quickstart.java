package org.geotools.tutorial;

import java.io.File;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class Quickstart {
	/**
	 * GeoTools Quickstart demo application. Prompts the user for a shapefile and
	 * displays its contents on the screen in a map frame
	 */
	public static void main(String[] args) throws Exception {
		File file = new File("c:\\example\\locations1.csv");
		if (file == null) {
			return;
		}

		final SimpleFeatureType TYPE = DataUtilities.createType("Location", "the_geom:Point:srid=4326," + // <- the geometry attribute: Point type
				"name:String," + // <- a String attribute
				"number:Integer" // a number attribute
		);
		System.out.println("TYPE:" + TYPE);

		/*
		 * A list to collect features as we create them.
		 */
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();

		/*
		 * GeometryFactory will be used to create the geometry attribute of each
		 * feature, using a Point object for the location.
		 */
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

		BufferedReader reader = new BufferedReader(new FileReader(file));
		try {
			/* First line of the data file is the header */
			String line = reader.readLine();
			System.out.println("Header: " + line);

			for (line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.trim().length() > 0) { // skip blank lines
					String tokens[] = line.split("\\,");

					double latitude = Double.parseDouble(tokens[0]);
					double longitude = Double.parseDouble(tokens[1]);
					String name = tokens[2].trim();
					int number = Integer.parseInt(tokens[3].trim());

					/* Longitude (= x coord) first ! */
					Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

					featureBuilder.add(point);
					featureBuilder.add(name);
					featureBuilder.add(number);
					SimpleFeature feature = featureBuilder.buildFeature(null);
					features.add(feature);
				}
			}
		} finally {
			reader.close();
		}

		/*
		 * Get an output file name and create the new shapefile
		 */
		File newFile = getNewShapeFile(file);

		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<String, Serializable>();
		params.put("url", newFile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
		
		/*
		 * TYPE is used as a template to describe the file contents
		 */
		newDataStore.createSchema(TYPE);

		/*
		 * Write the features to the shapefile
		 */
		Transaction transaction = new DefaultTransaction("create");

		String typeName = newDataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
		SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
		/*
		 * The Shapefile format has a couple limitations: - "the_geom" is always first,
		 * and used for the geometry attribute name - "the_geom" must be of type Point,
		 * MultiPoint, MuiltiLineString, MultiPolygon - Attribute names are limited in
		 * length - Not all data types are supported (example Timestamp represented as
		 * Date)
		 * 
		 * Each data store has different limitations so check the resulting
		 * SimpleFeatureType.
		 */
		System.out.println("SHAPE:" + SHAPE_TYPE);

		if (featureSource instanceof SimpleFeatureStore) {
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource; 
			/*
			 * SimpleFeatureStore has a method to add features from a
			 * SimpleFeatureCollection object, so we use the ListFeatureCollection class to
			 * wrap our list of features.
			 */
			SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
			featureStore.setTransaction(transaction);
			try {
				featureStore.addFeatures(collection);
				transaction.commit();
			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();
			} finally {
				transaction.close();
			}
			System.exit(0); // success!
		} else {
			System.out.println(typeName + " does not support read/write access");
			System.exit(1);
		}
		
		
		 

		WKTReader reader2 = new WKTReader( geometryFactory );
		Point point1 = (Point) reader2.read("POINT (1 1)");
		
		Coordinate coord = new Coordinate( 1, 1 );
		Point point2 = geometryFactory.createPoint( coord );
	}

	/**
	 * Prompt the user for the name and path to use for the output shapefile
	 * 
	 * @param csvFile
	 *            the input csv file used to create a default shapefile name
	 * 
	 * @return name and path for the shapefile as a new File object
	 */
	private static File getNewShapeFile(File csvFile) {
		String path = csvFile.getAbsolutePath();
		String newPath = path.substring(0, path.length() - 4) + ".shp";
		File newFile = new File(newPath);

		return newFile;
	}
	
	/**
     * Here is how you can use a SimpleFeatureType builder to create the schema for your shapefile
     * dynamically.
     * <p>
     * This method is an improvement on the code used in the main method above (where we used
     * DataUtilities.createFeatureType) because we can set a Coordinate Reference System for the
     * FeatureType and a a maximum field length for the 'name' field dddd
     */
    private static SimpleFeatureType createFeatureType() {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Location");
        builder.setCRS(DefaultGeographicCRS.WGS84); // <- Coordinate reference system

        // add attributes in order
        builder.add("the_geom", Point.class);
        builder.length(15).add("Name", String.class); // <- 15 chars width for name field
        builder.add("number",Integer.class);
        
        // build the type
        final SimpleFeatureType LOCATION = builder.buildFeatureType();
        return LOCATION;
    }

}
