package org.archicontribs.database.model.impl;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;

/**
 * extends ApplicationComponent<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.ApplicationComponent
 * @see org.archicontribs.database.model.IDBMetadata
 */
public class ApplicationComponent extends com.archimatetool.model.impl.ApplicationComponent implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(ApplicationComponent.class);
	private DBMetadata dbMetadata;
	
	public ApplicationComponent() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new ApplicationComponent");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
