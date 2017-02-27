package org.archicontribs.database.model;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.DBMetadata;
import org.archicontribs.database.IDBMetadata;

/**
 * extends SystemSoftware<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.SystemSoftware
 * @see org.archicontribs.database.IDBMetadata
 */
public class SystemSoftware extends com.archimatetool.model.impl.SystemSoftware implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(SystemSoftware.class);
	private DBMetadata dbMetadata;
	
	public SystemSoftware() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new SystemSoftware");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
