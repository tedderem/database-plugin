package org.archicontribs.database.model.impl;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;

/**
 * extends BusinessFunction<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.BusinessFunction
 * @see org.archicontribs.database.model.IDBMetadata
 */
public class BusinessFunction extends com.archimatetool.model.impl.BusinessFunction implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(BusinessFunction.class);
	private DBMetadata dbMetadata;
	
	public BusinessFunction() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new BusinessFunction");
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
