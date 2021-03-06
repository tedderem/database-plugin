package org.archicontribs.database.model.impl;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;

/**
 * extends TechnologyInterface<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.TechnologyInterface
 * @see org.archicontribs.database.model.IDBMetadata
 */
public class TechnologyInterface extends com.archimatetool.model.impl.TechnologyInterface implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(TechnologyInterface.class);
	private DBMetadata dbMetadata;;
	
	public TechnologyInterface() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new TechnologyInterface");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
