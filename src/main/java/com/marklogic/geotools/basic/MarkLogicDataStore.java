package com.marklogic.geotools.basic;


import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;

import com.marklogic.client.query.*;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.feature.type.FeatureTypeFactoryImpl;
import org.geotools.filter.FilterCapabilities;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.type.Name;
import org.opengis.filter.sort.SortBy;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.StringHandle;


public class MarkLogicDataStore extends ContentDataStore {

	DatabaseClient client;
	GeoQueryServiceManager geoQueryServices;
	UserConnectionDetails userConnectionDetails;

	private QueryCapabilities queryCapabilities;
	private FilterCapabilities filterCapabilities;

	/**
	 *
	 * @param host
	 * @param port
	 * @param securityContext
	 * @param database
	 * @param namespace
	 */
	public MarkLogicDataStore(String host, int port, DatabaseClientFactory.SecurityContext securityContext, String database, String namespace) {
		setupClient(host,port,securityContext,database);
		setNamespaceURI(namespace);
	}

	/**
	 * Creates a MarkLogic Data Store object using a MAP.  Should be passed from the MarkLogic Data Store Fatory class.
	 * @param map Map<String, Serializable> with necessary details to create the data store.
	 * @throws IOException
	 */
	public MarkLogicDataStore(Map<String, Serializable> map) throws IOException{
		// extract properties
		String host = (String) MarkLogicDataStoreFactory.ML_HOST_PARAM.lookUp(map);
		Integer port = (Integer) MarkLogicDataStoreFactory.ML_PORT_PARAM.lookUp(map);
		String username = (String) MarkLogicDataStoreFactory.ML_USERNAME_PARAM.lookUp(map);
		String password = (String) MarkLogicDataStoreFactory.ML_PASSWORD_PARAM.lookUp(map);
		String database = (String) MarkLogicDataStoreFactory.ML_DATABASE_PARAM.lookUp(map);
		String namespace = (String) MarkLogicDataStoreFactory.NAMESPACE_PARAM.lookUp(map);

		String userAuthType = (String) MarkLogicDataStoreFactory.USER_AUTH_TYPE.lookUp(map);
		String userHost = (String) MarkLogicDataStoreFactory.ML_USER_HOST_PARAM.lookUp(map);
		Integer userPort = (Integer) MarkLogicDataStoreFactory.ML_USER_PORT_PARAM.lookUp(map);

		this.userConnectionDetails = new UserConnectionDetails(userHost, userPort, userAuthType);

		this.queryCapabilities = buildQueryCapabilities();
		this.filterCapabilities = buildFilterCapabilities();
		
		setFilterFactory(CommonFactoryFinder.getFilterFactory(null));
        setGeometryFactory(new GeometryFactory());
        setFeatureTypeFactory(new FeatureTypeFactoryImpl());
        setFeatureFactory(CommonFactoryFinder.getFeatureFactory(null));
		
		setupClient(host,port,new DatabaseClientFactory.DigestAuthContext(username,password), database);
		setNamespaceURI(namespace);
		setupGeoQueryServices();
	}

	class UserConnectionDetails {
		public String host;
		public Integer port;
		public String authType;
		public UserConnectionDetails(String host, Integer port, String authType) {
			this.host = host;
			this.port = port;
			this.authType = authType;
		}
	}

	public QueryCapabilities buildQueryCapabilities() {
		return new QueryCapabilities() {
			public boolean isJoiningSupported() {return true;}
			public boolean isOffsetSupported() {return true;}
			public boolean isReliableFIDSupported() {return true;}
			public boolean isUseProvidedFIDSupported() {return false;}
			public boolean isVersionSupported() {return false;}
			public boolean supportsSorting(SortBy[] sortAttributes) {return true;}
		};
	}
	
	public FilterCapabilities buildFilterCapabilities() {
		FilterCapabilities capabilities = new FilterCapabilities();
		capabilities.addAll(FilterCapabilities.LOGICAL_OPENGIS);
		capabilities.addAll(FilterCapabilities.SIMPLE_COMPARISONS_OPENGIS);
		capabilities.addType(FilterCapabilities.FID);
		capabilities.addType(FilterCapabilities.LIKE);
		capabilities.addType(FilterCapabilities.NONE);
		capabilities.addType(FilterCapabilities.NULL_CHECK);
		capabilities.addType(FilterCapabilities.SIMPLE_ARITHMETIC);
		capabilities.addType(FilterCapabilities.SPATIAL_BBOX);
		capabilities.addType(FilterCapabilities.SPATIAL_CONTAINS);
		capabilities.addType(FilterCapabilities.SPATIAL_DISJOINT);
		capabilities.addType(FilterCapabilities.SPATIAL_INTERSECT);
		capabilities.addType(FilterCapabilities.SPATIAL_OVERLAPS);
		capabilities.addType(FilterCapabilities.SPATIAL_WITHIN);
		return capabilities;
	}
	
	public QueryCapabilities getQueryCapabilities() {
		if (queryCapabilities == null) {
			queryCapabilities = buildQueryCapabilities();
		}
		return queryCapabilities;
	}

	public UserConnectionDetails getUserConnectionDetails() {
		return this.userConnectionDetails;
	}
	
	public FilterCapabilities getFilterCapabilities() {
		if (filterCapabilities == null) {
			filterCapabilities = buildFilterCapabilities();
		}
		return filterCapabilities;
	}
	
	private void setupClient(String host, int port, DatabaseClientFactory.SecurityContext securityContext, String database) {
		if (Objects.nonNull(database)) {
			client = DatabaseClientFactory.newClient(host, port, database, securityContext);
		} else {
			client = DatabaseClientFactory.newClient(host, port, securityContext);
		}
	}
	
	private void setupGeoQueryServices() {
		geoQueryServices = new GeoQueryServiceManager(client);
	}
	
	DatabaseClient getClient() {
		return client;
	}
	
	GeoQueryServiceManager getGeoQueryServiceManager() {
		return geoQueryServices;
	}

	/**
	 * Update this to return a better description of our data, this is just a placeholder for now
	 * @return
	 * @throws IOException
	 */
	protected List<Name> createTypeNames() throws IOException {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine("**************************************************************************");
			LOGGER.fine("createTypeNames called!");
			LOGGER.fine("Datastore namespace: " + getNamespaceURI());
			LOGGER.fine("**************************************************************************");
		}
		GeoQueryServiceManager geoQueryServices = new GeoQueryServiceManager(client);
		
		try {
			List<Name> nameList;
			nameList = geoQueryServices.getLayerNames();

			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("**************************************************************************");
				LOGGER.log(Level.FINE, () -> "Names returned: " + Arrays.toString(nameList.toArray()));
				LOGGER.fine("**************************************************************************");
			}
			return nameList;
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new ArrayList<Name>();
		}
    }
	
	@Override
	public List<Name> getNames() throws IOException {
	    String[] typeNames = getTypeNames();
	    List<Name> names = new ArrayList<Name>(typeNames.length);
	    for (String typeName : typeNames) {
	        names.add(new NameImpl(namespaceURI, typeName));
	    }
	    return names;
	}
	
	@Override
  protected ContentFeatureSource createFeatureSource(ContentEntry entry) throws IOException {
    LOGGER.info("MarkLogicDataStore.createFeatureSource: entry.getName() = " + entry.getName().getNamespaceURI() + ":" + entry.getName().getLocalPart());
		return new MarkLogicBasicFeatureSource(entry, Query.ALL);
  }

}
