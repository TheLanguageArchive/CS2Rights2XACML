package nl.mpi.ams2xacml.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.mpi.corpusstructure.AccessInfo;
import nl.mpi.corpusstructure.ArchiveObjectsDB;
import nl.mpi.corpusstructure.CorpusStructureDB;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.Node;
import nl.mpi.corpusstructure.UnknownNodeException;

/**
 * A class to do corpusstructure DB lookups
 *
 * @author André Moreira
 */
public class CorpusStructureDAO {
	// these values must be set from the init code
	private String _corpusDbURL;
	private String _corpusDbUser;
	private String _corpusDbPassword;

	private CorpusStructureDB csdb;
	private ArchiveObjectsDB aodb;

	public CorpusStructureDAO(String jdbc, String user, String pass) {
		_corpusDbURL = jdbc;
		_corpusDbUser = user;
		_corpusDbPassword = pass;
		initCorpusStructureDB();
	}

	/**
	 * Given a node ID, return the corresponding {@link nl.mpi.corpusstructure.Node Node} 
	 * object or null if the database connection is not available or if no such
	 * nodeId exists.
	 * @param nodeId
	 * @return the {@link nl.mpi.corpusstructure.Node Node} object
	 */
	public Node getNode(String nodeId) {
		if(nodeId == null || csdb == null)
			return null;
		return csdb.getNode(nodeId);
	}

	/**
	 * Given a node ID, return the {@link nl.mpi.corpusstructure.Node Node} type or
	 * -1 if the database connection is not available or if no such
	 * nodeId exists.
	 * @param nodeId
	 * @return the {@link nl.mpi.corpusstructure.Node Node} type
	 */
	public int getNodeType(String nodeId) {
		if(nodeId == null || csdb == null)
			return -1;
		Node node = getNode(nodeId);
		if(node == null)
			return -1;
		return node.getNodeType();
	}	

	/**
	 * Given a handle (PID), return the node ID of that resource, or
	 * null if the database connection is not available or if no such
	 * handle exists.
	 * @param handle
	 * @return the {@link nl.mpi.corpusstructure.Node Node} ID
	 */
	public String getNodeIdFor(String handle) {
		if (handle == null || getArchiveObjectsDB() == null)
			return null;
		return getArchiveObjectsDB().getObjectForPID(handle);
	}
	
	/**
	 * Given a node ID, return the handle (PID) of that resource, or
	 * null if the database connection is not available or if no such
	 * nodeId exists.
	 * @param nodeId
	 * @return the handle (PID) of the node
	 */
	public String getHandleFor(String nodeId) {
		if (getArchiveObjectsDB() == null)
			return null;
		return getArchiveObjectsDB().getObjectPID(nodeId);
	}
	
	/**
	 * Given a {@link java.util.List List} of nodeIds, return all the node IDs of
	 * their descendants, or null if the database connection is not available
	 * or if no such node IDs exists.
	 * @param parentNodeIds {@link java.util.List List} of parent node IDs
	 * @return a {@link java.util.List List} with all the descendant nodeIds of the
	 * specified nodes.
	 */
	public List<String> getAllLinkedNodes(List<String> parentNodeIds) {
		List<String> nodes = new ArrayList<String>();
		for(String nodeId : parentNodeIds) {
			String [] descendants = csdb.getDescendants(nodeId, -1, "*");
			nodes.addAll(Arrays.asList(descendants));
		}
		return nodes;
	}

	/**
	 * Out who has read access to a corpusstructure node, taking a
	 * corpusstructure node ID as input. Will return AccessInfo.EVERYBODY
	 * if no nodeId is given or no corpusstructure DB is active to query
	 * for info.
	 * <p>The rights can be AccessInfo constants EVERYBODY, NOBODY (CLEARED
	 * is returned as NOBODY, too) or ALL_AUTH, or a List of users who
	 * have access.</p><p>Note that this depends on the internal syntax of the
	 * string returned by AccessInfo.getReadRights()</p>
	 * @param nodeId a node ID to query
	 * @return a {@link java.util.List List} of read rights: Either one 
	 * AccessInfo constant or an encoded string (first) followed by 1 or more user names.
	 */
	public List<String> getReadRightsFor(String nodeId) {
		ArrayList<String> list = new ArrayList<String>(1);
		if (nodeId == null || getCorpusStructureDB() == null) {
			list.add(AccessInfo.EVERYBODY);
			return list;
		}
		String acl;
		try {
			acl = getArchiveObjectsDB().getObjectAccessInfo(nodeId).getReadRights();

			if (AccessInfo.EVERYBODY.equals(acl)) {
				list.add(AccessInfo.EVERYBODY);
				return list;
			}
			if (AccessInfo.NOBODY.equals(acl) || AccessInfo.CLEARED.equals(acl)) {
				list.add(AccessInfo.NOBODY);
				return list;
			}
			if (AccessInfo.ALL_AUTH.equals(acl)) {
				list.add(AccessInfo.ALL_AUTH);
				return list;
			}
		} catch (UnknownNodeException e) {
			System.err.println("Unknown node "+nodeId);
			list.add(AccessInfo.NOBODY);
			return list;
		}
		// Lists typically have corpman plus a few, up to 50 users, rarely 100s
		list.add(acl); // special item
		String[] users = acl.split(" ");
		list.ensureCapacity(users.length + 1);
		for (int i=0; i<users.length; i++) {
			if (users[i].length() > 0)
				list.add(users[i]);
		}
		return list;
	}	
	
