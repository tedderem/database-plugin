package org.archicontribs.database;

import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;

import org.apache.log4j.Level;
import org.archicontribs.database.GUI.DBGui;
import org.archicontribs.database.model.ArchimateModel;
import org.archicontribs.database.model.DBArchimateFactory;
import org.archicontribs.database.model.DBCanvasFactory;
import org.archicontribs.database.model.IDBMetadata;
import org.archicontribs.database.model.DBMetadata.DATABASE_STATUS;
import org.archicontribs.database.model.impl.Folder;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import com.archimatetool.canvas.model.ICanvasModelSticky;
import com.archimatetool.canvas.model.IHintProvider;
import com.archimatetool.canvas.model.IIconic;
import com.archimatetool.editor.diagram.ArchimateDiagramModelFactory;
import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IBorderObject;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelImageProvider;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IDocumentable;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IFontAttribute;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.IJunction;
import com.archimatetool.model.ILineObject;
import com.archimatetool.model.ILockable;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.ISketchModel;
import com.archimatetool.model.ITextAlignment;
import com.archimatetool.model.ITextContent;
import com.archimatetool.model.ITextPosition;

/**
 * This class holds the information required to connect to, to import from and export to a database
 * 
 * @author Herve Jouin
 */
public class DBDatabaseConnection {
	private static final DBLogger logger = new DBLogger(DBDatabaseConnection.class);

	/**
	 * Version of the expected database model.<br>
	 * If the value found into the columns version of the table "database_version", then the plugin will try to upgrade the datamodel.
	 */
	public static final int databaseVersion = 203;

	/**
	 * This class variable stores the last commit transaction
	 * It will be used in every insert and update calls<br>
	 * This way, all requests in a transaction will have the same timestamp.
	 */
	private Timestamp lastTransactionTimestamp = null;

	/**
	 * the databaseEntry corresponding to the connection
	 */
	protected DBDatabaseEntry databaseEntry = null;
	protected String schema = "";



	/**
	 * Connection to the database
	 */
	protected Connection connection = null;

	// This variables allows to store the columns type. They will be calculated for all the database brands. 
	private String OBJECTID = "varchar(50)";
	private String OBJ_NAME =  "varchar(1024)";
	private String USERNAME = "varchar(30)";
	private String STRENGTH = "varchar(20)";
	private String COLOR = "varchar(7)";
	private String TYPE = "varchar(3)";
	private String TEXT = "text";
	private String FONT = "varchar(150)";
	private String INTEGER = "integer(10)";
	private String AUTO_INCREMENT = INTEGER+" NOT NULL AUTOINCREMENT";
	private String PRIMARY_KEY = "PRIMARY KEY";
	private String BOOLEAN = "tinyint(1)";          // do not use boolean SQL type as it is not supported by all databases, so we export and import integer instead (0 = false, 1 = true);
	private String DATETIME = "datetime";
	private String IMAGE = "blob";











	/**
	 * Opens a connection to a JDBC database using all the connection details
	 */
	public DBDatabaseConnection(DBDatabaseEntry databaseEntry) throws ClassNotFoundException, SQLException {

		assert(databaseEntry != null);

		this.databaseEntry = databaseEntry;

		schema = databaseEntry.getSchemaPrefix();

		openConnection();
	}


