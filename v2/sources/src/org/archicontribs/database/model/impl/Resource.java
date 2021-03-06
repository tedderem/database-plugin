package org.archicontribs.database.model.impl;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;

/**
 * extends Resource<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.Resource
 * @see org.archicontribs.database.model.IDBMetadata
 */
public class Resource extends com.archimatetool.model.impl.Resource implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(Resource.class);
	private DBMetadata dbMetadata;
	
	public Resource() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new Resource");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
