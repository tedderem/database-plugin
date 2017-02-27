package org.archicontribs.database.canvas;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.DBMetadata;
import org.archicontribs.database.IDBMetadata;

/**
 * extends AccessRelationship<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.canvas.model.impl.CanvasModelSticky
 * @see org.archicontribs.database.IDBMetadata
 */
public class CanvasModelSticky extends com.archimatetool.canvas.model.impl.CanvasModelSticky implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(CanvasModelSticky.class);
	private DBMetadata dbMetadata;
	
	public CanvasModelSticky() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new CanvasModelSticky");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}
