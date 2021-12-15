package org.geotools.tutorial;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureStore;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.feature.type.GeometryDescriptorImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.identity.FeatureId;
 


public class GeojsonToShp {
	public static void main(String[] args) throws IOException {
		GeojsonToShp g2s = new GeojsonToShp();
		//g2s.toShp(new File("c:\\example\\input\\250.json"));
		g2s.toShp(new File("c:\\example\\input\\11140.geojson"), new File("c:\\example\\output\\test2.shp"));
	}

	public void toShp(File geojson, File output) throws IOException {
		File shpFile = output;
		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<String, Serializable>();
		params.put("url", shpFile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore shpDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

		InputStream in = new FileInputStream(geojson);
		int decimals = 15;
		GeometryJSON gjson = new GeometryJSON(decimals);
		FeatureJSON fjson = new FeatureJSON(gjson);

		FeatureCollection fc = fjson.readFeatureCollection(in);

		fc.getSchema();
		
		WriteShapefile writer = new WriteShapefile(shpFile);
        writer.writeFeatures(fc);
	}
	
	
	
	public class WriteShapefile {
		File outfile;
		private ShapefileDataStore shpDataStore;

		public WriteShapefile(File f) {
			outfile = f;

			ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

			Map<String, Serializable> params = new HashMap<String, Serializable>();
			try {
				params.put("url", outfile.toURI().toURL());
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			params.put("create spatial index", Boolean.TRUE);

			try {
				shpDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public boolean writeFeatures(FeatureCollection<SimpleFeatureType, SimpleFeature> features) {

			if (shpDataStore == null) {
				throw new IllegalStateException("Datastore can not be null when writing");
			}
			SimpleFeatureType schema = features.getSchema();
			GeometryDescriptor geom = schema.getGeometryDescriptor();
			String oldGeomAttrib = "";
			try {

				/*
				 * Write the features to the shapefile
				 */
				Transaction transaction = new DefaultTransaction("create");

				String typeName = shpDataStore.getTypeNames()[0];
				SimpleFeatureSource featureSource = shpDataStore.getFeatureSource(typeName);

				/*
				 * The Shapefile format has a couple limitations: - "the_geom" is always
				 * first, and used for the geometry attribute name - "the_geom" must be of
				 * type Point, MultiPoint, MuiltiLineString, MultiPolygon - Attribute
				 * names are limited in length - Not all data types are supported (example
				 * Timestamp represented as Date)
				 *
				 * Because of this we have to rename the geometry element and then rebuild
				 * the features to make sure that it is the first attribute.
				 */

				List<AttributeDescriptor> attributes = schema.getAttributeDescriptors();
				GeometryType geomType = null;
				List<AttributeDescriptor> attribs = new ArrayList<AttributeDescriptor>();
				for (AttributeDescriptor attrib : attributes) {
					AttributeType type = attrib.getType();
					if (type instanceof GeometryType) {
						geomType = (GeometryType) type;
						oldGeomAttrib = attrib.getLocalName();
					} else {
						attribs.add(attrib);
					}
				}

				GeometryTypeImpl gt = new GeometryTypeImpl(new NameImpl("the_geom"), geomType.getBinding(),
				    geomType.getCoordinateReferenceSystem(), geomType.isIdentified(), geomType.isAbstract(),
				    geomType.getRestrictions(), geomType.getSuper(), geomType.getDescription());

				GeometryDescriptor geomDesc = new GeometryDescriptorImpl(gt, new NameImpl("the_geom"), geom.getMinOccurs(),
				    geom.getMaxOccurs(), geom.isNillable(), geom.getDefaultValue());

				attribs.add(0, geomDesc);

				SimpleFeatureType shpType = new SimpleFeatureTypeImpl(schema.getName(), attribs, geomDesc, schema.isAbstract(),
				    schema.getRestrictions(), schema.getSuper(), schema.getDescription());

				shpDataStore.createSchema(shpType);

				if (featureSource instanceof SimpleFeatureStore) {
					SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

					List<SimpleFeature> feats = new ArrayList<SimpleFeature>();

					FeatureIterator<SimpleFeature> features2 = features.features();
					while (features2.hasNext()) {
						SimpleFeature f = features2.next();
						SimpleFeature reType =DataUtilities.reType(shpType, f, true);
						//set the default Geom (the_geom) from the original Geom
					    reType.setAttribute("the_geom", f.getAttribute(oldGeomAttrib));

						feats.add(reType);
					}
					features2.close();
					SimpleFeatureCollection collection = new ListFeatureCollection(shpType, feats);

					featureStore.setTransaction(transaction);
					try {
						List<FeatureId> ids = featureStore.addFeatures(collection);
						transaction.commit();
					} catch (Exception problem) {
						problem.printStackTrace();
						transaction.rollback();
					} finally {
						transaction.close();
					}
					shpDataStore.dispose();
					return true;
				} else {
					
					transaction.close();
					shpDataStore.dispose();
					System.err.println("ShapefileStore not writable");
					
					return false;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		
	}

}