	private void openConnection() throws ClassNotFoundException, SQLException {
		if ( logger.isDebugEnabled() ) logger.debug("Opening connection to database "+databaseEntry.getName()+" : driver="+databaseEntry.getDriver()+", server="+databaseEntry.getServer()+", port="+databaseEntry.getPort()+", database="+databaseEntry.getDatabase()+", schema="+databaseEntry.getSchema()+", username="+databaseEntry.getUsername());

		String clazz = null;
		String connectionString = null;

		switch (databaseEntry.getDriver()) {
			case "postgresql" :
				clazz = "org.postgresql.Driver";
				connectionString = "jdbc:postgresql://" + databaseEntry.getServer() + ":" + databaseEntry.getPort() + "/" + databaseEntry.getDatabase();
				break;
			case "ms-sql"      :
				clazz = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
				connectionString = "jdbc:sqlserver://" + databaseEntry.getServer() + ":" + databaseEntry.getPort() + ";databaseName=" + databaseEntry.getDatabase();
				if ( DBPlugin.isEmpty(databaseEntry.getUsername()) && DBPlugin.isEmpty(databaseEntry.getPassword()) )
					connectionString += ";integratedSecurity=true";
				break;
			case "mysql"      :
				clazz = "com.mysql.jdbc.Driver";
				connectionString = "jdbc:mysql://" + databaseEntry.getServer() + ":" + databaseEntry.getPort() + "/" + databaseEntry.getDatabase();
				break;
			case "neo4j"      :
				clazz = "org.neo4j.jdbc.Driver";
				connectionString = "jdbc:neo4j:bolt://" + databaseEntry.getServer() + ":" + databaseEntry.getPort();
				break;
			case "oracle"     :
				clazz = "oracle.jdbc.driver.OracleDriver";
				connectionString = "jdbc:oracle:thin:@" + databaseEntry.getServer() + ":" + databaseEntry.getPort() + ":" + databaseEntry.getDatabase();
				break;
			case "sqlite"     :
				clazz = "org.sqlite.JDBC";
				connectionString = "jdbc:sqlite:"+databaseEntry.getServer();
				break;
			default :
				throw new SQLException("Unknonwn driver " + databaseEntry.getDriver());        // just in case
		}

		if ( logger.isDebugEnabled() ) logger.debug("JDBC class = " + clazz);
		Class.forName(clazz);

		if ( logger.isDebugEnabled() ) logger.debug("JDBC connection string = " + connectionString);
		try {
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "ms-sql") && DBPlugin.isEmpty(databaseEntry.getUsername()) && DBPlugin.isEmpty(databaseEntry.getPassword()) ) {
				if ( logger.isDebugEnabled() ) logger.debug("Connecting with Windows integrated security");
				connection = DriverManager.getConnection(connectionString);
			} else {
				if ( logger.isDebugEnabled() ) logger.debug("Connecting with username = "+databaseEntry.getUsername());
				connection = DriverManager.getConnection(connectionString, databaseEntry.getUsername(), databaseEntry.getPassword());
			}
		} catch (SQLException e) {
			// if the JDBC driver fails to connect to the database using the specified driver, the it tries with all the other drivers
			// and the exception is raised by the latest driver (log4j in our case)
			// so we need to trap this exception and change the error message
			// For JDBC people, this is not a bug but a functionality :( 
			if ( DBPlugin.areEqual(e.getMessage(), "JDBC URL is not correct.\nA valid URL format is: 'jdbc:neo4j:http://<host>:<port>'") )
				if ( databaseEntry.getDriver().equals("ms-sql") && DBPlugin.isEmpty(databaseEntry.getUsername()) && DBPlugin.isEmpty(databaseEntry.getPassword()) )	// integrated authentication
					throw new SQLException("Please verify the database configuration in the preferences.\n\nPlease also check that you installed the \"sqljdbc_auth.dll\" file in the JRE bin folder to enable the SQL Server integrated security mode.");
				else
					throw new SQLException("Please verify the database configuration in the preferences.");
			else
				throw e;
		}

		if ( logger.isDebugEnabled() ) {
			if ( DBPlugin.isEmpty(schema) ) {
				logger.debug("Will use default schema ");
			}else {
				logger.debug("Will use schema "+schema);
			}
		}
	}

	/**
	 * Closes connection to the database
	 */
	public void close() throws SQLException {
		reset();

		if ( connection == null || connection.isClosed() ) {
			if ( logger.isDebugEnabled() ) logger.debug("The database connection is already closed.");
		} else {
			if ( logger.isDebugEnabled() ) logger.debug("Closing database connection.");
			connection.close();
		}
		connection = null;
		databaseEntry = null;
	}

	public boolean isConnected() {
		return connection != null;
	}

	public void reset() throws SQLException {
		for ( PreparedStatement pstmt: preparedStatementMap.values() ) {
			pstmt.close();
			pstmt=null;
		}
		preparedStatementMap = new HashMap<String, PreparedStatement>();

		if ( currentResultSet != null ) {
			currentResultSet.close();
			currentResultSet = null;
		}

		// We reset all "ranks" to zero
		elementRank = 0;
		relationshipRank = 0;
		folderRank = 0;
		viewRank = 0;
		viewObjectRank = 0;
		viewConnectionRank = 0;

		// we free up a bit the memory
		allImagePaths = null;
	}




	/**
	 * Checks the database structure<br>
	 * @throws SQLExcetion if the connection to the database failed
	 * @returns true if the database structure is correct, false if not
	 */
	public void checkDatabase() throws ClassNotFoundException, SQLException {
		boolean wasConnected = isConnected();
		ResultSet result = null;

		DBGui.popup("Please wait while checking the "+databaseEntry.getDriver()+" database ...");

		try {
			if ( !wasConnected )
				openConnection();

			switch ( databaseEntry.getDriver() ) {
				case "neo4j" :
					DBGui.closePopup();		// no tables to check on neo4j databases
					return;
				case "sqlite" :
					AUTO_INCREMENT = "INTEGER PRIMARY KEY";
					DATETIME = "timestamp";
					TEXT = "clob";
					break;
				case "mysql"  :
					INTEGER = "int(10)";
					AUTO_INCREMENT = INTEGER+"NOT NULL AUTO_INCREMENT";
					TEXT = "mediumtext";
					IMAGE = "longblob";
					break;
				case "ms-sql"  :
					INTEGER = "int";
					AUTO_INCREMENT = INTEGER+" IDENTITY NOT NULL" ;
					IMAGE = "image";
					BOOLEAN = "tinyint";
					break;
				case "oracle" :
					INTEGER = "integer";
					AUTO_INCREMENT = INTEGER+" NOT NULL";
					BOOLEAN = "char";
					DATETIME = "date";
					TEXT = "clob";
					break;
				case "postgresql" :
					INTEGER = "integer";
					DATETIME = "timestamp";
					IMAGE = "bytea";
					BOOLEAN = "smallint";
					AUTO_INCREMENT = "SERIAL NOT NULL" ;
					break;
			}

			// checking if the database_version table exists
			if ( logger.isTraceEnabled() ) logger.trace("Checking \""+schema+"database_version\" table");

			try {
				result = select("SELECT version FROM "+schema+"database_version WHERE archi_plugin = ?", DBPlugin.pluginName);
			} catch (SQLException err) {
				DBGui.closePopup();
				// if the table does not exist
				if ( !DBGui.question("We successfully connected to the database but the necessary tables have not be found.\n\nDo you wish to create them ?") ) {
					throw new SQLException("Necessary tables not found.");
				}

				createTables();
				return;
			}

			if ( result.next() ) {
				switch ( result.getInt("version")) {
					case databaseVersion : break;		// good, nothing to do
					case 200 : 
				}
				if ( databaseVersion != result.getInt("version") ) {
					if ( (result.getInt("version")<200) || (result.getInt("version")>databaseVersion) )
						throw new SQLException("The database has got an unknown model version (is "+result.getInt("version")+" but should be between 200 and "+databaseVersion+")");

					if ( DBGui.question("The database needs to be upgraded. You will not loose any data during this operation.\n\nDo you wish to upgrade your database ?") ) {
						upgradeDatabase(result.getInt("version"));
					} else
						throw new SQLException("The database needs to be upgraded.");
				}
			} else {
				result.close();
				result=null;
				throw new SQLException(DBPlugin.pluginName+" not found in "+schema+"database_version table");
				//TODO : call create tables and update createTables method to ignore error on database_version table (in case it already exists and is empty)
			}
			result.close();
			result=null;

			DBGui.closePopup();

		} finally {
			if ( !wasConnected && connection!=null) {
				connection.close();
				connection = null;
			}

			DBGui.closePopup();
		}
	}

	/**
	 * Creates the necessary tables in the database
	 * @throws ClassNotFoundException 
	 */
	private void createTables() throws SQLException, ClassNotFoundException {
		final String[] databaseVersionColumns = {"archi_plugin", "version"};

		DBGui.popup("Please wait while creating necessary database tables ...");

		boolean wasConnected = isConnected();

		if ( !wasConnected )
			openConnection();

		setAutoCommit(false);

		try {
			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"database_version");
			request("CREATE TABLE "+schema+"database_version ("
					+ "archi_plugin "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL"
					+ ")");

			insert(schema+"database_version", databaseVersionColumns, DBPlugin.pluginName, databaseVersion);

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"bendpoints");
			request("CREATE TABLE "+schema+"bendpoints ("
					+ "parent_id "+OBJECTID +" NOT NULL, "
					+ "parent_version "+ INTEGER +" NOT NULL, "
					+ "rank "+INTEGER +" NOT NULL, "
					+ "start_x "+INTEGER +" NOT NULL, "
					+ "start_y "+INTEGER +" NOT NULL, "
					+ "end_x "+INTEGER +" NOT NULL, "
					+ "end_y "+INTEGER +" NOT NULL, "
					+ PRIMARY_KEY+" (parent_id, parent_version, rank)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"elements");
			request("CREATE TABLE "+schema+"elements ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "class "+OBJECTID +" NOT NULL, "
					+ "name "+OBJ_NAME +", "
					+ "documentation "+ TEXT +", "
					+ "type "+TYPE +", "
					+ "created_by "+USERNAME +" NOT NULL, "
					+ "created_on "+DATETIME +" NOT NULL, "
					+ "checkedin_by "+USERNAME +", "
					+ "checkedin_on "+DATETIME +", "
					+ "deleted_by "+USERNAME +", "
					+ "deleted_on "+DATETIME +", "
					+ "checksum "+OBJECTID +" NOT NULL,"
					+ PRIMARY_KEY+" (id, version)"
					+ ")");					

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"elements_in_model");
			request("CREATE TABLE "+schema+"elements_in_model ("
					+ "eim_id "+AUTO_INCREMENT +", "
					+ "element_id "+OBJECTID +" NOT NULL, "
					+ "element_version "+INTEGER +" NOT NULL, "
					+ "parent_folder_id "+ OBJECTID +" NOT NULL, "
					+ "model_id "+OBJECTID +" NOT NULL, "
					+ "model_version "+INTEGER +" NOT NULL, "
					+ "rank "+INTEGER +" NOT NULL"
					+ (AUTO_INCREMENT.endsWith("PRIMARY KEY") ? "" : (", "+PRIMARY_KEY+" (eim_id)") )
					+ ")");
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ) {
				if ( logger.isDebugEnabled() ) logger.debug("creating sequence "+schema+"seq_elements");
				request("BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE "+schema+"seq_elements_in_model'; EXCEPTION WHEN OTHERS THEN NULL; END;");
				request("CREATE SEQUENCE "+schema+"seq_elements_in_model START WITH 1 INCREMENT BY 1 CACHE 100");

				if ( logger.isDebugEnabled() ) logger.debug("creating trigger "+schema+"trigger_elements_in_model");
				request("CREATE OR REPLACE TRIGGER "+schema+"trigger_elements_in_model "
						+ "BEFORE INSERT ON "+schema+"elements_in_model "
						+ "FOR EACH ROW "
						+ "BEGIN"
						+ "  SELECT "+schema+"seq_elements_in_model.NEXTVAL INTO :NEW.eim_id FROM DUAL;"
						+ "END;");
			}

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"folders");
			request("CREATE TABLE "+schema+"folders ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "type "+INTEGER +" NOT NULL, "
					+ "root_type "+INTEGER +" NOT NULL, "
					+ "name "+OBJ_NAME +", "
					+ "documentation "+ TEXT +", "
					+ "created_by "+USERNAME +", "
					+ "created_on "+DATETIME +", "
					+ "checksum "+OBJECTID +" NOT NULL, "
					+ PRIMARY_KEY+" (id, version)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"folders_in_model");
			request("CREATE TABLE "+schema+"folders_in_model ("
					+ "fim_id "+AUTO_INCREMENT+", "
					+ "folder_id "+OBJECTID +" NOT NULL, "
					+ "folder_version "+INTEGER +" NOT NULL, "
					+ "parent_folder_id "+ OBJECTID +", "
					+ "model_id "+OBJECTID +" NOT NULL, "
					+ "model_version "+INTEGER +" NOT NULL, "
					+ "rank "+INTEGER +" NOT NULL"
					+ (AUTO_INCREMENT.endsWith("PRIMARY KEY") ? "" : (", "+PRIMARY_KEY+" (fim_id)") )
					+ ")");
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ) {
				if ( logger.isDebugEnabled() ) logger.debug("creating sequence "+schema+"seq_folders_in_model");
				request("BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE "+schema+"seq_folders_in_model'; EXCEPTION WHEN OTHERS THEN NULL; END;");
				request("CREATE SEQUENCE "+schema+"seq_folders_in_model START WITH 1 INCREMENT BY 1 CACHE 100");

				if ( logger.isDebugEnabled() ) logger.debug("creating trigger "+schema+"trigger_folders_in_model");
				request("CREATE OR REPLACE TRIGGER "+schema+"trigger_folders_in_model "
						+ "BEFORE INSERT ON "+schema+"folders_in_model "
						+ "FOR EACH ROW "
						+ "BEGIN"
						+ "  SELECT "+schema+"seq_folders_in_model.NEXTVAL INTO :NEW.fim_id FROM DUAL;"
						+ "END;");
			}

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"images");
			request("CREATE TABLE "+schema+"images ("
					+ "path "+OBJECTID +" NOT NULL, "
					+ "image "+IMAGE +" NOT NULL, "
					+ "checksum "+ OBJECTID +" NOT NULL, "
					+ PRIMARY_KEY+" (path)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"models");
			request("CREATE TABLE "+schema+"models ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "name "+OBJ_NAME +" NOT NULL, "
					+ "note "+TEXT +", "
					+ "purpose "+TEXT +", "
					+ "created_by "+USERNAME +" NOT NULL, "
					+ "created_on "+DATETIME +" NOT NULL, "
					+ "checkedin_by "+ USERNAME +", "
					+ "checkedin_on "+ DATETIME +", "
					+ "deleted_by "+USERNAME +", "
					+ "deleted_on "+DATETIME +", "
					+ PRIMARY_KEY+" (id, version)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"properties");
			request("CREATE TABLE "+schema+"properties ("
					+ "parent_id "+OBJECTID +" NOT NULL, "
					+ "parent_version "+ INTEGER +" NOT NULL, "
					+ "rank "+INTEGER +" NOT NULL, "
					+ "name "+OBJ_NAME +", "
					+ "value "+TEXT +", "
					+ PRIMARY_KEY+" (parent_id, parent_version, rank)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"relationships");
			request("CREATE TABLE "+schema+"relationships ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "class "+OBJECTID +" NOT NULL, "
					+ "name "+OBJ_NAME +", "
					+ "documentation "+ TEXT +", "
					+ "source_id "+OBJECTID +", "
					+ "target_id "+OBJECTID +", "
					+ "strength "+STRENGTH +", "
					+ "access_type "+INTEGER +", "
					+ "created_by "+USERNAME +" NOT NULL, "
					+ "created_on "+DATETIME +" NOT NULL, "
					+ "checkedin_by "+USERNAME +", "
					+ "checkedin_on "+DATETIME +", "
					+ "deleted_by "+USERNAME +", "
					+ "deleted_on "+DATETIME +", "
					+ "checksum "+OBJECTID +" NOT NULL, "
					+ PRIMARY_KEY+" (id, version)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"relationships_in_model");
			request("CREATE TABLE "+schema+"relationships_in_model ("
					+ "rim_id "+AUTO_INCREMENT+", "
					+ "relationship_id "+OBJECTID +" NOT NULL, "
					+ "relationship_version "+ INTEGER +" NOT NULL, "
					+ "parent_folder_id "+OBJECTID +" NOT NULL, "
					+ "model_id "+OBJECTID +" NOT NULL, "
					+ "model_version "+INTEGER +" NOT NULL, "
					+ "rank "+INTEGER +" NOT NULL "
					+ (AUTO_INCREMENT.endsWith("PRIMARY KEY") ? "" : (", "+PRIMARY_KEY+" (rim_id)") )
					+ ")");
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ) {
				if ( logger.isDebugEnabled() ) logger.debug("creating sequence "+schema+"seq_relationships");
				request("BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE "+schema+"seq_relationships_in_model'; EXCEPTION WHEN OTHERS THEN NULL; END;");
				request("CREATE SEQUENCE "+schema+"seq_relationships_in_model START WITH 1 INCREMENT BY 1 CACHE 100");

				if ( logger.isDebugEnabled() ) logger.debug("creating trigger "+schema+"trigger_relationships_in_model");
				request("CREATE OR REPLACE TRIGGER "+schema+"trigger_relationships_in_model "
						+ "BEFORE INSERT ON "+schema+"relationships_in_model "
						+ "FOR EACH ROW "
						+ "BEGIN"
						+ "  SELECT "+schema+"seq_relationships_in_model.NEXTVAL INTO :NEW.rim_id FROM DUAL;"
						+ "END;");
			}

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"views");
			request("CREATE TABLE "+schema+"views ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "class "+OBJECTID +" NOT NULL, "
					+ "name "+OBJ_NAME +", "
					+ "documentation "+TEXT +" , "
					+ "hint_content "+TEXT +", "
					+ "hint_title "+OBJ_NAME +", "
					+ "created_by "+USERNAME +" NOT NULL, "
					+ "created_on "+DATETIME +" NOT NULL, "
					+ "background "+INTEGER +", "
					+ "connection_router_type "+ INTEGER +" NOT NULL, "
					+ "viewpoint "+OBJECTID +", "
					+ "screenshot "+IMAGE +", "
					+ "checksum "+OBJECTID +" NOT NULL, "
					+ PRIMARY_KEY+" (id, version)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"views_connections");
			request("CREATE TABLE "+schema+"views_connections ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "container_id "+OBJECTID +" NOT NULL, "
					+ "view_id "+OBJECTID +" NOT NULL, "
					+ "view_version "+INTEGER +" NOT NULL, "
					+ "class "+OBJECTID +" NOT NULL, "
					+ "name "+OBJ_NAME +", "					// connection must store a name because all of them are not linked to a relationship
					+ "documentation "+TEXT +", "
					+ "is_locked "+BOOLEAN +", "
					+ "line_color "+COLOR +", "
					+ "line_width "+INTEGER +", "
					+ "font "+FONT +", "
					+ "font_color "+COLOR +", "
					+ "relationship_id "+OBJECTID +", "
					+ "relationship_version "+INTEGER +", "
					+ "source_connections "+ TEXT + ", "
					+ "target_connections "+ TEXT + ", "
					+ "source_object_id "+ OBJECTID +", "
					+ "target_object_id "+ OBJECTID +", "
					+ "text_position "+INTEGER +", "
					+ "type "+INTEGER +", "
					+ "rank "+INTEGER +" NOT NULL, "
					+ "checksum "+OBJECTID +" NOT NULL, "
					+ PRIMARY_KEY+" (id, version, container_id)"
					+ ")");

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"views_in_model");
			request("CREATE TABLE "+schema+"views_in_model ("
					+ "vim_id "+AUTO_INCREMENT+", "
					+ "view_id "+OBJECTID +" NOT NULL, "
					+ "view_version "+INTEGER +" NOT NULL, "
					+ "parent_folder_id "+ OBJECTID +" NOT NULL, "
					+ "model_id "+OBJECTID +" NOT NULL, "
					+ "model_version "+INTEGER +" NOT NULL, "
					+ "rank "+INTEGER
					+ (AUTO_INCREMENT.endsWith("PRIMARY KEY") ? "" : (", "+PRIMARY_KEY+" (vim_id)") )
					+ ")");
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ) {
				if ( logger.isDebugEnabled() ) logger.debug("creating sequence "+schema+"seq_views");
				request("BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE "+schema+"seq_views_in_model'; EXCEPTION WHEN OTHERS THEN NULL; END;");
				request("CREATE SEQUENCE "+schema+"seq_views_in_model START WITH 1 INCREMENT BY 1 CACHE 100");

				if ( logger.isDebugEnabled() ) logger.debug("creating trigger "+schema+"trigger_views_in_model");
				request("CREATE OR REPLACE TRIGGER "+schema+"trigger_views_in_model "
						+ "BEFORE INSERT ON "+schema+"views_in_model "
						+ "FOR EACH ROW "
						+ "BEGIN"
						+ "  SELECT "+schema+"seq_views_in_model.NEXTVAL INTO :NEW.vim_id FROM DUAL;"
						+ "END;");
			}

			if ( logger.isDebugEnabled() ) logger.debug("creating table "+schema+"views_objects");
			request("CREATE TABLE "+schema+"views_objects ("
					+ "id "+OBJECTID +" NOT NULL, "
					+ "version "+INTEGER +" NOT NULL, "
					+ "container_id "+OBJECTID +" NOT NULL, "
					+ "view_id "+OBJECTID +" NOT NULL, "
					+ "view_version "+INTEGER +" NOT NULL, "
					+ "class "+OBJECTID +" NOT NULL, "
					+ "element_id "+OBJECTID +", "
					+ "element_version "+INTEGER +", "
					+ "diagram_ref_id "+ OBJECTID +", "
					+ "border_color "+COLOR +", "
					+ "border_type "+INTEGER +", "
					+ "content "+TEXT +", "
					+ "documentation "+TEXT +", "
					+ "hint_content "+TEXT +", "
					+ "hint_title "+OBJ_NAME +", "
					+ "is_locked "+BOOLEAN +", "
					+ "image_path "+ OBJECTID +", "
					+ "image_position "+ INTEGER +", "
					+ "line_color "+ COLOR +", "
					+ "line_width "+ INTEGER +", "
					+ "fill_color "+ COLOR +", "
					+ "font "+ FONT +", "
					+ "font_color "+ COLOR +", "
					+ "name "+ OBJ_NAME +", "
					+ "notes "+ TEXT +", "
					+ "source_connections "+ TEXT + ", "
					+ "target_connections "+ TEXT + ", "
					+ "text_alignment "+ INTEGER +", "
					+ "text_position "+ INTEGER +", "
					+ "type "+ INTEGER +", "
					+ "x "+ INTEGER +", "
					+ "y "+ INTEGER +", "
					+ "width "+ INTEGER +", "
					+ "height "+ INTEGER +", "
					+ "rank "+ INTEGER +" NOT NULL, "
					+ "checksum "+ OBJECTID +" NOT NULL, "
					+ PRIMARY_KEY+" (id, version, container_id)"
					+ ")");

			commit();
			setAutoCommit(true);

			DBGui.popup(Level.INFO,"The database has been successfully initialized.");

		} catch (SQLException err) {
			rollback();
			setAutoCommit(true);
			// we delete the archi_plugin table because for some databases, DDL cannot be rolled back
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") )
				request("BEGIN EXECUTE IMMEDIATE 'DROP TABLE "+schema+"database_version'; EXCEPTION WHEN OTHERS THEN NULL; END;");
			else
				request("DROP TABLE IF EXISTS "+schema+"database_version");
			throw err;
		}

	}


	/**
	 * Upgrades the database
	 */
	private void upgradeDatabase(int fromVersion) throws SQLException {
		String COLUMN = DBPlugin.areEqual(databaseEntry.getDriver(), "sqlite") ? "COLUMN" : "";

		// convert from version 200 to 201 :
		//      - add a blob column into the views table
		if ( fromVersion == 200 ) {
			setAutoCommit(false);
			request("ALTER TABLE "+schema+"views ADD "+COLUMN+" screenshot "+IMAGE);
			
			request("UPDATE "+schema+"database_version SET version = 201 WHERE archi_plugin = '"+DBPlugin.pluginName+"'");
			commit();
			setAutoCommit(true);

			fromVersion = 201;
		}

		// convert from version 201 to 202 :
		//      - add a text_position column in the views_connections table
		//      - add source_connections and target_connections to views_objects and views_connections tables
		if ( fromVersion == 201 ) {
			setAutoCommit(false);
			request("ALTER TABLE "+schema+"views_connections ADD "+COLUMN+" text_position "+INTEGER);
			request("ALTER TABLE "+schema+"views_objects ADD "+COLUMN+" source_connections "+TEXT);
			request("ALTER TABLE "+schema+"views_objects ADD "+COLUMN+" target_connections "+TEXT);
			request("ALTER TABLE "+schema+"views_connections ADD "+COLUMN+" source_connections "+TEXT);
			request("ALTER TABLE "+schema+"views_connections ADD "+COLUMN+" target_connections "+TEXT);
			
			request("UPDATE "+schema+"database_version SET version = 202 WHERE archi_plugin = '"+DBPlugin.pluginName+"'");
			commit();
			setAutoCommit(true);

			fromVersion = 202;
		}
		
		// convert from version 202 to 203 :
		//      - add a text_position column in the views_connections table
		//      - add source_connections and target_connections to views_objects and views_connections tables
		if ( fromVersion == 202 ) {
			setAutoCommit(false);
			request("ALTER TABLE "+schema+"views_connections ADD "+COLUMN+" relationship_version "+INTEGER);
			request("UPDATE "+schema+"views_connections SET relationship_version = 1");
			
			request("ALTER TABLE "+schema+"views_objects ADD "+COLUMN+" element_version "+INTEGER);
			request("UPDATE "+schema+"views_objects SET element_version = 1");
			
			request("UPDATE "+schema+"database_version SET version = 203 WHERE archi_plugin = '"+DBPlugin.pluginName+"'");
			commit();
			setAutoCommit(true);

			fromVersion = 203;
		}
	}

	/**
	 * Add the parameters to the PreparedStatement and return a string with the complete request
	 */
	@SuppressWarnings("unchecked")
	@SafeVarargs
	private final <T> void constructStatement(PreparedStatement pstmt, String request, T... parameters) throws SQLException {
		StringBuilder debugRequest = new StringBuilder();
		String[] splittedRequest = request.split("\\?");

		int requestRank = 0;
		int parameterRank = 0;
		while (parameterRank < parameters.length) {
			if ( logger.isTraceEnabled() ) debugRequest.append(splittedRequest[requestRank]);

			if ( parameters[parameterRank] == null ) {
				if ( logger.isTraceEnabled() ) debugRequest.append("null");
				pstmt.setString(++requestRank, null);
			} else if ( parameters[parameterRank] instanceof String ) {
				if ( logger.isTraceEnabled() ) debugRequest.append("'"+parameters[parameterRank]+"'");
				pstmt.setString(++requestRank, (String)parameters[parameterRank]);

			} else if ( parameters[parameterRank] instanceof Integer ) {
				if ( logger.isTraceEnabled() ) debugRequest.append(parameters[parameterRank]);
				pstmt.setInt(++requestRank, (int)parameters[parameterRank]);

			} else if ( parameters[parameterRank] instanceof Timestamp ) {
				if ( logger.isTraceEnabled() ) debugRequest.append(String.valueOf((Timestamp)parameters[parameterRank]));
				pstmt.setTimestamp(++requestRank, (Timestamp)parameters[parameterRank]);

			} else if ( parameters[parameterRank] instanceof Boolean ) {
				if ( logger.isTraceEnabled() ) debugRequest.append(String.valueOf((boolean)parameters[parameterRank]));
				pstmt.setBoolean(++requestRank, (Boolean)parameters[parameterRank]);

			} else if ( parameters[parameterRank] instanceof ArrayList<?> ){
				for(int i = 0; i < ((ArrayList<String>)parameters[parameterRank]).size(); ++i) {
					if ( logger.isTraceEnabled() ) {
						if ( i != 0 )
							debugRequest.append(",");
						debugRequest.append("'"+((ArrayList<String>)parameters[parameterRank]).get(i)+"'");
					}
					pstmt.setString(++requestRank, ((ArrayList<String>)parameters[parameterRank]).get(i));
				}
			} else if ( parameters[parameterRank] instanceof byte[] ) {
				try  {
					pstmt.setBinaryStream(++requestRank, new ByteArrayInputStream((byte[])parameters[parameterRank]), ((byte[])parameters[parameterRank]).length);
					if ( logger.isTraceEnabled() ) debugRequest.append("[image as stream ("+((byte[])parameters[parameterRank]).length+" bytes)]");
				} catch (Exception err) {
					pstmt.setString(++requestRank, Base64.getEncoder().encodeToString((byte[])parameters[parameterRank]));
					if ( logger.isTraceEnabled() ) debugRequest.append("[image as base64 string ("+((byte[])parameters[parameterRank]).length+" bytes)]");
				}

			} else {
				if ( logger.isTraceEnabled() ) logger.trace(request);
				throw new SQLException("Unknown "+parameters[parameterRank].getClass().getSimpleName()+" parameter in SQL select.");
			}
			++parameterRank;
		}
		if ( logger.isTraceEnabled() ) {
			if ( requestRank < splittedRequest.length )
				debugRequest.append(splittedRequest[requestRank]);
			logger.trace("database request : "+debugRequest.toString());
		}
	}

	/**
	 * HashMap to store the JDBC preparedStatement
	 */
	private Map<String, PreparedStatement> preparedStatementMap = new HashMap<String, PreparedStatement>();

	/**
	 * Wrapper to generate and execute a SELECT request in the database<br>
	 * One may use '?' in the request and provide the corresponding values as parameters (at the moment, only strings are accepted)<br>
	 * The connection to the database should already exist 
	 * @return the resultset with the data read from the database
	 */
	@SafeVarargs
	public final <T> ResultSet select(String request, T... parameters) throws SQLException {
		assert (connection != null);

		PreparedStatement pstmt;
		try {
			pstmt = preparedStatementMap.get(request);
			if ( pstmt == null ) {
				pstmt = connection.prepareStatement(request, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				preparedStatementMap.put(request, pstmt);
			} else {
				pstmt.clearParameters();
			}
		} catch (SQLException err) {
			// in case of an SQLException, we log the raw request to ease the debug process
			if ( logger.isTraceEnabled() ) logger.trace("SQL Exception for database request : "+request);
			throw err;
		}

		constructStatement(pstmt, request, parameters);

		return pstmt.executeQuery();
	}

	/**
	 * wrapper to generate and execute a INSERT request in the database
	 * One may just provide the column names and the corresponding values as parameters
	 * the wrapper automatically generates the VALUES part of the request 
	 * @return The number of lines inserted in the table
	 */
	@SafeVarargs
	public final <T> int insert(String table, String[] columns, T...parameters) throws SQLException {
		assert (connection != null);

		StringBuilder cols = new StringBuilder();
		StringBuilder values = new StringBuilder();
		ArrayList<T> newParameters = new ArrayList<T>();

		for (int i=0 ; i < columns.length ; ++i) {
			if ( parameters[i] != null ) {
				if ( cols.length() != 0 ) {
					cols.append(", ");
					values.append(", ");
				}
				cols.append(columns[i]);
				values.append("?");
				newParameters.add(parameters[i]);
			}
		}

		if ( (cols.length() == 0) || (values.length() == 0) )
			throw new SQLException("SQL request cannot have all its parameters null.");

		return request("INSERT INTO "+table+" ("+cols.toString()+") VALUES ("+values.toString()+")", newParameters.toArray());
	}

	/**
	 * wrapper to execute an INSERT or UPDATE request in the database
	 * One may use '?' in the request and provide the corresponding values as parameters (strings, integers, booleans and byte[] are accepted)
	 * @return the number of lines impacted by the request
	 */
	@SafeVarargs
	public final <T> int request(String request, T... parameters) throws SQLException {
		assert (connection != null);
		int rowCount = 0;

		if ( parameters.length == 0 ) {		// no need to use a PreparedStatement
			if ( logger.isTraceEnabled() ) logger.trace(request);

			Statement stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

			rowCount = stmt.executeUpdate(request);
			stmt.close();
			stmt=null;
		} else {
			PreparedStatement pstmt = preparedStatementMap.get(request);

			if ( pstmt == null ) {
				pstmt = connection.prepareStatement(request, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				preparedStatementMap.put(request, pstmt);
			} else {
				pstmt.clearParameters();
			}

			constructStatement(pstmt, request, parameters);

			Savepoint savepoint = null;
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "postgresql") ) savepoint = connection.setSavepoint();
			try {
				rowCount = pstmt.executeUpdate();
			} catch (SQLException e) {
				if ( savepoint != null ) {
					try {
						connection.rollback(savepoint);
						if ( logger.isTraceEnabled() ) logger.trace("Rolled back to savepoint");
					} catch (SQLException e2) { logger.error("Failed to rollback to savepoint", e2); };
				}
				throw e;
			} finally {
				if ( savepoint != null ) connection.releaseSavepoint(savepoint);
			}
		}

		return rowCount;
	}

	protected Timestamp getLastTransactionTimestamp() throws SQLException {
		// if autocommit is on, then each call will return the current time
		if ( connection.getAutoCommit() )
			return new Timestamp(Calendar.getInstance().getTime().getTime());

		// if autoCommit is off, then we return the timestamp of the last commit (and therefore the timestamp of the beginning of the current transaction)
		return lastTransactionTimestamp;
	}

	public void setAutoCommit(boolean autoCommit) throws SQLException {
		if ( logger.isDebugEnabled() ) logger.debug("Setting database auto commit to "+String.valueOf(autoCommit));
		connection.setAutoCommit(autoCommit);
		if ( autoCommit )
			lastTransactionTimestamp = null;                                                         // all the request will have their own timetamp
		else
			lastTransactionTimestamp = new Timestamp(Calendar.getInstance().getTime().getTime());    // all the requests will have the same timestamp
	}

	public void commit() throws SQLException {
		if ( logger.isDebugEnabled() ) logger.debug("Commiting database transaction.");
		connection.commit();
		lastTransactionTimestamp = new Timestamp(Calendar.getInstance().getTime().getTime());
	}

	public void rollback() throws SQLException {
		if ( logger.isDebugEnabled() ) logger.debug("Rollbacking database transaction.");
		connection.rollback();
	}

	///////////////////////////////////////////////////////////////////////////////////
	//                                                                               //
	//   I M P O R T   S P E C I F I C   V A R I A B L E S   A N D   M E T H O D S   //
	//                                                                               //
	///////////////////////////////////////////////////////////////////////////////////

	/**
	 * ResultSet of the current transaction (used by import process to allow the loop to be managed outside the DBdatabase class)
	 */
	private ResultSet currentResultSet = null;

	/**
	 * Gets a component from the database and convert the result into a HashMap<br>
	 * Mainly used in DBGuiExportModel to compare a component to its database version.
	 */
	public HashMap<String, Object> getObject(EObject component, int version) throws Exception {
		String id = ((IIdentifier)component).getId();

		ResultSet result = null;
		if ( version == 0 ) {
			// because of PostGreSQL, we need to split the request in two
			if ( component instanceof IArchimateElement ) result = select("SELECT version, class, name, documentation, type, created_by, created_on, checksum FROM "+schema+"elements e WHERE version = (SELECT MAX(version) FROM "+schema+"elements WHERE id = e.id) AND id = ?", id);
			else if ( component instanceof IArchimateRelationship ) result = select("SELECT version, class, name, documentation, source_id, target_id, strength, access_type, created_by, created_on, checksum FROM "+schema+"relationships r WHERE version = (SELECT MAX(version) FROM "+schema+"relationships WHERE id = r.id) AND id = ?", id);
			else if ( component instanceof IFolder ) result = select("SELECT version, type, name, documentation, created_by, created_on, checksum FROM folders f WHERE version = (SELECT MAX(version) FROM "+schema+"folders WHERE id = f.id) AND id = ?", id);
			else if ( component instanceof IDiagramModel ) result = select("SELECT version, class, name, documentation, hint_content, hint_title, created_by, created_on, background, connection_router_type, viewpoint, checksum FROM "+schema+"views v WHERE version = (SELECT MAX(version) FROM "+schema+"views WHERE id = v.id) AND id = ?", id);
			else throw new Exception("Do not know how to get a "+component.getClass().getSimpleName()+" from the database.");
		} else {        
			if ( component instanceof IArchimateElement ) result = select("SELECT class, name, documentation, type, created_by, created_on, checksum FROM "+schema+"elements WHERE id = ? AND version = ?", id, version);
			else if ( component instanceof IArchimateRelationship ) result = select("SELECT class, name, documentation, source_id, target_id, strength, access_type, created_by, created_on, checksum FROM "+schema+"relationships WHERE id = ? AND version = ?", id, version);
			else if ( component instanceof IFolder ) result = select("SELECT type, name, documentation, created_by, created_on, checksum FROM "+schema+"folders WHERE id = ? AND version = ?", id, version);
			else if ( component instanceof IDiagramModel ) result = select("SELECT class, name, documentation, hint_content, hint_title, created_by, created_on, background, connection_router_type, viewpoint, checksum FROM "+schema+"views WHERE id = ? AND version = ?", id, version);
			else throw new Exception("Do not know how to get a "+component.getClass().getSimpleName()+" from the database.");
		}

		result.next();

		HashMap<String, Object> hashResult = resultSetToHashMap(result);      //TODO : if ArchimateModel : add the children in the HashMap

		if ( component instanceof IFolder ) hashResult.put("class", "Folder");                  // the folders table does not have a class column, so we add the property by hand
		result.close();
		result=null;


		result = select("SELECT count(*) as count_properties FROM "+schema+"properties WHERE parent_id = ? AND parent_version = ?", id, version);
		result.next();
		String[][] databaseProperties = new String[result.getInt("count_properties")][2];
		result.close();
		result=null;

		result = select("SELECT name, value FROM "+schema+"properties WHERE parent_id = ? AND parent_version = ?", id, version );
		int j = 0;
		while ( result.next() ) {
			databaseProperties[j++] = new String[] { result.getString("name"), result.getString("value") };
		}
		Arrays.sort(databaseProperties, new Comparator<String[]>() { public int compare(final String[] row1, final String[] row2) { return DBPlugin.collator.compare(row1[0],row2[0]); } });
		result.close();
		result=null;

		hashResult.put("properties", databaseProperties);

		return hashResult;
	}

	/**
	 * Creates a HashMap from a ResultSet
	 */
	public HashMap<String, Object> resultSetToHashMap(ResultSet rs) throws SQLException {
		HashMap<String, Object> map = new HashMap<String, Object>();

		for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
			if ( rs.getObject(column) != null ) {
				// we only listed the types that may be found by the database proxy and not the exhaustive types list
				String columnName = rs.getMetaData().getColumnName(column).toLowerCase();			// we need to convert to lowercase because of Oracle
				switch ( rs.getMetaData().getColumnType(column) ) {
					case Types.INTEGER :
					case Types.NUMERIC :
					case Types.SMALLINT :
					case Types.TINYINT :
					case Types.BIGINT :
					case Types.BOOLEAN :
					case Types.BIT :        map.put(columnName, rs.getInt(column));  break;

					case Types.TIMESTAMP :
					case Types.TIME :       map.put(columnName, rs.getTimestamp(column)); break;

					default :               map.put(columnName, rs.getString(column));
				}
			}
		}

		return map;
	}

	public void getModels(String filter, Table tblModels) throws Exception {
		ResultSet result;

		// Using a GROUP BY on PostGresSQL does not give the expected result ...   
		if ( filter==null || filter.length()==0 )
			result = select("SELECT id, name, version FROM "+schema+"models m WHERE version = (SELECT MAX(version) FROM "+schema+"models WHERE id = m.id) ORDER BY name");
		else
			result = select("SELECT id, name, version FROM "+schema+"models m WHERE version = (SELECT MAX(version) FROM "+schema+"models WHERE id = m.id) AND name like ? ORDER BY name", filter);


		while ( result.next() && result.getString("id") != null ) {
			if (logger.isTraceEnabled() ) logger.trace("found model \""+result.getString("name")+"\"");
			TableItem tableItem = new TableItem(tblModels, SWT.BORDER);
			tableItem.setText(result.getString("name"));
			tableItem.setData("id", result.getString("id"));
		}
		tblModels.layout();
		tblModels.setVisible(true);
		tblModels.setLinesVisible(true);
		tblModels.setRedraw(true);
		if (logger.isTraceEnabled() ) logger.trace("found "+tblModels.getItemCount()+" model"+(tblModels.getItemCount()>1?"s":"")+" in total");

		result.close();
		result=null;

		if ( tblModels.getItemCount() != 0 ) {
			tblModels.setSelection(0);
			tblModels.notifyListeners(SWT.Selection, new Event());      // calls database.getModelVersions()
		}
	}

	public void getModelVersions(String id, Table tblModelVersions) throws Exception {
		ResultSet result = select("SELECT version, created_by, created_on, name, note, purpose FROM "+schema+"models WHERE id = ? ORDER BY version DESC", id);
		TableItem tableItem = null;

		tblModelVersions.removeAll();
		while ( result.next() ) {
			if (logger.isTraceEnabled() ) logger.trace("found version \""+result.getString("version")+"\"");
			
			if ( tableItem == null ) {
				// if the first line, then we add the "latest version"
				tableItem = new TableItem(tblModelVersions, SWT.NULL);
				tableItem.setText(0, "");
				tableItem.setText(1, "Now");
				tableItem.setText(2, "");
				tableItem.setData("name", result.getString("name"));
				tableItem.setData("note", result.getString("note")!=null ? result.getString("note") : "");
				tableItem.setData("purpose", result.getString("purpose")!=null ? result.getString("purpose") : "");
			}
			tableItem = new TableItem(tblModelVersions, SWT.NULL);
			tableItem.setText(0, result.getString("version"));
			tableItem.setText(1, new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(result.getTimestamp("created_on")));
			tableItem.setText(2, result.getString("created_by"));
			tableItem.setData("name", result.getString("name"));
			tableItem.setData("note", result.getString("note")!=null ? result.getString("note") : "");
			tableItem.setData("purpose", result.getString("purpose")!=null ? result.getString("purpose") : "");
		}

		result.close();
		result=null;

		if ( tblModelVersions.getItemCount() != 0 ) {
			tblModelVersions.setSelection(0);
			tblModelVersions.notifyListeners(SWT.Selection, new Event());       // calls database.getModelVersions()
		}
	}

	private LinkedList<HashMap<String, Object>> _fifo = new LinkedList<HashMap<String, Object>>();
	private HashMap<String, Object> removeFirst(LinkedList<HashMap<String, Object>> fifo) {
		HashMap<String, Object> map = null;

		try {
			map = fifo.removeFirst();
		} catch ( NoSuchElementException e ) {      // if the element has not been added yet
			try {
				Thread.sleep(100);                  // we wait 100 milliseconds
			} catch (InterruptedException e1) {
				logger.debug("Exception when waiting for fifo", e1);
			}                     
			map = fifo.removeFirst();               // and we retry
		}
		return map;
	}




	private HashSet<String> allImagePaths;

	private int countElementsToImport = 0;
	public int countElementsToImport() { return countElementsToImport; }

	private int countElementsImported = 0;
	public int countElementsImported() { return countElementsImported; }

	private int countRelationshipsToImport = 0;
	public int countRelationshipsToImport() { return countRelationshipsToImport; }

	private int countRelationshipsImported = 0;
	public int countRelationshipsImported() { return countRelationshipsImported; }

	private int countFoldersToImport = 0;
	public int countFoldersToImport() { return countFoldersToImport; }

	private int countFoldersImported = 0;
	public int countFoldersImported() { return countFoldersImported; }

	private int countViewsToImport = 0;
	public int countViewsToImport() { return countViewsToImport; }

	private int countViewsImported = 0;
	public int countViewsImported() { return countViewsImported; }

	private int countViewObjectsToImport = 0;
	public int countViewObjectsToImport() { return countViewObjectsToImport; }

	private int countViewObjectsImported = 0;
	public int countViewObjectsImported() { return countViewObjectsImported; }

	private int countViewConnectionsToImport = 0;
	public int countViewConnectionsToImport() { return countViewConnectionsToImport; }

	private int countViewConnectionsImported = 0;
	public int countViewConnectionsImported() { return countViewConnectionsImported; }

	private int countImagesToImport = 0;
	public int countImagesToImport() { return countImagesToImport; }

	private int countImagesImported = 0;
	public int countImagesImported() { return countImagesImported; }
	
	private String importFoldersRequest;
	private String importElementsRequest;
	private String importRelationshipsRequest;
	private String importViewsRequest;
	private String importViewsObjectsRequest;
	private String importViewsConnectionsRequest;

	/**
	 * Import the model metadata from the database
	 */
	public int importModelMetadata(ArchimateModel model) throws Exception {
		// reseting the model's counters
		model.resetCounters();

		ResultSet result = select("SELECT name, purpose FROM "+schema+"models WHERE id = ? AND version = ?"
				,model.getId()
				,model.getCurrentVersion()
				);

		//TODO : manage the "real" model metadata :-)

		result.next();

		_fifo.add(resultSetToHashMap(result));
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					HashMap<String, Object> map = removeFirst(_fifo);

					model.setPurpose((String)map.get("purpose"));
				} catch (Exception e) {
					DBPlugin.setAsyncException(e);
				}
			}
		});
		result.close();
		result=null;

		importProperties(model);

		String documentation = DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ? "TO_CHAR(documentation)" : "documentation";
		
		String elementsDocumentation = DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ? "TO_CHAR(elements.documentation) AS documentation" : "elements.documentation";
		if ( model.getImportLatestVersion() ) {
			importElementsRequest = "SELECT DISTINCT element_id, parent_folder_id, version, class, name, type, documentation, created_on"+
									" FROM ("+
									"	SELECT elements_in_model.element_id, elements_in_model.parent_folder_id, elements.version, elements.class, elements.name, elements.type, "+elementsDocumentation+", elements.created_on"+
									"	FROM "+schema+"elements_in_model"+
									"	JOIN "+schema+"elements ON elements.id = elements_in_model.element_id AND elements.version = (SELECT MAX(version) FROM "+schema+"elements WHERE elements.id = elements_in_model.element_id)"+
									"	WHERE model_id = ? AND model_version = ?"+
									" UNION"+
									"	SELECT views_objects.element_id, null as parent_folder_id, elements.version, elements.class, elements.name, elements.type, "+elementsDocumentation+", elements.created_on"+
									"	FROM "+schema+"views_in_model"+
									"	JOIN "+schema+"views ON views.id = views_in_model.view_id"+
									"	JOIN "+schema+"views_objects ON views_objects.view_id = views.id AND views_objects.version = views.version"+
									"	JOIN "+schema+"elements ON elements.id = views_objects.element_id AND elements.version = (SELECT MAX(version) FROM "+schema+"elements WHERE elements.id = views_objects.element_id)"+
									"	WHERE model_id = ? AND model_version = ? AND element_id IS NOT null"+
									"   AND views_objects.element_id NOT IN ("+
									"      SELECT elements_in_model.element_id"+
									"	   FROM "+schema+"elements_in_model"+
									"	   JOIN "+schema+"elements ON elements.id = elements_in_model.element_id AND elements.version = (SELECT MAX(version) FROM "+schema+"elements WHERE elements.id = elements_in_model.element_id)"+
									"	   WHERE model_id = ? AND model_version = ?"+
									"   )"+
									" ) elements GROUP BY element_id, parent_folder_id, version, class, name, type, documentation, created_on";
			result = select("SELECT COUNT(*) AS countElements FROM ("+importElementsRequest+") elts"
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					);
		} else {
			importElementsRequest = "SELECT DISTINCT elements_in_model.element_id, elements_in_model.parent_folder_id, elements.version, elements.class, elements.name, elements.type, "+elementsDocumentation+", elements.created_on"+
									" FROM "+schema+"elements_in_model"+
									" JOIN "+schema+"elements ON elements.id = elements_in_model.element_id AND elements.version = elements_in_model.element_version"+
									" WHERE model_id = ? AND model_version = ?"+
									" GROUP BY element_id, parent_folder_id, version, class, name, type, "+documentation+", created_on";
			result = select("SELECT COUNT(*) AS countElements FROM ("+importElementsRequest+") elts"
					,model.getId()
					,model.getCurrentVersion()
					);
		}

		result.next();
		countElementsToImport = result.getInt("countElements");
		countElementsImported = 0;
		result.close();
		result=null;

		String relationshipsDocumentation = DBPlugin.areEqual(databaseEntry.getDriver(), "oracle") ? "TO_CHAR(relationships.documentation) AS documentation" : "relationships.documentation";
		if ( model.getImportLatestVersion() ) {
			importRelationshipsRequest = "SELECT DISTINCT relationship_id, parent_folder_id, version, class, name, documentation, source_id, target_id, strength, access_type, created_on"+
										 " FROM ("+
					 					 "	SELECT relationships_in_model.relationship_id, relationships_in_model.parent_folder_id, relationships.version, relationships.class, relationships.name, "+relationshipsDocumentation+", relationships.source_id, relationships.target_id, relationships.strength, relationships.access_type, relationships.created_on"+
										 "	FROM "+schema+"relationships_in_model"+
										 "	JOIN "+schema+"relationships ON relationships.id = relationships_in_model.relationship_id AND relationships.version = (SELECT MAX(version) FROM "+schema+"relationships WHERE relationships.id = relationships_in_model.relationship_id)"+
										 "	WHERE model_id = ? AND model_version = ?"+
										 " UNION"+
										 "	SELECT views_connections.relationship_id, null as parent_folder_id, relationships.version, relationships.class, relationships.name, "+relationshipsDocumentation+", relationships.source_id, relationships.target_id, relationships.strength, relationships.access_type, relationships.created_on"+
										 "	FROM "+schema+"views_in_model"+
										 "	JOIN "+schema+"views ON views.id = views_in_model.view_id"+
										 "	JOIN "+schema+"views_connections ON views_connections.view_id = views.id AND views_connections.version = views.version"+
										 "	JOIN "+schema+"relationships ON relationships.id = views_connections.relationship_id AND relationships.version = (SELECT MAX(version) FROM "+schema+"relationships WHERE relationships.id = views_connections.relationship_id)"+
										 "	WHERE model_id = ? AND model_version = ? and relationship_id IS NOT null"+
										 "  AND views_connections.relationship_id NOT IN ("+
										 "     SELECT relationships_in_model.relationship_id"+
										 "	   FROM "+schema+"relationships_in_model"+
										 "	   JOIN "+schema+"relationships ON relationships.id = relationships_in_model.relationship_id AND relationships.version = (SELECT MAX(version) FROM "+schema+"relationships WHERE relationships.id = relationships_in_model.relationship_id)"+
										 "	   WHERE model_id = ? AND model_version = ?"+
										 "  )"+
										 " ) relationships GROUP BY relationship_id, parent_folder_id, version, class, name, documentation, source_id, target_id, strength, access_type, created_on";
			result = select("SELECT COUNT(*) AS countRelationships FROM ("+importRelationshipsRequest+") relts"
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					);
		} else {
			importRelationshipsRequest = "SELECT relationships_in_model.relationship_id, relationships_in_model.parent_folder_id, relationships.version, relationships.class, relationships.name, "+relationshipsDocumentation+", relationships.source_id, relationships.target_id, relationships.strength, relationships.access_type, relationships.created_on"+
										 " FROM "+schema+"relationships_in_model"+
										 " INNER JOIN "+schema+"relationships ON relationships.id = relationships_in_model.relationship_id AND relationships.version = relationships_in_model.relationship_version"+
										 " WHERE model_id = ? AND model_version = ?"+
										 " GROUP BY relationship_id, parent_folder_id, version, class, name, "+documentation+", source_id, target_id, strength, access_type, created_on";
			result = select("SELECT COUNT(*) AS countRelationships FROM ("+importRelationshipsRequest+") relts"
					,model.getId()
					,model.getCurrentVersion()
					);
		}
		result.next();
		countRelationshipsToImport = result.getInt("countRelationships");
		countRelationshipsImported = 0;
		result.close();
		result=null;

		if ( model.getImportLatestVersion() ) {
			importFoldersRequest = "SELECT folder_id, folder_version, parent_folder_id, type, name, documentation, created_on"+
									" FROM "+schema+"folders_in_model"+
									" JOIN "+schema+"folders ON folders.id = folders_in_model.folder_id AND folders.version = (SELECT MAX(version) FROM "+schema+"folders WHERE folders.id = folders_in_model.folder_id)"+
									" WHERE model_id = ? AND model_version = ? ORDER BY folders_in_model.rank";
		} else {
			importFoldersRequest = "SELECT folder_id, folder_version, parent_folder_id, type, name, documentation, created_on"+
									" FROM "+schema+"folders_in_model"+
									" JOIN "+schema+"folders ON folders.id = folders_in_model.folder_id AND folders.version = folders_in_model.folder_version"+
									" WHERE model_id = ? AND model_version = ? ORDER BY folders_in_model.rank";
		}
		result = select("SELECT COUNT(*) AS countFolders FROM ("+importFoldersRequest+") fldrs"
				,model.getId()
				,model.getCurrentVersion()
				);
		result.next();
		countFoldersToImport = result.getInt("countFolders");
		countFoldersImported = 0;
		result.close();
		result=null;

		if ( model.getImportLatestVersion() ) {
			importViewsRequest = "SELECT id, version, parent_folder_id, class, name, documentation, background, connection_router_type, hint_content, hint_title, viewpoint, created_on"+
								 " FROM "+schema+"views_in_model"+
								 " JOIN "+schema+"views ON views.id = views_in_model.view_id AND views.version = (select max(version) from "+schema+"views where views.id = views_in_model.view_id)"+
								 " WHERE model_id = ? AND model_version = ? ORDER BY views_in_model.rank";
		} else {
			importViewsRequest = "SELECT id, version, parent_folder_id, class, name, documentation, background, connection_router_type, hint_content, hint_title, viewpoint, created_on"+
								 " FROM "+schema+"views_in_model"+
								 " JOIN "+schema+"views ON views.id = views_in_model.view_id AND views.version = views_in_model.view_version"+
								 " WHERE model_id = ? AND model_version = ? ORDER BY views_in_model.rank";
		}
		result = select("SELECT COUNT(*) AS countViews FROM ("+importViewsRequest+") vws"
				,model.getId()
				,model.getCurrentVersion()
				);
		result.next();
		countViewsToImport = result.getInt("countViews");
		countViewsImported = 0;
		result.close();
		result=null;

		if ( model.getImportLatestVersion() ) {
			importViewsObjectsRequest = "SELECT id, version, container_id, class, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, source_connections, target_connections, text_alignment, text_position, type, x, y, width, height"+
										" FROM "+schema+"views_in_model"+
										" JOIN "+schema+"views_objects ON views_objects.view_id = views_in_model.view_id AND views_objects.view_version = (SELECT MAX(version) FROM "+schema+"views_objects WHERE views_objects.view_id = views_in_model.view_id)"+
										" WHERE model_id = ? AND model_version = ? ORDER BY views_objects.rank";
		} else {
			importViewsObjectsRequest = "SELECT id, version, container_id, class, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, source_connections, target_connections, text_alignment, text_position, type, x, y, width, height"+
										" FROM "+schema+"views_in_model"+
										" JOIN "+schema+"views_objects ON views_objects.view_id = views_in_model.view_id AND views_objects.view_version = views_in_model.view_version"+
										" WHERE model_id = ? AND model_version = ? ORDER BY views_objects.rank";
		}
		result = select("SELECT COUNT(*) AS countViewsObjects FROM ("+importViewsObjectsRequest+") vobjs"
				,model.getId()
				,model.getCurrentVersion()
				);
		result.next();
		countViewObjectsToImport = result.getInt("countViewsObjects");
		countViewObjectsImported = 0;
		result.close();
		result=null;

		if ( model.getImportLatestVersion() ) {
			importViewsConnectionsRequest = "SELECT id, version, container_id, class, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, source_connections, target_connections, source_object_id, target_object_id, text_position, type "+
										" FROM "+schema+"views_in_model"+
										" JOIN "+schema+"views_connections ON views_connections.view_id = views_in_model.view_id AND views_connections.view_version = (SELECT MAX(version) FROM "+schema+"views_connections WHERE views_connections.view_id = views_in_model.view_id)"+
										" WHERE model_id = ? AND model_version = ? ORDER BY views_connections.rank";
		} else {
			importViewsConnectionsRequest = "SELECT id, version, container_id, class, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, source_connections, target_connections, source_object_id, target_object_id, text_position, type"+
										" FROM "+schema+"views_in_model"+
										" JOIN "+schema+"views_connections ON views_connections.view_id = views_in_model.view_id AND views_connections.view_version = views_in_model.view_version"+
										" WHERE model_id = ? AND model_version = ? ORDER BY views_connections.rank";
		}
		result = select("SELECT COUNT(*) AS countViewsConnections FROM ("+importViewsConnectionsRequest+") vcons"
				,model.getId()
				,model.getCurrentVersion()
				);
		result.next();
		countViewConnectionsToImport = result.getInt("countViewsConnections");
		countViewConnectionsImported = 0;
		result.close();
		result=null;

		result = select("SELECT COUNT(DISTINCT image_path) AS countImages FROM "+schema+"views_in_model INNER JOIN "+schema+"views ON "+schema+"views_in_model.view_id = views.id AND "+schema+"views_in_model.view_version = views.version INNER JOIN "+schema+"views_objects ON views.id = "+schema+"views_objects.view_id AND views.version = "+schema+"views_objects.version INNER JOIN "+schema+"images ON "+schema+"views_objects.image_path = images.path WHERE model_id = ? AND model_version = ? AND path IS NOT NULL" 
				,model.getId()
				,model.getCurrentVersion()
				);
		result.next();
		countImagesToImport = result.getInt("countImages");
		countImagesImported = 0;
		result.close();
		result=null;

		if ( logger.isDebugEnabled() ) logger.debug("Importing "+countElementsToImport+" elements, "+countRelationshipsToImport+" relationships, "+countFoldersToImport+" folders, "+countViewsToImport+" views, "+countViewObjectsToImport+" views objects, "+countViewConnectionsToImport+" views connections, and "+countImagesToImport+" images.");

		// initializing the HashMaps that will be used to reference imported objects
		allImagePaths = new HashSet<String>();

		return countElementsToImport + countRelationshipsToImport + countFoldersToImport + countViewsToImport + countViewObjectsToImport + countViewConnectionsToImport + countImagesToImport;
	}

	/**
	 * Prepare the import of the folders from the database
	 */
	public void prepareImportFolders(ArchimateModel model) throws Exception {
		currentResultSet = select(importFoldersRequest
				,model.getId()
				,model.getCurrentVersion()
				);
	}

	/**
	 * Import the folders from the database
	 */
	public boolean importFolders(ArchimateModel model) throws Exception {
		if ( currentResultSet != null ) {
			if ( currentResultSet.next() ) {
				IFolder folder = DBArchimateFactory.eINSTANCE.createFolder();

				folder.setId(currentResultSet.getString("folder_id"));
				((IDBMetadata)folder).getDBMetadata().setCurrentVersion(currentResultSet.getInt("folder_version"));
				((IDBMetadata)folder).getDBMetadata().setDatabaseCreatedOn(currentResultSet.getTimestamp("created_on"));

				_fifo.add(resultSetToHashMap(currentResultSet));
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							HashMap<String, Object> map = removeFirst(_fifo);

							folder.setName((String)map.get("name"));
							folder.setDocumentation((String)map.get("documentation"));

							String parentId = (String)map.get("parent_folder_id");

							if ( parentId != null && !parentId.isEmpty() ) {
								folder.setType(FolderType.get(0));                              // non root folders have got the "USER" type
								
								IFolder parent = model.getAllFolders().get(parentId);
								if ( parent == null )
									parent=model.getFolder(FolderType.get((int)map.get("root_type")));
								if ( parent == null ) 
									throw new Exception("Don't know where to create folder "+(String)map.get("name")+" of type "+(Integer)map.get("type")+" and root_type "+(Integer)map.get("root_type")+" (unknown folder ID "+(String)map.get("parent_folder_id")+")");

								parent.getFolders().add(folder);
							} else {
								folder.setType(FolderType.get((Integer)map.get("type")));        // root folders have got their own type
								model.getFolders().add(folder);
							}
						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});
				importProperties(folder);
				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)folder).getDBMetadata().getCurrentVersion()+" of folder "+currentResultSet.getString("name")+"("+currentResultSet.getString("folder_id")+")");

				// we reference this folder for future use (storing sub-folders or components into it ...)
				model.countObject(folder, false, null);
				++countFoldersImported;
				return true;
			}
			currentResultSet.close();
			currentResultSet = null;
		}
		return false;
	}

	/**
	 * check if the number of imported folders is equals to what is expected
	 */
	/*
	public void checkImportedFoldersCount() throws Exception {
		if ( countFoldersImported != countFoldersToImport )
			throw new Exception(countFoldersImported+" folders imported instead of the "+countFoldersToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countFoldersImported+" folders imported.");
	}
	*/
	
	/**
	 * Prepare the import of the elements from the database
	 */
	// it is complex because we need to retrieve the elements that have been added in views by other models
	public void prepareImportElements(ArchimateModel model) throws Exception {
		if ( model.getImportLatestVersion() ) {
			currentResultSet = select(importElementsRequest
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					);
		} else {
			currentResultSet = select(importElementsRequest
					,model.getId()
					,model.getCurrentVersion()
					);
		}
			
	}

	/**
	 * import the elements from the database
	 */
	public boolean importElements(ArchimateModel model) throws Exception {
		if ( currentResultSet != null ) {
			if ( currentResultSet.next() ) {
				IArchimateElement element = (IArchimateElement) DBArchimateFactory.eINSTANCE.create(currentResultSet.getString("class"));
				element.setId(currentResultSet.getString("element_id"));
				((IDBMetadata)element).getDBMetadata().setCurrentVersion(currentResultSet.getInt("version"));
				((IDBMetadata)element).getDBMetadata().setDatabaseCreatedOn(currentResultSet.getTimestamp("created_on"));

				_fifo.add(resultSetToHashMap(currentResultSet));
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							HashMap<String, Object> map = removeFirst(_fifo);

							element.setName((String)map.get("name"));
							element.setDocumentation((String)map.get("documentation"));
							if ( element instanceof IJunction   && map.get("type")!=null )  ((IJunction)element).setType((String)map.get("type"));

							IFolder folder;
							if ( map.get("parent_folder_id") == null ) {
								folder = model.getDefaultFolderForObject(element);
							} else {
								folder = model.getAllFolders().get(map.get("parent_folder_id"));
							}
							folder.getElements().add(element);

						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});
				importProperties(element);

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)element).getDBMetadata().getCurrentVersion()+" of "+currentResultSet.getString("class")+":"+currentResultSet.getString("name")+"("+currentResultSet.getString("element_id")+")");

				// we reference the element for future use (establishing relationships, creating views objects, ...)
				model.countObject(element, false, null);
				++countElementsImported;
				return true;
			}
			currentResultSet.close();
			currentResultSet = null;
		}
		return false;
	}

	/**
	 * check if the number of imported elements is equals to what is expected
	 */
	/*
	public void checkImportedElementsCount() throws Exception {
		if ( countElementsImported != countElementsToImport )
			throw new Exception(countElementsImported+" elements imported instead of the "+countElementsToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countElementsImported+" elements imported.");
	}
	*/

	/**
	 * Prepare the import of the relationships from the database
	 */
	public void prepareImportRelationships(ArchimateModel model) throws Exception {
		if ( model.getImportLatestVersion() ) {
			currentResultSet = select(importRelationshipsRequest
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					,model.getId()
					,model.getCurrentVersion()
					);
		} else {
			currentResultSet = select(importRelationshipsRequest
					,model.getId()
					,model.getCurrentVersion()
					);
		}
	}

	/**
	 * import the relationships from the database
	 */
	public boolean importRelationships(ArchimateModel model) throws Exception {
		if ( currentResultSet != null ) {
			if ( currentResultSet.next() ) {
				IArchimateRelationship relationship = (IArchimateRelationship) DBArchimateFactory.eINSTANCE.create(currentResultSet.getString("class"));
				relationship.setId(currentResultSet.getString("relationship_id"));
				((IDBMetadata)relationship).getDBMetadata().setCurrentVersion(currentResultSet.getInt("version"));
				((IDBMetadata)relationship).getDBMetadata().setDatabaseCreatedOn(currentResultSet.getTimestamp("created_on"));

				_fifo.add(resultSetToHashMap(currentResultSet));
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							HashMap<String, Object> map = removeFirst(_fifo);

							relationship.setName((String)map.get("name"));
							relationship.setDocumentation((String)map.get("documentation"));

							if ( relationship instanceof IInfluenceRelationship && map.get("strength")!=null )      ((IInfluenceRelationship)relationship).setStrength((String)map.get("strength"));
							if ( relationship instanceof IAccessRelationship    && map.get("access_type")!=null )   ((IAccessRelationship)relationship).setAccessType((Integer)map.get("access_type"));

							IFolder folder;
							if ( map.get("parent_folder_id") == null ) {
								folder = model.getDefaultFolderForObject(relationship);
							} else {
								folder = model.getAllFolders().get(map.get("parent_folder_id"));
							}
							folder.getElements().add(relationship);
						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});
				importProperties(relationship);

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)relationship).getDBMetadata().getCurrentVersion()+" of "+currentResultSet.getString("class")+":"+currentResultSet.getString("name")+"("+currentResultSet.getString("relationship_id")+")");

				// we reference the relationship for future use (establishing relationships, creating views connections, ...)
				model.countObject(relationship, false, null);
				model.registerSourceAndTarget(relationship, currentResultSet.getString("source_id"), currentResultSet.getString("target_id"));
				++countRelationshipsImported;
				return true;
			}
			currentResultSet.close();
			currentResultSet = null;
		}
		return false;
	}

	/**
	 * check if the number of imported relationships is equals to what is expected
	 */
	/*
	public void checkImportedRelationshipsCount() throws Exception {
		if ( countRelationshipsImported != countRelationshipsToImport )
			throw new Exception(countRelationshipsImported+" relationships imported instead of the "+countRelationshipsToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countRelationshipsImported+" relationships imported.");
	}
	*/

	/**
	 * Prepare the import of the views from the database
	 */
	public void prepareImportViews(ArchimateModel model) throws Exception {
		currentResultSet = select(importViewsRequest,
				model.getId(),
				model.getCurrentVersion()
				);
	}

	/**
	 * import the views from the database
	 */
	public boolean importViews(ArchimateModel model) throws Exception {
		if ( currentResultSet != null ) {
			if ( currentResultSet.next() ) {
				IDiagramModel view;
				if ( DBPlugin.areEqual(currentResultSet.getString("class"), "CanvasModel") )
					view = (IDiagramModel) DBCanvasFactory.eINSTANCE.create(currentResultSet.getString("class"));
				else
					view = (IDiagramModel) DBArchimateFactory.eINSTANCE.create(currentResultSet.getString("class"));

				view.setId(currentResultSet.getString("id"));
				((IDBMetadata)view).getDBMetadata().setCurrentVersion(currentResultSet.getInt("version"));
				((IDBMetadata)view).getDBMetadata().setDatabaseCreatedOn(currentResultSet.getTimestamp("created_on"));

				_fifo.add(resultSetToHashMap(currentResultSet));
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							HashMap<String, Object> map = removeFirst(_fifo);

							view.setName((String)map.get("name"));
							view.setDocumentation((String)map.get("documentation"));
							view.setConnectionRouterType((Integer)map.get("connection_router_type"));
							if ( view instanceof IArchimateDiagramModel && map.get("viewpoint")!=null )     ((IArchimateDiagramModel) view).setViewpoint((String)map.get("viewpoint"));
							if ( view instanceof ISketchModel           && map.get("background")!=null )    ((ISketchModel)view).setBackground((Integer)map.get("background"));
							if ( view instanceof IHintProvider          && map.get("hint_content")!=null )  ((IHintProvider)view).setHintContent((String)map.get("hint_content"));
							if ( view instanceof IHintProvider          && map.get("hint_title")!=null )    ((IHintProvider)view).setHintTitle((String)map.get("hint_title"));

							model.getAllFolders().get(map.get("parent_folder_id")).getElements().add(view);
						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});
				importProperties(view);

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)view).getDBMetadata().getCurrentVersion()+" of "+currentResultSet.getString("name")+"("+currentResultSet.getString("id")+")");

				// we reference the view for future use
				model.countObject(view, false, null);
				++countViewsImported;
				return true;
			}
			currentResultSet.close();
			currentResultSet = null;
		}
		return false;
	}

	/**
	 * check if the number of imported views is equals to what is expected
	 */
	/*
	public void checkImportedViewsCount() throws Exception {
		if ( countViewsImported != countViewsToImport )
			throw new Exception(countViewsImported+" views imported instead of the "+countViewsToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countViewsImported+" views imported.");
	}
	*/

	/**
	 * Prepare the import of the views objects of a specific view from the database
	 */
	public void prepareImportViewsObjects(String viewId, int version) throws Exception {
		currentResultSet = select("SELECT id, version, container_id, class, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, source_connections, target_connections, text_alignment, text_position, type, x, y, width, height FROM "+schema+"views_objects WHERE view_id = ? AND view_version = ? ORDER BY rank"
				,viewId
				,version
				);
	}

	/**
	 * import the views objects from the database
	 */
	public boolean importViewsObjects(ArchimateModel model, IDiagramModel view) throws Exception {
		if ( currentResultSet != null ) {
			if ( currentResultSet.next() ) {
				EObject eObject;

				if ( currentResultSet.getString("class").startsWith("Canvas") )
					eObject = DBCanvasFactory.eINSTANCE.create(currentResultSet.getString("class"));
				else
					eObject = DBArchimateFactory.eINSTANCE.create(currentResultSet.getString("class"));

				((IIdentifier)eObject).setId(currentResultSet.getString("id"));
				((IDBMetadata)eObject).getDBMetadata().setCurrentVersion(currentResultSet.getInt("version"));

				if ( eObject instanceof IDiagramModelArchimateComponent && currentResultSet.getString("element_id") != null) {
					// we check that the element already exists. If not, we import it (this may be the case during an individual view import.
					IArchimateElement element = model.getAllElements().get(currentResultSet.getString("element_id"));
					if ( element == null ) {
						if (logger.isTraceEnabled() ) logger.trace("importing individual element ...");
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								try {
									importElementFromId(model, null, currentResultSet.getString("element_id"), false);
								} catch (Exception e) {
									DBPlugin.setAsyncException(e);
								}
							}
						});
						DBPlugin.checkAsyncException();
					}
				}

				_fifo.add(resultSetToHashMap(currentResultSet));
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							HashMap<String, Object> map = removeFirst(_fifo);
							if ( eObject instanceof IDiagramModelArchimateComponent && map.get("element_id")!=null )                            ((IDiagramModelArchimateComponent)eObject).setArchimateConcept(model.getAllElements().get(map.get("element_id")));
							if ( eObject instanceof IDiagramModelReference          && map.get("diagram_ref_id")!=null )                        ((IDiagramModelReference)eObject).setReferencedModel(model.getAllViews().get(map.get("diagram_ref_id")));
							if ( eObject instanceof IDiagramModelArchimateObject    && map.get("type")!=null )                                  ((IDiagramModelArchimateObject)eObject).setType((Integer)map.get("type"));
							if ( eObject instanceof IBorderObject                   && map.get("border_color")!=null )                          ((IBorderObject)eObject).setBorderColor((String)map.get("border_color"));
							if ( eObject instanceof IDiagramModelNote               && map.get("border_type")!=null )                           ((IDiagramModelNote)eObject).setBorderType((Integer)map.get("border_type"));
							if ( eObject instanceof ITextContent                    && map.get("content")!=null )                               ((ITextContent)eObject).setContent((String)map.get("content"));
							if ( eObject instanceof IDocumentable                   && map.get("documentation")!=null )                         ((IDocumentable)eObject).setDocumentation((String)map.get("documentation"));
							if ( eObject instanceof INameable                       && map.get("name")!=null && map.get("element_id")==null )   ((INameable)eObject).setName((String)map.get("name"));
							if ( eObject instanceof IHintProvider                   && map.get("hint_content")!=null )                          ((IHintProvider)eObject).setHintContent((String)map.get("hint_content"));
							if ( eObject instanceof IHintProvider                   && map.get("hint_title")!=null )                            ((IHintProvider)eObject).setHintTitle((String)map.get("hint_title"));
							if ( eObject instanceof ILockable                       && map.get("is_locked")!=null )                             {int locked; if ( map.get("is_locked") instanceof String ) locked = Integer.valueOf((String)map.get("is_locked")); else locked=(Integer)map.get("is_locked"); ((ILockable)eObject).setLocked(locked==0?false:true);}
							if ( eObject instanceof IDiagramModelImageProvider      && map.get("image_path")!=null )                            ((IDiagramModelImageProvider)eObject).setImagePath((String)map.get("image_path"));
							if ( eObject instanceof IIconic                         && map.get("image_position")!=null )                        ((IIconic)eObject).setImagePosition((Integer)map.get("image_position"));
							if ( eObject instanceof ILineObject                     && map.get("line_color")!=null )                            ((ILineObject)eObject).setLineColor((String)map.get("line_color"));
							if ( eObject instanceof ILineObject                     && map.get("line_width")!=null )                            ((ILineObject)eObject).setLineWidth((Integer)map.get("line_width"));
							if ( eObject instanceof IDiagramModelObject             && map.get("fill_color")!=null )                            ((IDiagramModelObject)eObject).setFillColor((String)map.get("fill_color"));
							if ( eObject instanceof IFontAttribute                  && map.get("font")!=null )                                  ((IFontAttribute)eObject).setFont((String)map.get("font"));
							if ( eObject instanceof IFontAttribute                  && map.get("font_color")!=null )                            ((IFontAttribute)eObject).setFontColor((String)map.get("font_color"));
							if ( eObject instanceof ICanvasModelSticky              && map.get("notes")!=null )                                 ((ICanvasModelSticky)eObject).setNotes((String)map.get("notes"));
							if ( eObject instanceof ITextAlignment                  && map.get("text_alignment")!=null )                        ((ITextAlignment)eObject).setTextAlignment((Integer)map.get("text_alignment"));
							if ( eObject instanceof ITextPosition                   && map.get("text_position")!=null )                         ((ITextPosition)eObject).setTextPosition((Integer)map.get("text_position"));
							if ( eObject instanceof IDiagramModelObject )                                                                       ((IDiagramModelObject)eObject).setBounds((Integer)map.get("x"), (Integer)map.get("y"), (Integer)map.get("width"), (Integer)map.get("height"));

							// The container is either the view, or a container in the view
							if ( DBPlugin.areEqual((String)map.get("container_id"), view.getId()) )
								view.getChildren().add((IDiagramModelObject)eObject);
							else
								((IDiagramModelContainer)model.getAllViewObjects().get(map.get("container_id"))).getChildren().add((IDiagramModelObject)eObject);;

						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});

				if ( eObject instanceof IConnectable ) {
					model.registerSourceConnection((IDiagramModelObject)eObject, currentResultSet.getString("source_connections"));
					model.registerTargetConnection((IDiagramModelObject)eObject, currentResultSet.getString("target_connections"));
				}

				// If the object has got properties but does not have a linked element, then it may have distinct properties
				if ( eObject instanceof IProperties && currentResultSet.getString("element_id")==null ) {
					importProperties((IProperties)eObject);
				}

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)eObject).getDBMetadata().getCurrentVersion()+" of "+currentResultSet.getString("class")+"("+((IIdentifier)eObject).getId()+")");

				// we reference the view for future use
				model.countObject(eObject, false, null);
				++countViewObjectsImported;

				// if the object contains an image, we store its path to import it later
				if ( currentResultSet.getString("image_path") != null )
					allImagePaths.add(currentResultSet.getString("image_path"));

				return true;
			}
			currentResultSet.close();
			currentResultSet = null;
		}
		return false;
	}

	/**
	 * check if the number of imported objects is equals to what is expected
	 */
	/*
	public void checkImportedObjectsCount() throws Exception {
		if ( countViewObjectsImported != countViewObjectsToImport )
			throw new Exception(countViewObjectsImported+" objects imported instead of the "+countViewObjectsToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countViewObjectsImported+" objects imported.");
	}
	*/

	/**
	 * Prepare the import of the views connections of a specific view from the database
	 */
	public void prepareImportViewsConnections(String viewId, int version) throws Exception {
		currentResultSet = select("SELECT id, version, container_id, class, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, source_connections, target_connections, source_object_id, target_object_id, text_position, type FROM "+schema+"views_connections WHERE view_id = ? AND view_version = ? ORDER BY rank"
				,viewId
				,version
				);
	}

	/**
	 * import the views connections from the database
	 */
	public boolean importViewsConnections(ArchimateModel model) throws Exception {
		if ( currentResultSet != null ) {
			if ( currentResultSet.next() ) {
				EObject eObject;

				if ( currentResultSet.getString("class").startsWith("Canvas") )
					eObject = DBCanvasFactory.eINSTANCE.create(currentResultSet.getString("class"));
				else
					eObject = DBArchimateFactory.eINSTANCE.create(currentResultSet.getString("class"));

				((IIdentifier)eObject).setId(currentResultSet.getString("id"));
				((IDBMetadata)eObject).getDBMetadata().setCurrentVersion(currentResultSet.getInt("version"));

				if ( eObject instanceof IDiagramModelArchimateConnection && currentResultSet.getString("relationship_id") != null) {
					// we check that the relationship already exists. If not, we import it (this may be the case during an individual view import.
					IArchimateRelationship relationship = model.getAllRelationships().get(currentResultSet.getString("relationship_id"));
					if ( relationship == null ) {
						importRelationshipFromId(model, null, currentResultSet.getString("relationship_id"), false);
					}
				}

				_fifo.add(resultSetToHashMap(currentResultSet));
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							HashMap<String, Object> map = removeFirst(_fifo);

							if ( eObject instanceof INameable                           && map.get("name")!=null )              ((INameable)eObject).setName((String)map.get("name"));
							if ( eObject instanceof ILockable                           && map.get("is_locked")!=null )         {int locked; if ( map.get("is_locked") instanceof String ) locked = Integer.valueOf((String)map.get("is_locked")); else locked=(Integer)map.get("is_locked"); ((ILockable)eObject).setLocked(locked==0?false:true);}
							if ( eObject instanceof IDocumentable                       && map.get("documentation")!=null )     ((IDocumentable)eObject).setDocumentation((String)map.get("documentation"));
							if ( eObject instanceof ILineObject                         && map.get("line_color")!=null )        ((ILineObject)eObject).setLineColor((String)map.get("line_color"));
							if ( eObject instanceof ILineObject                         && map.get("line_width")!=null )        ((ILineObject)eObject).setLineWidth((Integer)map.get("line_width"));
							if ( eObject instanceof IFontAttribute                      && map.get("font")!=null )              ((IFontAttribute)eObject).setFont((String)map.get("font"));
							if ( eObject instanceof IFontAttribute                      && map.get("font_color")!=null )        ((IFontAttribute)eObject).setFontColor((String)map.get("font_color"));
							if ( eObject instanceof IDiagramModelConnection             && map.get("type")!=null )              ((IDiagramModelConnection)eObject).setType((Integer)map.get("type"));
							if ( eObject instanceof IDiagramModelConnection             && map.get("text_position")!=null )     ((IDiagramModelConnection)eObject).setTextPosition((Integer)map.get("text_position"));
							if ( eObject instanceof IDiagramModelArchimateConnection    && map.get("type")!=null )              ((IDiagramModelArchimateConnection)eObject).setType((Integer)map.get("type"));
							if ( eObject instanceof IDiagramModelArchimateConnection    && map.get("relationship_id")!=null )   ((IDiagramModelArchimateConnection)eObject).setArchimateConcept(model.getAllRelationships().get(map.get("relationship_id")));
						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});

				if ( eObject instanceof IConnectable ) {
					model.registerSourceConnection((IDiagramModelConnection)eObject, currentResultSet.getString("source_connections"));
					model.registerTargetConnection((IDiagramModelConnection)eObject, currentResultSet.getString("target_connections"));
				}
				//model.registerSourceAndTarget((IDiagramModelConnection)eObject, currentResultSet.getString("source_object_id"), currentResultSet.getString("target_object_id"));

				if ( eObject instanceof IDiagramModelConnection ) {
					ResultSet resultBendpoints = select("SELECT start_x, start_y, end_x, end_y FROM "+schema+"bendpoints WHERE parent_id = ? AND parent_version = ? ORDER BY rank"
							,((IIdentifier)eObject).getId()
							,((IDBMetadata)eObject).getDBMetadata().getCurrentVersion()
							);
					while(resultBendpoints.next()) {
						IDiagramModelBendpoint bendpoint = DBArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
						bendpoint.setStartX(resultBendpoints.getInt("start_x"));
						bendpoint.setStartY(resultBendpoints.getInt("start_y"));
						bendpoint.setEndX(resultBendpoints.getInt("end_x"));
						bendpoint.setEndY(resultBendpoints.getInt("end_y"));
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								try {
									((IDiagramModelConnection)eObject).getBendpoints().add(bendpoint);
								} catch (Exception e) {
									DBPlugin.setAsyncException(e);
								}
							}
						});
					}
					resultBendpoints.close();
				}

				// we reference the view for future use
				model.countObject(eObject, false, null);
				++countViewConnectionsImported;

				// If the connection has got properties but does not have a linked relationship, then it may have distinct properties
				if ( eObject instanceof IProperties && currentResultSet.getString("relationship_id")==null ) {
					importProperties((IProperties)eObject);
				}

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)eObject).getDBMetadata().getCurrentVersion()+" of "+currentResultSet.getString("class")+"("+((IIdentifier)eObject).getId()+")");

				return true;
			}
			currentResultSet.close();
			currentResultSet = null;
		}
		return false;
	}

	/**
	 * check if the number of imported connections is equals to what is expected
	 */
	/*
	public void checkImportedConnectionsCount() throws Exception {
		if ( countViewConnectionsImported != countViewConnectionsToImport )
			throw new Exception(countViewConnectionsImported+" connections imported instead of the "+countViewConnectionsToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countViewConnectionsImported+" connections imported.");
	}
	*/

	/**
	 * Prepare the import of the images from the database
	 */
	public HashSet<String> getAllImagePaths() {
		return allImagePaths;
	}

	/**
	 * import the views from the database
	 */
	public void importImage(ArchimateModel model, String path) throws Exception {
		currentResultSet = select("SELECT image FROM "+schema+"images WHERE path = ?", path);

		if (currentResultSet.next() ) {
			IArchiveManager archiveMgr = (IArchiveManager)model.getAdapter(IArchiveManager.class);
			try {
				String imagePath;
				byte[] imageContent = currentResultSet.getBytes("image");

				if ( logger.isDebugEnabled() ) logger.debug( "Importing "+path+" with "+imageContent.length/1024+" Ko of data");
				imagePath = archiveMgr.addByteContentEntry(path, imageContent);

				if ( DBPlugin.areEqual(imagePath, path) ) {
					if ( logger.isDebugEnabled() ) logger.debug( "... image imported");
				} else {
					if ( logger.isDebugEnabled() ) logger.debug( "... image imported but with new path "+imagePath);
					//TODO: the image was already in the cache but with a different path
					//TODO: we must search all the objects with "path" to replace it with "imagepath" 
				}

			} catch (Exception e) {
				currentResultSet.close();
				currentResultSet = null;
				throw new Exception("Import of image failed !", e.getCause()!=null ? e.getCause() : e);
			}
			if ( logger.isDebugEnabled() ) logger.debug("   imported "+path);
			++countImagesImported;
			currentResultSet.close();
			currentResultSet = null;
		} else {
			currentResultSet.close();
			currentResultSet = null;
			throw new Exception("Import of image failed : unkwnown image path "+path);
		}
	}

	/**
	 * check if the number of imported images is equals to what is expected
	 */
	public void checkImportedImagesCount() throws Exception {
		if ( countImagesImported != countImagesToImport )
			throw new Exception(countImagesImported+" images imported instead of the "+countImagesToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(countImagesImported+" images imported.");
	}

	/**
	 * Imports the properties of an Archi component<br>
	 * - missing properties are created
	 * - existing properties are updated with correct values if needed
	 * - existing properties with correct values are left untouched 
	 */
	private void importProperties(IProperties parent) throws Exception {
		int currentVersion;

		if ( parent instanceof IArchimateModel )
			currentVersion = ((ArchimateModel)parent).getCurrentVersion();
		else
			currentVersion = ((IDBMetadata)parent).getDBMetadata().getCurrentVersion();

		ResultSet result = select("SELECT name, value FROM "+schema+"properties WHERE parent_id = ? AND parent_version = ? ORDER BY rank"
				,((IIdentifier)parent).getId()
				,currentVersion
				);

		int i = 0;
		boolean shouldAdd;
		while ( result.next() ) {
			// if the property already exist, we update its value. If it doesn't, we create it
			IProperty prop ;

			shouldAdd = (i >= parent.getProperties().size() );
			if ( shouldAdd )
				prop = DBArchimateFactory.eINSTANCE.createProperty();
			else
				prop = parent.getProperties().get(i);

			if ( !DBPlugin.areEqual(prop.getKey(), result.getString("name")) )
				prop.setKey(result.getString("name"));
			if ( !DBPlugin.areEqual(prop.getValue(), result.getString("value")) )
				prop.setValue(result.getString("value"));

			if ( shouldAdd ) {
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							parent.getProperties().add(prop);
						} catch (Exception e) {
							DBPlugin.setAsyncException(e);
						}
					}
				});
			}

			++i;
		}
		result.close();
		result=null;

		// if there are more properties in memory than in database, we delete them
		while ( i < parent.getProperties().size() )
			parent.getProperties().remove(i);
	}

	public static String getSqlTypeName(int type) {
		switch (type) {
			case Types.BIT:
				return "BIT";
			case Types.TINYINT:
				return "TINYINT";
			case Types.SMALLINT:
				return "SMALLINT";
			case Types.INTEGER:
				return "INTEGER";
			case Types.BIGINT:
				return "BIGINT";
			case Types.FLOAT:
				return "FLOAT";
			case Types.REAL:
				return "REAL";
			case Types.DOUBLE:
				return "DOUBLE";
			case Types.NUMERIC:
				return "NUMERIC";
			case Types.DECIMAL:
				return "DECIMAL";
			case Types.CHAR:
				return "CHAR";
			case Types.VARCHAR:
				return "VARCHAR";
			case Types.LONGVARCHAR:
				return "LONGVARCHAR";
			case Types.DATE:
				return "DATE";
			case Types.TIME:
				return "TIME";
			case Types.TIMESTAMP:
				return "TIMESTAMP";
			case Types.BINARY:
				return "BINARY";
			case Types.VARBINARY:
				return "VARBINARY";
			case Types.LONGVARBINARY:
				return "LONGVARBINARY";
			case Types.NULL:
				return "NULL";
			case Types.OTHER:
				return "OTHER";
			case Types.JAVA_OBJECT:
				return "JAVA_OBJECT";
			case Types.DISTINCT:
				return "DISTINCT";
			case Types.STRUCT:
				return "STRUCT";
			case Types.ARRAY:
				return "ARRAY";
			case Types.BLOB:
				return "BLOB";
			case Types.CLOB:
				return "CLOB";
			case Types.REF:
				return "REF";
			case Types.DATALINK:
				return "DATALINK";
			case Types.BOOLEAN:
				return "BOOLEAN";
			case Types.ROWID:
				return "ROWID";
			case Types.NCHAR:
				return "NCHAR";
			case Types.NVARCHAR:
				return "NVARCHAR";
			case Types.LONGNVARCHAR:
				return "LONGNVARCHAR";
			case Types.NCLOB:
				return "NCLOB";
			case Types.SQLXML:
				return "SQLXML";
		}

		return "?";
	}

	public List<Object> importElementFromId(ArchimateModel model, IArchimateDiagramModel view, String elementId, boolean mustCreateCopy) throws Exception {
		IArchimateElement element;
		List<Object> imported = new ArrayList<Object>();
		boolean newElement = false;

		// TODO add an option to import elements recursively

		//TODO : add try catch block !!!

		// We import the element
		// PostGreSQL obliges to complexify the request
		ResultSet result = select("SELECT version, class, name, documentation, type, created_on FROM "+schema+"elements e WHERE version = (SELECT MAX(version) FROM "+schema+"elements WHERE id = e.id) AND id = ?", elementId);
		if ( !result.next() ) {
			result.close();
			result=null;
			throw new Exception("Element with id="+elementId+" has not been found in the database.");
		}

		if ( mustCreateCopy ) {
			if ( logger.isDebugEnabled() ) logger.debug("Importing a copy of element id "+elementId+".");
			element = (IArchimateElement) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
			element.setId(model.getIDAdapter().getNewID());
			newElement = true;

			element.setName(result.getString("name")+" (copy)");
			((IDBMetadata)element).getDBMetadata().setCurrentVersion(0);
			((IDBMetadata)element).getDBMetadata().setDatabaseCreatedOn(new Timestamp(Calendar.getInstance().getTime().getTime()));

		} else {
			element = model.getAllElements().get(elementId);
			if ( element == null ) {
				if ( logger.isDebugEnabled() ) logger.debug("Importing element id "+elementId+".");
				element = (IArchimateElement) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
				element.setId(elementId);
				newElement = true;
			} else {
				if ( logger.isDebugEnabled() ) logger.debug("Updating element id "+elementId+".");
				newElement = false;
			}

			if ( !DBPlugin.areEqual(element.getName(), result.getString("name")) ) element.setName(result.getString("name"));
			((IDBMetadata)element).getDBMetadata().setCurrentVersion(result.getInt("version"));
			((IDBMetadata)element).getDBMetadata().setDatabaseCreatedOn(result.getTimestamp("created_on"));
		}

		if ( !DBPlugin.areEqual(element.getDocumentation(), result.getString("documentation")) ) element.setDocumentation(result.getString("documentation"));
		if ( element instanceof IJunction ) {
			if ( !DBPlugin.areEqual(((IJunction)element).getType(), result.getString("type")) ) ((IJunction)element).setType(result.getString("type"));
		}

		importProperties(element);


		boolean createViewObject = false;
		if( newElement ) {
			model.getDefaultFolderForObject(element).getElements().add(element);
			model.getAllElements().put(element.getId(), element);
			model.countObject(element, false, null);
			createViewObject = view!=null;
		} else {
			if ( view == null ) {
				createViewObject = false;
			} else {
				createViewObject = componentToConnectable(view, element).isEmpty();
			}
		}

		if ( view != null && createViewObject ) {
			view.getChildren().add(ArchimateDiagramModelFactory.createDiagramModelArchimateObject(element));
		}

		result.close();
		result=null;
		++countElementsImported;

		// We import the relationships that source or target the element
		result = select("SELECT id, source_id, target_id FROM "+schema+"relationships WHERE source_id = ? OR target_id = ?", elementId, elementId);
		while ( result.next() && result.getString("id") != null ) {
			// we import only relationships that do not exist
			if ( model.getAllRelationships().get(result.getString("id")) == null ) {
				IArchimateElement sourceElement = model.getAllElements().get(result.getString("source_id"));
				IArchimateRelationship sourceRelationship = model.getAllRelationships().get(result.getString("source_id"));
				IArchimateElement targetElement = model.getAllElements().get(result.getString("target_id"));
				IArchimateRelationship targetRelationship = model.getAllRelationships().get(result.getString("target_id"));

				// we import only relations when both source and target are in the model
				if ( (sourceElement!=null || sourceRelationship!=null) && (targetElement!=null || targetRelationship!=null) ) {
					imported.add(importRelationshipFromId(model, view, result.getString("id"), false));
				}
			}
		}
		result.close();
		result=null;

		if ( !imported.isEmpty() )
			model.resolveRelationshipsSourcesAndTargets();

		imported.add(0, element);
		//ITreeModelView treeView = (ITreeModelView)ViewManager.showViewPart(ITreeModelView.ID, true);
		//if(treeView != null) {
		//	logger.trace("selecting newly imported components");
		//	treeView.getViewer().setSelection(new StructuredSelection(element), true);
		//}

		return imported;
	}

	private List<IConnectable> componentToConnectable(IArchimateDiagramModel view, IArchimateConcept concept) {
		List<IConnectable> connectables = new ArrayList<IConnectable>();
		for ( IDiagramModelObject viewObject: view.getChildren() ) {
			connectables.addAll(componentToConnectable((IDiagramModelArchimateComponent)viewObject, concept));
		}
		return connectables;
	}

	private List<IConnectable> componentToConnectable(IDiagramModelArchimateComponent component, IArchimateConcept concept) {
		List<IConnectable> connectables = new ArrayList<IConnectable>();

		if ( concept instanceof IArchimateElement ) {
			if ( DBPlugin.areEqual(component.getArchimateConcept().getId(), concept.getId()) ) connectables.add(component);
		} else if ( concept instanceof IArchimateRelationship ) {
			for ( IDiagramModelConnection conn: component.getSourceConnections() ) {
				if ( DBPlugin.areEqual(conn.getSource().getId(), concept.getId()) ) connectables.add(conn);
				if ( DBPlugin.areEqual(conn.getTarget().getId(), concept.getId()) ) connectables.add(conn);
			}
			for ( IDiagramModelConnection conn: component.getTargetConnections() ) {
				if ( DBPlugin.areEqual(conn.getSource().getId(), concept.getId()) ) connectables.add(conn);
				if ( DBPlugin.areEqual(conn.getTarget().getId(), concept.getId()) ) connectables.add(conn);
			}
		}

		if ( component instanceof IDiagramModelContainer ) {
			for ( IDiagramModelObject child: ((IDiagramModelContainer)component).getChildren() ) {
				connectables.addAll(componentToConnectable((IDiagramModelArchimateComponent)child, concept));
			}
		}
		return connectables;
	}

	public IArchimateRelationship importRelationshipFromId(ArchimateModel model, IArchimateDiagramModel view, String relationshipId, boolean mustCreateCopy) throws Exception {
		boolean newRelationship = false;

		ResultSet result = select("SELECT version, class, name, documentation, source_id, target_id, strength, access_type, created_on FROM "+schema+"relationships r WHERE version = (SELECT MAX(version) FROM "+schema+"relationships WHERE id = r.id) AND id = ?", relationshipId);
		if ( !result.next() ) {
			result.close();
			result=null;
			throw new Exception("relationship with id="+relationshipId+" has not been found in the database.");
		}
		// TODO check that the element does not exist yet ...

		IArchimateRelationship relationship;

		if ( mustCreateCopy ) {
			if ( logger.isDebugEnabled() ) logger.debug("Importing a copy of relationship id "+relationshipId+".");
			relationship = (IArchimateRelationship) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
			relationship.setId(model.getIDAdapter().getNewID());
			newRelationship = true;

			((IDBMetadata)relationship).getDBMetadata().setCurrentVersion(0);
			((IDBMetadata)relationship).getDBMetadata().setDatabaseCreatedOn(new Timestamp(Calendar.getInstance().getTime().getTime()));
		} else {
			relationship = model.getAllRelationships().get(relationshipId);
			if ( relationship == null ) {
				if ( logger.isDebugEnabled() ) logger.debug("Importing relationship id "+relationshipId+".");
				relationship = (IArchimateRelationship) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
				relationship.setId(relationshipId);
				newRelationship = true;
			} else {
				if ( logger.isDebugEnabled() ) logger.debug("Upgrading relationship id "+relationshipId+".");
				newRelationship = false;
			}

			((IDBMetadata)relationship).getDBMetadata().setCurrentVersion(result.getInt("version"));
			((IDBMetadata)relationship).getDBMetadata().setDatabaseCreatedOn(result.getTimestamp("created_on"));
		}

		if ( !DBPlugin.areEqual(relationship.getName(), result.getString("name")) ) relationship.setName(result.getString("name"));
		if ( !DBPlugin.areEqual(relationship.getDocumentation(), result.getString("documentation")) )relationship.setDocumentation(result.getString("documentation"));

		if ( relationship instanceof IInfluenceRelationship && result.getObject("strength")!=null    && !DBPlugin.areEqual(((IInfluenceRelationship)relationship).getStrength(), result.getString("strength")) )  ((IInfluenceRelationship)relationship).setStrength(result.getString("strength"));
		if ( relationship instanceof IAccessRelationship    && result.getObject("access_type")!=null && ((IAccessRelationship)relationship).getAccessType() != result.getInt("access_type") )  ((IAccessRelationship)relationship).setAccessType(result.getInt("access_type"));

		IArchimateConcept source = model.getAllElements().get(result.getString("source_id"));
		if ( source == null ) source = model.getAllRelationships().get(result.getString("source_id"));
		relationship.setSource(source);

		IArchimateConcept target = model.getAllElements().get(result.getString("target_id"));
		if ( source == null ) source = model.getAllRelationships().get(result.getString("target_id"));
		relationship.setTarget(target);


		importProperties(relationship);

		boolean createViewConnection = false;
		if ( newRelationship ) {
			model.getDefaultFolderForObject(relationship).getElements().add(relationship);
			model.getAllRelationships().put(relationship.getId(), relationship);
			createViewConnection = view!=null;
		} else {
			if ( view == null ) {
				createViewConnection = false;
			} else {
				createViewConnection = componentToConnectable(view, relationship).isEmpty();
			}
		}

		if ( view != null && createViewConnection ) {
			List<IConnectable> sourceConnections = componentToConnectable(view, relationship.getSource());
			List<IConnectable> targetConnections = componentToConnectable(view, relationship.getTarget());

			for ( IConnectable sourceConnection: sourceConnections ) {
				for ( IConnectable targetConnection: targetConnections ) {
					IDiagramModelArchimateConnection connection = ArchimateDiagramModelFactory.createDiagramModelArchimateConnection(relationship);
					connection.setSource(sourceConnection);
					sourceConnection.getSourceConnections().add(connection);
					connection.setTarget(targetConnection);
					targetConnection.getTargetConnections().add(connection);
				}
			}
		}

		result.close();
		result=null;
		++countRelationshipsImported;

		return relationship;
	}



	/**
	 * This method imports a view with all its components (graphical objects and connections) and requirements (elements and relationships)<br>
	 * elements and relationships that needed to be imported are located in a folder named by the view
	 */
	public IDiagramModel importViewFromId(ArchimateModel model, IFolder parentFolder, String id, boolean mustCreateCopy) throws Exception {
		if ( model.getAllViews().get(id) != null ) {
			if ( mustCreateCopy )
				DBGui.popup(Level.WARN, "Re-importing a view is not supported.\n\nIf you wish to create a copy of an existing table, you may use a copy-paste operation.");
			else
				DBGui.popup(Level.WARN, "Re-importing a view is not supported.\n\nIf you wish to refresh your view from the database, you may close your model and re-import it from the database.");
			return null;
		}

		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of view id "+id);
			else {
				logger.debug("Importing view id "+id);
			}
		}

		// 1 : we create the view
		ResultSet result = select("SELECT version, class, name, documentation, background, connection_router_type, hint_content, hint_title, viewpoint FROM "+schema+"views v WHERE version = (SELECT MAX(version) FROM "+schema+"views WHERE id = v.id) AND id = ?", id);
		result.next();
		IDiagramModel view;
		if ( DBPlugin.areEqual(result.getString("class"), "CanvasModel") )
			view = (IDiagramModel) DBCanvasFactory.eINSTANCE.create(result.getString("class"));
		else
			view = (IDiagramModel) DBArchimateFactory.eINSTANCE.create(result.getString("class"));

		if ( mustCreateCopy ) 
			view.setId(model.getIDAdapter().getNewID());
		else
			view.setId(id);

		((IDBMetadata)view).getDBMetadata().setCurrentVersion(result.getInt("version"));

		_fifo.add(resultSetToHashMap(result));
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					HashMap<String, Object> map = removeFirst(_fifo);

					view.setName((String)map.get("name"));
					view.setDocumentation((String)map.get("documentation"));
					view.setConnectionRouterType((Integer)map.get("connection_router_type"));
					if ( view instanceof IArchimateDiagramModel && map.get("viewpoint")!=null )     ((IArchimateDiagramModel) view).setViewpoint((String)map.get("viewpoint"));
					if ( view instanceof ISketchModel           && map.get("background")!=null )    ((ISketchModel)view).setBackground((Integer)map.get("background"));
					if ( view instanceof IHintProvider          && map.get("hint_content")!=null )  ((IHintProvider)view).setHintContent((String)map.get("hint_content"));
					if ( view instanceof IHintProvider          && map.get("hint_title")!=null )    ((IHintProvider)view).setHintTitle((String)map.get("hint_title"));

					if ( (parentFolder!=null) && (((IDBMetadata)parentFolder).getDBMetadata().getRootFolderType() == FolderType.DIAGRAMS_VALUE) )
						parentFolder.getElements().add(view);
					else
						model.getDefaultFolderForObject(view).getElements().add(view);
				} catch (Exception e) {
					DBPlugin.setAsyncException(e);
				}
			}
		});
		importProperties(view);

		model.getAllViews().put(((IIdentifier)view).getId(), view);

		if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)view).getDBMetadata().getCurrentVersion()+" of "+((INameable)view).getName()+"("+((IIdentifier)view).getId()+")");

		model.resetsourceAndTargetCounters();

		// 2 : we import the objects and create the corresponding elements if they do not exist yet
		//        importing an element will automatically import the relationships to and from this element
		prepareImportViewsObjects(((IIdentifier)view).getId(), ((IDBMetadata)view).getDBMetadata().getCurrentVersion());
		while ( importViewsObjects(model, view) ) { 
			DBPlugin.checkAsyncException();
		}
		DBGui.sync();

		// 3 : we import the connections and create the corresponding relationships if they do not exist yet
		prepareImportViewsConnections(((IIdentifier)view).getId(), ((IDBMetadata)view).getDBMetadata().getCurrentVersion());
		while ( importViewsConnections(model) ) {
			DBPlugin.checkAsyncException();
		}
		DBGui.sync();

		model.resolveRelationshipsSourcesAndTargets();
		model.resolveConnectionsSourcesAndTargets();

		//TODO : import missing images
		//for (String path: getAllImagePaths()) {
		//    importImage(model, path);
		//    DBPlugin.checkAsyncException();
		//}
		DBGui.sync();

		return view;
	}

	/**
	 * gets the latest model version in the database (0 if the model does not exist in the database)
	 */
	public int getLatestModelVersion(ArchimateModel model) throws Exception {
		// we use COALESCE to guarantee that a value is returned, even if the model does not exist in the database
		ResultSet result = select("SELECT COALESCE(MAX(version),0) as version FROM "+schema+"models WHERE id = ?", model.getId());
		result.next();

		int version = result.getInt("version");
		result.close();
		result=null;

		return version;
	}

	///////////////////////////////////////////////////////////////////////////////////
	//                                                                               //
	//   E X P O R T   S P E C I F I C   V A R I A B L E S   A N D   M E T H O D S   //
	//                                                                               //
	///////////////////////////////////////////////////////////////////////////////////

	private static enum COMPARE {NEW, IDENTICAL, UPDATED};

	private int countNewElements = 0;
	public int countNewElements() { return countNewElements; }

	private int countSyncedElements = 0;
	public int countSyncedElements() { return countSyncedElements; }

	private int countUpdatedElements = 0;
	public int countUpdatedElements() { return countUpdatedElements; }


	private int countNewRelationships = 0;
	public int countNewRelationships() { return countNewRelationships; }

	private int countSyncedRelationships = 0;
	public int countSyncedRelationships() { return countSyncedRelationships; }

	private int countUpdatedRelationships = 0;
	public int countUpdatedRelationships() { return countUpdatedRelationships; }


	private int countNewViews = 0;
	public int countNewViews() { return countNewViews; }

	private int countSyncedViews = 0;
	public int countSyncedViews() { return countSyncedViews; }

	private int countUpdatedViews = 0;
	public int countUpdatedViews() { return countUpdatedViews; }

	private int countNewViewObjects = 0;
	public int countNewViewObjects() { return countNewViewObjects; }

	private int countSyncedViewObjects = 0;
	public int countSyncedViewObjects() { return countSyncedViewObjects; }

	private int countUpdatedViewObjects = 0;
	public int countUpdatedViewObjects() { return countUpdatedViewObjects; }


	private int countNewViewConnections = 0;
	public int countNewViewConnections() { return countNewViewConnections; }

	private int countSyncedViewConnections = 0;
	public int countSyncedViewConnections() { return countSyncedViewConnections; }

	private int countUpdatedViewConnections = 0;
	public int countUpdatedViewConnections() { return countUpdatedViewConnections; }


	private int countNewFolders = 0;
	public int countNewFolders() { return countNewFolders; }

	private int countSyncedFolders = 0;
	public int countSyncedFolders() { return countSyncedFolders; }

	private int countUpdatedFolders = 0;
	public int countUpdatedFolders() { return countUpdatedFolders; }


	private int countNewImages = 0;
	public int countNewImages() { return countNewImages; }

	private int countSyncedImages = 0;
	public int countSyncedImages() { return countSyncedImages; }

	private int countUpdatedImages = 0;
	public int countUpdatedImages() { return countUpdatedImages; }


	public void checkComponentsToExport(ArchimateModel model, boolean checkFoldersAndViews) throws Exception {
		if ( logger.isDebugEnabled() ) logger.debug("Checking in database which components need to be exported.");

		countNewElements = 0;
		countSyncedElements = 0;
		countUpdatedElements = 0;
		for ( Entry<String, IArchimateElement> entry: model.getAllElements().entrySet() ) {
			switch (checkComponent(schema+"elements", entry.getValue())) {
				case NEW :       ++countNewElements; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew); break;
				case IDENTICAL : ++countSyncedElements; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced); break;
				case UPDATED :   ++countUpdatedElements; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated); break;
			}
		}

		countNewRelationships = 0;
		countSyncedRelationships = 0;
		countUpdatedRelationships = 0;
		for ( Entry<String, IArchimateRelationship> entry: model.getAllRelationships().entrySet() ) {
			switch (checkComponent(schema+"relationships", entry.getValue())) {
				case NEW :       ++countNewRelationships; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew); break;
				case IDENTICAL : ++countSyncedRelationships; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced); break;
				case UPDATED :   ++countUpdatedRelationships; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated); break;
			}
		}

		countNewFolders = 0;
		countSyncedFolders = 0;
		countUpdatedFolders = 0;
		if ( checkFoldersAndViews ) for ( Entry<String, IFolder> entry: model.getAllFolders().entrySet() ) {
			switch (checkComponent(schema+"folders", entry.getValue())) {
				case NEW :       ++countNewFolders; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew); break;
				case IDENTICAL : ++countSyncedFolders; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced); break;
				case UPDATED :   ++countUpdatedFolders; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated); break;
			}
		}

		countNewViews = 0;
		countSyncedViews = 0;
		countUpdatedViews = 0;
		if ( checkFoldersAndViews ) for ( Entry<String, IDiagramModel> entry: model.getAllViews().entrySet() ) {
			switch (checkComponent(schema+"views", entry.getValue())) {
				case NEW :       ++countNewViews; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew); break;
				case IDENTICAL : ++countSyncedViews; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced); break;
				case UPDATED :   ++countUpdatedViews; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated); break;
			}
		}

		countNewViewObjects = 0;
		countSyncedViewObjects = 0;
		countUpdatedViewObjects = 0;
		if ( checkFoldersAndViews ) for ( Entry<String, IDiagramModelComponent> entry: model.getAllViewObjects().entrySet() ) {
			IDiagramModelComponent parentDiagram = ((IDBMetadata)entry.getValue()).getDBMetadata().getParentDiagram();
			switch ( ((IDBMetadata)parentDiagram).getDBMetadata().getDatabaseStatus() )  {
				case isNew     : ++countNewViewObjects; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew); break;
				case isSynced  : ++countSyncedViewObjects; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced); break;
				case isUpdated : ++countUpdatedViewObjects; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated); break;
			}

			/*
        	if ( ((IDBMetadata)entry.getValue()).getDBMetadata().getCurrentVersion() == 0 ) {
                /*NEW* / ++countNewViewObjects; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew);
            } else {
                if ( ((IDBMetadata)entry.getValue()).getDBMetadata().isUpdated() ) {
                    /*UPDATED* /++countUpdatedViewObjects; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated);
                } else {
                    /*IDENTICAL* /++countSyncedViewObjects; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced);
                }
            }
			 */
		}

		countNewViewConnections = 0;
		countSyncedViewConnections = 0;
		countUpdatedViewConnections = 0;
		if ( checkFoldersAndViews ) for ( Entry<String, IDiagramModelConnection> entry: model.getAllViewConnections().entrySet() ) {
			IDiagramModelComponent parentDiagram = ((IDBMetadata)entry.getValue()).getDBMetadata().getParentDiagram();
			switch ( ((IDBMetadata)parentDiagram).getDBMetadata().getDatabaseStatus() )  {
				case isNew     : ++countNewViewConnections; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew); break;
				case isSynced  : ++countSyncedViewConnections; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced); break;
				case isUpdated : ++countUpdatedViewConnections; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated); break;
			}
			/*
            if ( ((IDBMetadata)entry.getValue()).getDBMetadata().getCurrentVersion() == 0 ) {
                /*NEW* / ++countNewViewConnections; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isNew);
            } else {
                if ( ((IDBMetadata)entry.getValue()).getDBMetadata().isUpdated() ) {
                    /*UPDATED* /++countUpdatedViewConnections; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isUpdated);
                } else {
                    /*IDENTICAL* /++countSyncedViewConnections; ((IDBMetadata)entry.getValue()).getDBMetadata().setDatabaseStatus(DATABASE_STATUS.isSynced);
                }
            }
			 */
		}

		countNewImages = 0;
		countSyncedImages = 0;
		countUpdatedImages = 0;
		for ( String image: model.getAllImagePaths() ) {
			switch (checkImage(image)) {
				case NEW :       ++countNewImages; break;
				case IDENTICAL : ++countSyncedImages; break;
				case UPDATED :   ++countUpdatedImages; break;   // should never be here, but just in case :-)
			}
		}
	}

	/**
	 * Retrieves the version and the checksum of the component from the database
	 */
	private COMPARE checkComponent(String tableName, EObject eObject) throws Exception {
		int currentVersion = 0;
		int databaseVersion = 0;
		String databaseChecksum = null;
		String databaseCreatedBy = null;
		Timestamp databaseCreatedOn = null;
		boolean hasCreatedByColumn = !DBPlugin.areEqual(databaseEntry.getDriver(), "neo4j") && !tableName.contains("_");        // tables containing an underscore in their name (views_objects and views_connections) do not have created_by and created_on columns

		ResultSet result;

		if ( DBPlugin.areEqual(databaseEntry.getDriver(), "neo4j") ) {
			if ( databaseEntry.getNeo4jNativeMode() )
				result = select("MATCH [t:"+tableName+" {id:?}] RETURN t.version as version, t.checksum as checksum ORDER BY t.version DESC", ((IIdentifier)eObject).getId());
			else
				result = select("MATCH (t:"+tableName+" {id:?}) RETURN t.version as version, t.checksum as checksum ORDER BY t.version DESC", ((IIdentifier)eObject).getId());
		} else {
			// we check the checksum in the database
			result = select("SELECT version, checksum"+(hasCreatedByColumn ? ", created_by, created_on" : "")+" FROM "+tableName+" WHERE id = ? ORDER BY version DESC", ((IIdentifier)eObject).getId());
		}
		while ( result.next() ) {
			// The first result gives the latest version 
			if ( databaseVersion == 0 ) {
				databaseVersion = result.getInt("version");
				databaseChecksum = result.getString("checksum");
				if ( hasCreatedByColumn ) {
					databaseCreatedBy = result.getString("created_by");
					databaseCreatedOn = result.getTimestamp("created_on");
				}
			}
			// We check every version in the database to retrieve the version of the current object
			// DO NOT SET IT BECAUSE IT VOIDS THE CONFLICT DETECTION
			if ( DBPlugin.areEqual(((IDBMetadata)eObject).getDBMetadata().getCurrentChecksum(), result.getString("checksum")) ) {
				currentVersion = result.getInt("version");
				break;
			}
		}
		result.close();
		result=null;

		if ( currentVersion == 0 ) {

			currentVersion = databaseVersion;
		}

		// Then, we store the values in the DBMetadata
		((IDBMetadata)eObject).getDBMetadata().setCurrentVersion(currentVersion);
		((IDBMetadata)eObject).getDBMetadata().setExportedVersion(currentVersion + 1);
		((IDBMetadata)eObject).getDBMetadata().setDatabaseVersion(databaseVersion);
		((IDBMetadata)eObject).getDBMetadata().setDatabaseChecksum(databaseChecksum);
		((IDBMetadata)eObject).getDBMetadata().setDatabaseCreatedBy(databaseCreatedBy);
		((IDBMetadata)eObject).getDBMetadata().setDatabaseCreatedOn(databaseCreatedOn);

		if ( databaseVersion == 0 ) {
			if ( logger.isTraceEnabled() ) logger.trace("   does not exist in the database. Current version  : "+currentVersion+", checksum : "+((IDBMetadata)eObject).getDBMetadata().getCurrentChecksum());
			return COMPARE.NEW;
		}

		if ( logger.isTraceEnabled() ) logger.trace("   Database version : "+databaseVersion+", checksum : "+databaseChecksum+(hasCreatedByColumn ? ", created by : "+databaseCreatedBy+", created on : "+databaseCreatedOn : ""));
		if ( logger.isTraceEnabled() ) logger.trace("   Current version  : "+currentVersion+", checksum : "+((IDBMetadata)eObject).getDBMetadata().getCurrentChecksum());

		if ( DBPlugin.areEqual(((IDBMetadata)eObject).getDBMetadata().getCurrentChecksum(), databaseChecksum ) ) 
			return COMPARE.IDENTICAL;

		return COMPARE.UPDATED; 
	}

	/**
	 * Checks the checksum of the image path
	 */
	private COMPARE checkImage(String path) throws Exception {
		COMPARE comp;
		// we check the existence of the image in the database
		ResultSet result = select("SELECT path FROM "+schema+"images WHERE path = ? ", path);

		if ( result.next() ) {
			if ( logger.isTraceEnabled() ) logger.trace("   does exist in the database.");
			comp = COMPARE.IDENTICAL;
		} else {
			if ( logger.isTraceEnabled() ) logger.trace("   does not exist in the database.");
			comp = COMPARE.NEW;
		}

		result.close();
		result=null;
		return comp;
	}


	/* *********************************************************************************** */
	/* *********************************************************************************** */
	/* ***               E X P O R T                                                   *** */
	/* *********************************************************************************** */
	/* *********************************************************************************** */

	/**
	 * Exports the model metadata into the database
	 */
	public void exportModel(ArchimateModel model, String releaseNote) throws Exception {
		final String[] modelsColumns = {"id", "version", "name", "note", "purpose", "created_by", "created_on"};

		insert(schema+"models", modelsColumns
				,model.getId()
				,model.getExportedVersion()
				,model.getName()
				,releaseNote
				,model.getPurpose()
				,System.getProperty("user.name")
				,getLastTransactionTimestamp()
				);

		exportProperties(model);
	}

	/**
	 * Export a component to the database
	 */
	public boolean exportEObject(EObject eObject, boolean assignToModel) throws Exception {
		boolean hasBeenExported = false;
		if ( ((IDBMetadata)eObject).getDBMetadata().needsToBeExported() ) {
			hasBeenExported = true;
			if ( logger.isDebugEnabled() ) logger.debug("version "+((IDBMetadata)eObject).getDBMetadata().getExportedVersion()+" of "+((IDBMetadata)eObject).getDBMetadata().getDebugName()+" is exported");

			if ( eObject instanceof IArchimateElement ) exportElement((IArchimateElement)eObject);
			else if ( eObject instanceof IArchimateRelationship ) exportRelationship((IArchimateRelationship)eObject);
			else if ( eObject instanceof IFolder ) exportFolder((IFolder)eObject);
			else if ( eObject instanceof IDiagramModel ) exportView((IDiagramModel)eObject);
			else if ( eObject instanceof IDiagramModelObject ) exportViewObject((IDiagramModelComponent)eObject);
			else if ( eObject instanceof IDiagramModelConnection ) exportViewConnection((IDiagramModelConnection)eObject);
			else throw new Exception("Do not know how to export "+eObject.getClass().getSimpleName());
		} else
			if ( logger.isDebugEnabled() ) logger.debug("version "+((IDBMetadata)eObject).getDBMetadata().getExportedVersion()+" of "+((IDBMetadata)eObject).getDBMetadata().getDebugName()+" does not need to be exported");

		if ( assignToModel ) {
			if ( logger.isDebugEnabled() ) logger.debug("assigning component to model");
			if ( eObject instanceof IArchimateElement ) assignElementToModel((IArchimateElement)eObject);
			else if ( eObject instanceof IArchimateRelationship ) assignRelationshipToModel((IArchimateRelationship)eObject);
			else if ( eObject instanceof IFolder ) assignFolderToModel((IFolder)eObject);
			else if ( eObject instanceof IDiagramModel ) assignViewToModel((IDiagramModel)eObject);
			else if ( eObject instanceof IDiagramModelObject ) ;											// IDiagramModelObject and IDiagramModelConnection are assigned to views not to models
			else if ( eObject instanceof IDiagramModelConnection ) ;
			else throw new Exception("Do not know how to assign to the model : "+eObject.getClass().getSimpleName());
		}

		return hasBeenExported;
	}

	/**
	 * Export an element to the database
	 */
	private void exportElement(IArchimateConcept element) throws Exception {
		final String[] elementsColumns = {"id", "version", "class", "name", "type", "documentation", "created_by", "created_on", "checksum"};

		if ( DBPlugin.areEqual(databaseEntry.getDriver(), "neo4j") ) {
			// USE MERGE instead to replace existing nodes
			request("CREATE (new:elements {id:?, version:?, class:?, name:?, type:?, documentation:?, checksum:?})"
					,element.getId()
					,((IDBMetadata)element).getDBMetadata().getExportedVersion()
					,element.getClass().getSimpleName()
					,element.getName()
					,((element instanceof IJunction) ? ((IJunction)element).getType() : null)
					,element.getDocumentation()
					,((IDBMetadata)element).getDBMetadata().getCurrentChecksum()
					);
		} else {
			insert(schema+"elements", elementsColumns
					,element.getId()
					,((IDBMetadata)element).getDBMetadata().getExportedVersion()
					,element.getClass().getSimpleName()
					,element.getName()
					,((element instanceof IJunction) ? ((IJunction)element).getType() : null)
					,element.getDocumentation()
					,System.getProperty("user.name")
					,getLastTransactionTimestamp()
					,((IDBMetadata)element).getDBMetadata().getCurrentChecksum()
					);
		}

		exportProperties(element);

		// the element has been exported to the database
		((IDBMetadata)element).getDBMetadata().setCurrentVersion(((IDBMetadata)element).getDBMetadata().getExportedVersion());
		((IDBMetadata)element).getDBMetadata().setDatabaseChecksum(((IDBMetadata)element).getDBMetadata().getCurrentChecksum());

		switch ( ((IDBMetadata)element).getDBMetadata().getDatabaseStatus() ) {
			case isNew : --countNewElements; ++countSyncedElements ; break;
			case isUpdated : --countUpdatedElements; ++countSyncedElements ; break;
			default : ; // shouldn't be here, but just in case ...
		}
	}

	/**
	 * This class variable allows to sort the exported elements that they are imported in the same order<br>
	 * It is reset to zero each time a connection to a new database is done (connection() method).
	 */
	private int elementRank = 0;

	/**
	 * Assign an element to a model into the database
	 */
	private void assignElementToModel(IArchimateConcept element) throws Exception {
		final String[] elementsInModelColumns = {"element_id", "element_version", "parent_folder_id", "model_id", "model_version", "rank"};
		ArchimateModel model = (ArchimateModel)element.getArchimateModel();

		insert(schema+"elements_in_model", elementsInModelColumns
				,element.getId()
				,((IDBMetadata)element).getDBMetadata().getCurrentVersion()   // we use currentVersion as it has been set in exportElement()
				,((IFolder)((IArchimateConcept)element).eContainer()).getId()
				,model.getId()
				,model.getExportedVersion()
				,++elementRank
				);
	}

	/**
	 * Export a relationship to the database
	 */
	private void exportRelationship(IArchimateConcept relationship) throws Exception {
		final String[] relationshipsColumns = {"id", "version", "class", "name", "documentation", "source_id", "target_id", "strength", "access_type", "created_by", "created_on", "checksum"};

		if ( DBPlugin.areEqual(databaseEntry.getDriver(), "neo4j") ) {
			// USE MERGE instead to replace existing nodes
			if ( databaseEntry.getNeo4jNativeMode() ) {
				if ( (((IArchimateRelationship)relationship).getSource() instanceof IArchimateElement) && (((IArchimateRelationship)relationship).getTarget() instanceof IArchimateElement) ) {
					request("MATCH (source:elements {id:?, version:?}), (target:elements {id:?, version:?}) CREATE [relationship:relationships {id:?, version:?, class:?, name:?, documentation:?, strength:?, access_type:?, checksum:?}], (source)-[relationship]->(target)"
							,((IArchimateRelationship)relationship).getSource().getId()
							,((IDBMetadata)((IArchimateRelationship)relationship).getSource()).getDBMetadata().getExportedVersion()
							,((IArchimateRelationship)relationship).getTarget().getId()
							,((IDBMetadata)((IArchimateRelationship)relationship).getTarget()).getDBMetadata().getExportedVersion()
							,relationship.getId()
							,((IDBMetadata)relationship).getDBMetadata().getExportedVersion()
							,relationship.getClass().getSimpleName()
							,relationship.getName()
							,relationship.getDocumentation()
							,((relationship instanceof IInfluenceRelationship) ? ((IInfluenceRelationship)relationship).getStrength() : null)
							,((relationship instanceof IAccessRelationship) ? ((IAccessRelationship)relationship).getAccessType() : null)
							,((IDBMetadata)relationship).getDBMetadata().getCurrentChecksum()
							);
				}
			} else {
				request("MATCH (source {id:?, version:?}), (target {id:?, version:?}) CREATE (relationship:relationships {id:?, version:?, class:?, name:?, documentation:?, strength:?, access_type:?, checksum:?}), (source)-[rel1:relatedTo]->(relationship)-[rel2:relatedTo]->(target)"
						,((IArchimateRelationship)relationship).getSource().getId()
						,((IDBMetadata)((IArchimateRelationship)relationship).getSource()).getDBMetadata().getExportedVersion()
						,((IArchimateRelationship)relationship).getTarget().getId()
						,((IDBMetadata)((IArchimateRelationship)relationship).getTarget()).getDBMetadata().getExportedVersion()
						,relationship.getId()
						,((IDBMetadata)relationship).getDBMetadata().getExportedVersion()
						,relationship.getClass().getSimpleName()
						,relationship.getName()
						,relationship.getDocumentation()
						,((relationship instanceof IInfluenceRelationship) ? ((IInfluenceRelationship)relationship).getStrength() : null)
						,((relationship instanceof IAccessRelationship) ? ((IAccessRelationship)relationship).getAccessType() : null)
						,((IDBMetadata)relationship).getDBMetadata().getCurrentChecksum()
						);
			}
		} else {
			insert(schema+"relationships", relationshipsColumns
					,relationship.getId()
					,((IDBMetadata)relationship).getDBMetadata().getExportedVersion()
					,relationship.getClass().getSimpleName()
					,relationship.getName()
					,relationship.getDocumentation()
					,((IArchimateRelationship)relationship).getSource().getId()
					,((IArchimateRelationship)relationship).getTarget().getId()
					,((relationship instanceof IInfluenceRelationship) ? ((IInfluenceRelationship)relationship).getStrength() : null)
					,((relationship instanceof IAccessRelationship) ? ((IAccessRelationship)relationship).getAccessType() : null)
					,System.getProperty("user.name")
					,getLastTransactionTimestamp()
					,((IDBMetadata)relationship).getDBMetadata().getCurrentChecksum()
					);
		}

		exportProperties(relationship);

		// the relationship has been exported to the database
		((IDBMetadata)relationship).getDBMetadata().setCurrentVersion(((IDBMetadata)relationship).getDBMetadata().getExportedVersion());
		((IDBMetadata)relationship).getDBMetadata().setDatabaseChecksum(((IDBMetadata)relationship).getDBMetadata().getCurrentChecksum());

		switch ( ((IDBMetadata)relationship).getDBMetadata().getDatabaseStatus() ) {
			case isNew : --countNewRelationships; ++countSyncedRelationships ; break;
			case isUpdated : --countUpdatedRelationships; ++countSyncedRelationships ; break;
			default : ; // shouldn't be here, but just in case ...
		}
	}

	/**
	 * This class variable allows to sort the exported relationships that they are imported in the same order<br>
	 * It is reset to zero each time a connection to a new database is done (connection() method).
	 */
	private int relationshipRank = 0;

	/**
	 * Assign a relationship to a model into the database
	 */
	private void assignRelationshipToModel(IArchimateConcept relationship) throws Exception {
		final String[] relationshipsInModelColumns = {"relationship_id", "relationship_version", "parent_folder_id", "model_id", "model_version", "rank"};

		ArchimateModel model = (ArchimateModel)relationship.getArchimateModel();

		insert(schema+"relationships_in_model", relationshipsInModelColumns
				,relationship.getId()
				,((IDBMetadata)relationship).getDBMetadata().getCurrentVersion()   // we use currentVersion as it has been set in exportRelationship()
				,((IFolder)((IArchimateConcept)relationship).eContainer()).getId()
				,model.getId()
				,model.getExportedVersion()
				,++relationshipRank
				);
	}

	/**
	 * Export a folder into the database.<br>
	 */
	private void exportFolder(IFolder folder) throws Exception {
		final String[] foldersColumns = {"id", "version", "type", "root_type", "name", "documentation", "created_by", "created_on", "checksum"};

		insert(schema+"folders", foldersColumns
				,folder.getId()
				,((IDBMetadata)folder).getDBMetadata().getExportedVersion()
				,folder.getType().getValue()
				,((IDBMetadata)folder).getDBMetadata().getRootFolderType()
				,folder.getName()
				,folder.getDocumentation()
				,System.getProperty("user.name")
				,getLastTransactionTimestamp()
				,((Folder)folder).getDBMetadata().getCurrentChecksum()
				);

		exportProperties(folder);

		// the folder has been exported to the database
		((IDBMetadata)folder).getDBMetadata().setCurrentVersion(((IDBMetadata)folder).getDBMetadata().getExportedVersion());
		((IDBMetadata)folder).getDBMetadata().setDatabaseChecksum(((IDBMetadata)folder).getDBMetadata().getCurrentChecksum());

		switch ( ((IDBMetadata)folder).getDBMetadata().getDatabaseStatus() ) {
			case isNew : --countNewFolders; ++countSyncedFolders ; break;
			case isUpdated : --countUpdatedFolders; ++countSyncedFolders ; break;
			default : ; // shouldn't be here, but just in case ...
		}
	}


	/**
	 * This class variable allows to sort the exported folders that they are imported in the same order<br>
	 * It is reset to zero each time a connection to a new database is done (connection() method).
	 */
	private int folderRank = 0;

	/**
	 * Assign a folder to a model into the database
	 */
	private void assignFolderToModel(IFolder folder) throws Exception {
		final String[] foldersInModelColumns = {"folder_id", "folder_version", "parent_folder_id", "model_id", "model_version", "rank"};

		ArchimateModel model = (ArchimateModel)folder.getArchimateModel();

		insert(schema+"folders_in_model", foldersInModelColumns
				,folder.getId()
				,((IDBMetadata)folder).getDBMetadata().getCurrentVersion()   // we use currentVersion as it has been set in exportFolder()
				,(((IIdentifier)((Folder)folder).eContainer()).getId() == model.getId() ? null : ((IIdentifier)((Folder)folder).eContainer()).getId())
				,model.getId()
				,model.getExportedVersion()
				,++folderRank
				);
	}

	/**
	 * Export a view into the database.
	 */
	private void exportView(IDiagramModel view) throws Exception {
		final String[] ViewsColumns = {"id", "version", "class", "created_by", "created_on", "name", "connection_router_type", "documentation", "hint_content", "hint_title", "viewpoint", "background", "screenshot", "checksum"};

		byte[] viewImage = null;

		if ( databaseEntry.getExportViewsImages() ) {
			viewImage = DBGui.createImage(view, 1, 10);
		}

		insert(schema+"views", ViewsColumns
				,view.getId()
				,((IDBMetadata)view).getDBMetadata().getExportedVersion()
				,view.getClass().getSimpleName()
				,System.getProperty("user.name")
				,new Timestamp(Calendar.getInstance().getTime().getTime())
				,view.getName()
				,view.getConnectionRouterType()
				,view.getDocumentation()
				,((view instanceof IHintProvider) ? ((IHintProvider)view).getHintContent() : null)
				,((view instanceof IHintProvider) ? ((IHintProvider)view).getHintTitle() : null)
				,((view instanceof IArchimateDiagramModel) ? ((IArchimateDiagramModel)view).getViewpoint() : null)
				,((view instanceof ISketchModel) ? ((ISketchModel)view).getBackground() : null)
				,viewImage
				,((IDBMetadata)view).getDBMetadata().getCurrentChecksum()
				);

		if ( databaseEntry.getExportViewsImages() ) {
			DBGui.disposeImage();
		}

		exportProperties(view);

		// the view is exported
		((IDBMetadata)view).getDBMetadata().setCurrentVersion(((IDBMetadata)view).getDBMetadata().getExportedVersion());
		((IDBMetadata)view).getDBMetadata().setDatabaseChecksum(((IDBMetadata)view).getDBMetadata().getCurrentChecksum());

		switch ( ((IDBMetadata)view).getDBMetadata().getDatabaseStatus() ) {
			case isNew : --countNewViews; ++countSyncedViews ; break;
			case isUpdated : --countUpdatedViews; ++countSyncedViews ; break;
			default : ; // shouldn't be here, but just in case ...
		}
	}

	/**
	 * This class variable allows to sort the exported views that they are imported in the same order<br>
	 * It is reset to zero each time a connection to a new database is done (connection() method).
	 */
	private int viewRank = 0;

	/**
	 * Assign a view to a model into the database
	 */
	private void assignViewToModel(IDiagramModel view) throws Exception {
		final String[] viewsInModelColumns = {"view_id", "view_version", "parent_folder_id", "model_id", "model_version", "rank"};

		ArchimateModel model = (ArchimateModel)view.getArchimateModel();

		insert(schema+"views_in_model", viewsInModelColumns
				,view.getId()
				,((IDBMetadata)view).getDBMetadata().getCurrentVersion()   // we use currentVersion as it has been set in exportView()
				,((IFolder)view.eContainer()).getId()
				,model.getId()
				,model.getExportedVersion()
				,++viewRank
				);
	}

	/**
	 * This class variable allows to sort the exported views objects that they are imported in the same order<br>
	 * It is reset to zero each time a connection to a new database is done (connection() method).
	 */
	private int viewObjectRank = 0;

	/**
	 * Export a view object into the database.<br>
	 * The rank allows to order the views during the import process.
	 */
	private void exportViewObject(IDiagramModelComponent viewObject) throws Exception {
		final String[] ViewsObjectsColumns = {"id", "version", "container_id", "view_id", "view_version", "class", "element_id", "diagram_ref_id", "type", "border_color", "border_type", "content", "documentation", "hint_content", "hint_title", "is_locked", "image_path", "image_position", "line_color", "line_width", "fill_color", "font", "font_color", "name", "notes", "source_connections", "target_connections", "text_alignment", "text_position", "x", "y", "width", "height", "rank", "checksum"};

		EObject viewContainer = viewObject.eContainer();
		while ( !(viewContainer instanceof IDiagramModel) ) {
			viewContainer = viewContainer.eContainer();
		}

		insert(schema+"views_objects", ViewsObjectsColumns
				,((IIdentifier)viewObject).getId()
				,((IDBMetadata)viewObject).getDBMetadata().getExportedVersion()
				,((IIdentifier)viewObject.eContainer()).getId()
				,((IIdentifier)viewContainer).getId()
				,((IDBMetadata)viewContainer).getDBMetadata().getExportedVersion()
				,viewObject.getClass().getSimpleName()
				,((viewObject instanceof IDiagramModelArchimateComponent) ? ((IDiagramModelArchimateComponent)viewObject).getArchimateConcept().getId() : null)
				,((viewObject instanceof IDiagramModelReference) ? ((IDiagramModelReference)viewObject).getReferencedModel().getId() : null)
				,((viewObject instanceof IDiagramModelArchimateObject) ? ((IDiagramModelArchimateObject)viewObject).getType() : null)
				,((viewObject instanceof IBorderObject) ? ((IBorderObject)viewObject).getBorderColor() : null)
				,((viewObject instanceof IDiagramModelNote) ? ((IDiagramModelNote)viewObject).getBorderType() : null)
				,((viewObject instanceof ITextContent) ? ((ITextContent)viewObject).getContent() : null)
				,((viewObject instanceof IDocumentable && !(viewObject instanceof IDiagramModelArchimateComponent)) ? ((IDocumentable)viewObject).getDocumentation() : null)        // They have got there own documentation. The others use the documentation of the corresponding ArchimateConcept
				,((viewObject instanceof IHintProvider) ? ((IHintProvider)viewObject).getHintContent() : null)
				,((viewObject instanceof IHintProvider) ? ((IHintProvider)viewObject).getHintTitle() : null)
				//TODO : add helpHintcontent and helpHintTitle
				,((viewObject instanceof ILockable) ? (((ILockable)viewObject).isLocked()?1:0) : null)
				,((viewObject instanceof IDiagramModelImageProvider) ? ((IDiagramModelImageProvider)viewObject).getImagePath() : null)
				,((viewObject instanceof IIconic) ? ((IIconic)viewObject).getImagePosition() : null)
				,((viewObject instanceof ILineObject) ? ((ILineObject)viewObject).getLineColor() : null)
				,((viewObject instanceof ILineObject) ? ((ILineObject)viewObject).getLineWidth() : null)
				,((viewObject instanceof IDiagramModelObject) ? ((IDiagramModelObject)viewObject).getFillColor() : null)
				,((viewObject instanceof IFontAttribute) ? ((IFontAttribute)viewObject).getFont() : null)
				,((viewObject instanceof IFontAttribute) ? ((IFontAttribute)viewObject).getFontColor() : null)
				,((viewObject instanceof INameable && !(viewObject instanceof IDiagramModelArchimateComponent) && !(viewObject instanceof IDiagramModelReference)) ? ((INameable)viewObject).getName() : null)      // They have got there own name. The others use the name of the corresponding ArchimateConcept
				,((viewObject instanceof ICanvasModelSticky) ? ((ICanvasModelSticky)viewObject).getNotes() : null)
				,((viewObject instanceof IConnectable) ? encode(((IConnectable)viewObject).getSourceConnections()) : null)
				,((viewObject instanceof IConnectable) ? encode(((IConnectable)viewObject).getTargetConnections()) : null)
				,((viewObject instanceof ITextAlignment) ? ((ITextAlignment)viewObject).getTextAlignment() : null)
				,((viewObject instanceof ITextPosition) ? ((ITextPosition)viewObject).getTextPosition() : null)
				,((viewObject instanceof IDiagramModelObject) ? ((IDiagramModelObject)viewObject).getBounds().getX() : null)
				,((viewObject instanceof IDiagramModelObject) ? ((IDiagramModelObject)viewObject).getBounds().getY() : null)
				,((viewObject instanceof IDiagramModelObject) ? ((IDiagramModelObject)viewObject).getBounds().getWidth() : null)
				,((viewObject instanceof IDiagramModelObject) ? ((IDiagramModelObject)viewObject).getBounds().getHeight() : null)
				,++viewObjectRank
				,((IDBMetadata)viewObject).getDBMetadata().getCurrentChecksum()
				);

		if ( viewObject instanceof IProperties && !(viewObject instanceof IDiagramModelArchimateComponent))
			exportProperties((IProperties)viewObject);

		// The viewObject is exported
		((IDBMetadata)viewObject).getDBMetadata().setCurrentVersion(((IDBMetadata)viewObject).getDBMetadata().getExportedVersion());
		((IDBMetadata)viewObject).getDBMetadata().setDatabaseChecksum(((IDBMetadata)viewObject).getDBMetadata().getCurrentChecksum());

		switch ( ((IDBMetadata)viewObject).getDBMetadata().getDatabaseStatus() ) {
			case isNew : --countNewViewObjects; ++countSyncedViewObjects ; break;
			case isUpdated : --countUpdatedViewObjects; ++countSyncedViewObjects ; break;
			default : ; // shouldn't be here, but just in case ...
		}
	}

	/**
	 * This class variable allows to sort the exported views objects that they are imported in the same order<br>
	 * It is reset to zero each time a connection to a new database is done (connection() method).
	 */
	private int viewConnectionRank = 0;

	/**
	 * Export a view connection into the database.<br>
	 * The rank allows to order the views during the import process.
	 */
	private void exportViewConnection(IDiagramModelConnection viewConnection) throws Exception {
		final String[] ViewsConnectionsColumns = {"id", "version", "container_id", "view_id", "view_version", "class", "name", "documentation", "is_locked", "line_color", "line_width", "font", "font_color", "relationship_id", "source_connections", "target_connections", "source_object_id", "target_object_id", "text_position", "type", "rank", "checksum"};
		final String[] bendpointsColumns = {"parent_id", "parent_version", "rank", "start_x", "start_y", "end_x", "end_y"};


		EObject viewContainer = viewConnection.eContainer();
		while ( !(viewContainer instanceof IDiagramModel) ) {
			viewContainer = viewContainer.eContainer();
		}

		insert(schema+"views_connections", ViewsConnectionsColumns
				,((IIdentifier)viewConnection).getId()
				,((IDBMetadata)viewConnection).getDBMetadata().getExportedVersion()
				,((IIdentifier)viewConnection.eContainer()).getId()
				,((IIdentifier)viewContainer).getId()
				,((IDBMetadata)viewContainer).getDBMetadata().getExportedVersion()
				,viewConnection.getClass().getSimpleName()
				,((viewConnection instanceof INameable     && !(viewConnection instanceof IDiagramModelArchimateConnection)) ? ((INameable)viewConnection).getName() : null)                    // if there is a relationship behind, the name is the relationship name, so no need to store it.
				,((viewConnection instanceof IDocumentable && !(viewConnection instanceof IDiagramModelArchimateConnection)) ? ((IDocumentable)viewConnection).getDocumentation() : null)       // if there is a relationship behind, the documentation is the relationship name, so no need to store it.
				,((viewConnection instanceof ILockable) ? (((ILockable)viewConnection).isLocked()?1:0) : null)  
				,((viewConnection instanceof ILineObject) ? ((ILineObject)viewConnection).getLineColor() : null)
				,((viewConnection instanceof ILineObject) ? ((ILineObject)viewConnection).getLineWidth() : null)
				,((viewConnection instanceof IFontAttribute) ? ((IFontAttribute)viewConnection).getFont() : null)
				,((viewConnection instanceof IFontAttribute) ? ((IFontAttribute)viewConnection).getFontColor() : null)
				,((viewConnection instanceof IDiagramModelArchimateConnection) ? ((IDiagramModelArchimateConnection)viewConnection).getArchimateConcept().getId() : null)
				,((viewConnection instanceof IConnectable) ? encode(((IConnectable)viewConnection).getSourceConnections()) : null)
				,((viewConnection instanceof IConnectable) ? encode(((IConnectable)viewConnection).getTargetConnections()) : null)
				,((viewConnection instanceof IDiagramModelConnection) ? ((IDiagramModelConnection)viewConnection).getSource().getId() : null)
				,((viewConnection instanceof IDiagramModelConnection) ? ((IDiagramModelConnection)viewConnection).getTarget().getId() : null)
				,((viewConnection instanceof IDiagramModelConnection) ? ((IDiagramModelConnection)viewConnection).getTextPosition() : null)
				,((viewConnection instanceof IDiagramModelArchimateObject) ? ((IDiagramModelArchimateObject)viewConnection).getType() : (viewConnection instanceof IDiagramModelConnection) ? ((IDiagramModelConnection)viewConnection).getType() : null)
				,++viewConnectionRank
				,((IDBMetadata)viewConnection).getDBMetadata().getCurrentChecksum()
				);

		if ( viewConnection instanceof IProperties )
			exportProperties((IProperties)viewConnection);

		if ( viewConnection instanceof IDiagramModelConnection ) {
			for ( int pos = 0 ; pos < ((IDiagramModelConnection)viewConnection).getBendpoints().size(); ++pos) {
				IDiagramModelBendpoint bendpoint = ((IDiagramModelConnection)viewConnection).getBendpoints().get(pos);
				insert(schema+"bendpoints", bendpointsColumns
						,((IIdentifier)viewConnection).getId()
						,((IDBMetadata)viewConnection).getDBMetadata().getExportedVersion()
						,pos
						,bendpoint.getStartX()
						,bendpoint.getStartY()
						,bendpoint.getEndX()
						,bendpoint.getEndY()
						);
			}
		}
		// The viewConnection is exported
		((IDBMetadata)viewConnection).getDBMetadata().setCurrentVersion(((IDBMetadata)viewConnection).getDBMetadata().getExportedVersion());
		((IDBMetadata)viewConnection).getDBMetadata().setDatabaseChecksum(((IDBMetadata)viewConnection).getDBMetadata().getCurrentChecksum());

		switch ( ((IDBMetadata)viewConnection).getDBMetadata().getDatabaseStatus() ) {
			case isNew : --countNewViewConnections; ++countSyncedViewConnections ; break;
			case isUpdated : --countUpdatedViewConnections; ++countSyncedViewConnections ; break;
			default : ; // shouldn't be here, but just in case ...
		}
	}

	/**
	 * Export properties to the database
	 */
	private void exportProperties(IProperties parent) throws Exception {
		final String[] propertiesColumns = {"parent_id", "parent_version", "rank", "name", "value"};

		int exportedVersion;
		if ( parent instanceof ArchimateModel ) {
			exportedVersion = ((ArchimateModel)parent).getExportedVersion();
		} else 
			exportedVersion = ((IDBMetadata)parent).getDBMetadata().getExportedVersion();

		for ( int propRank = 0 ; propRank < parent.getProperties().size(); ++propRank) {
			IProperty prop = parent.getProperties().get(propRank);
			if ( DBPlugin.areEqual(databaseEntry.getDriver(), "neo4j") ) {
				request("MATCH (parent {id:?, version:?}) CREATE (prop:property {rank:?, name:?, value:?}), (parent)-[:hasProperty]->(prop)"
						,((IIdentifier)parent).getId()
						,exportedVersion
						,propRank
						,prop.getKey()
						,prop.getValue()
						);
			}
			else
				insert(schema+"properties", propertiesColumns
						,((IIdentifier)parent).getId()
						,exportedVersion
						,propRank
						,prop.getKey()
						,prop.getValue()
						);
		}
	}

	public DATABASE_STATUS exportImage(String path, byte[] image) throws SQLException, NoSuchAlgorithmException {
		String checksum = DBChecksum.calculateChecksum(image);
		DATABASE_STATUS status;
		ResultSet result = null;

		try {
			result = select("SELECT checksum FROM "+schema+"images WHERE path = ?", path);

			if ( result.next() ) {
				// if the image exists in the database, we update it if the checkum differs
				if ( !DBPlugin.areEqual(checksum, result.getString("checksum")) ) {
					request("UPDATE "+schema+"images SET image = ?, checksum = ? WHERE path = ?"
							,image
							,checksum
							,path
							);
					status = DATABASE_STATUS.isUpdated;
				} else {
					status = DATABASE_STATUS.isSynced;
				}
			} else {
				// if the image is not yet in the db, we insert it
				String[] databaseColumns = {"path", "image", "checksum"};
				insert(schema+"images", databaseColumns
						,path
						,image
						,checksum								
						);
				status = DATABASE_STATUS.isNew;
			}
		} finally {    
			if ( result != null ) result.close();
			result = null;
		}

		return status;
	}

	private String encode (EList<IDiagramModelConnection> connections) {
		StringBuilder result = new StringBuilder();
		for ( IDiagramModelConnection connection: connections ) {
			if ( result.length() > 0 )
				result.append(",");
			result.append(connection.getId());
		}
		return result.toString();
	}

	public static String getTargetConnectionsString(EList<IDiagramModelConnection> connections) {
		StringBuilder target = new StringBuilder();
		for ( IDiagramModelConnection connection: connections ) {
			if ( target.length() > 0 )
				target.append(",");
			target.append(connection.getId());
		}
		return target.toString();
	}
}