	/**
	 * Out who has write access to a corpusstructure node, taking a
	 * corpusstructure node ID as input. Will return AccessInfo.EVERYBODY
	 * if no nodeId is given or no corpusstructure DB is active to query
	 * for info. Do not use this method, but hasAccessTo() to check for
	 * a known user ID whether that user has access to a specific node.
	 * <p>The rights can be AccessInfo constants EVERYBODY, NOBODY (CLEARED
	 * is returned as NOBODY, too) or ALL_AUTH, or a List of users who
	 * have access.</p><p>Note that this depends on the internal syntax of the
	 * string returned by AccessInfo.getWriteRights()</p>
	 * @param nodeId a node ID to query
	 * @return a list of write rights: Either one AccessInfo constant
	 * or an encoded string (first) followed by 1 or more user names.
	 */
	public List<String> getWriteRightsFor(String nodeId) {
		ArrayList<String> list = new ArrayList<String>(1);
		if (nodeId == null || getCorpusStructureDB() == null) {
			list.add(AccessInfo.EVERYBODY);
			return list;
		}
		String acl;
		try {
			acl = getArchiveObjectsDB().getObjectAccessInfo(nodeId).getWriteRights();

			if (AccessInfo.EVERYBODY.equals(acl)) {
				list.add(AccessInfo.EVERYBODY);
				return list;
			}
			if (AccessInfo.NOBODY.equals(acl) || AccessInfo.CLEARED.equals(acl)) {
				list.add(AccessInfo.NOBODY);
				return list;
			}
			if (AccessInfo.ALL_AUTH.equals(acl)) {
				list.add(AccessInfo.ALL_AUTH);
				return list;
			}
		} catch (UnknownNodeException e) {
			System.err.println("Unknown node "+nodeId);
			list.add(AccessInfo.NOBODY);
			return list;
		}
		// Lists typically have corpman plus a few, up to 50 users, rarely 100s
		list.add(acl); // special item
		String[] users = acl.split(" ");
		list.ensureCapacity(users.length + 1);
		for (int i=0; i<users.length; i++) {
			if (users[i].length() > 0)
				list.add(users[i]);
		}
		return list;
	}
        
        public boolean getOnsite(String nodeId) {
            return getArchiveObjectsDB().isOnSite(nodeId);
        }

	/**
	 * Initialize the underlying corpusstructure database
	 */
	private void initCorpusStructureDB() {
		if ((csdb != null) && (aodb != null))
			return;
		if (_corpusDbURL == null || "none".equals(_corpusDbURL)) {
			csdb = null;
			aodb = null;
			return;
		}
		CorpusStructureDBImpl csdbimpl = new CorpusStructureDBImpl(_corpusDbURL, false, _corpusDbUser, _corpusDbPassword);
		csdb = csdbimpl;
		aodb = csdbimpl;
	}

	/**
	 * @return the low-level CorpusStructureDB object, do not close!
	 *         Returns null if no corpusstructure DB is active.
	 */
	public CorpusStructureDB getCorpusStructureDB() {
		if (csdb == null)
			initCorpusStructureDB();
		return csdb;
	}

	/**
	 * @return the low-level ArchiveObjectsDB object, do not close.
	 *         Returns null if no corpusstructure DB is active.
	 */
	public ArchiveObjectsDB getArchiveObjectsDB() {
		if (aodb == null) initCorpusStructureDB();
		return aodb;
	}

	/**
	 * Closes the low-level CorpussStructureDB and ArchiveObjectsDB
	 * objects used by CorpusStructureDAO, if any.
	 */
	public void closeCorpusStructureDB() {
		if (csdb != null) {
			csdb.close();
			csdb = null;
		}
		if (aodb != null) {
			aodb.close();
			aodb = null;
		}
	}
}
