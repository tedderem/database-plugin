package org.archicontribs.database.model.impl;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;

/**
 * extends ApplicationEvent<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.model.impl.ApplicationEvent
 * @see org.archicontribs.database.model.IDBMetadata
 */
public class ApplicationEvent extends com.archimatetool.model.impl.ApplicationEvent implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(ApplicationEvent.class);
	private DBMetadata dbMetadata;
	
	public ApplicationEvent() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new ApplicationEvent");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
