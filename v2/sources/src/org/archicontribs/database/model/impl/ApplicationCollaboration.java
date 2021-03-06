package org.archicontribs.database.model.impl;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;

/**
 * extends ApplicationCollaboration<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.ApplicationCollaboration
 * @see org.archicontribs.database.model.IDBMetadata
 */
public class ApplicationCollaboration extends com.archimatetool.model.impl.ApplicationCollaboration implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(ApplicationCollaboration.class);
	private DBMetadata dbMetadata;
	
	public ApplicationCollaboration() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new ApplicationCollaboration");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
