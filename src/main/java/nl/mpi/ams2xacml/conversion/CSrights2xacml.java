package nl.mpi.ams2xacml.conversion;

import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import nl.mpi.ams2xacml.dao.CorpusStructureDAO;
import nl.mpi.ams2xacml.xacml.XACMLtemplateHandler;
import nl.mpi.corpusstructure.CorpusNode;

import org.w3c.dom.Node;

public class CSrights2xacml {

	private static CorpusStructureDAO csDAO;
	private static String csdbURL;
	private static String csdbUser;
	private static String csdbPassword;
	private static String usernameFormat;
	private static XACMLtemplateHandler xacmlHandler;

	private static XPathExpression templateReadObjDSXPath;
	private static XPathExpression templateReadObjDSRuleXPath;
	private static XPathExpression templateManageObjXPath;
	private static XPathExpression templateManageObjRuleXPath;
	private static List<String> startNodeIds = new ArrayList<String>();


	private static void showHelp() {
		System.err.println("INF: csrights2xacml.sh <options> [<start nodeId> <start nodeId> ...]");
		System.err.println("INF: <start nodeId>	nodes ids where to start the conversion. XACML policies will be generated for all the descendants of this node. (example: MPI12345#)");
		System.err.println("INF: csrights2xacml options:");
		System.err.println("INF: -c=<URL>   the corpusstructure database URL (default: 'lux08.mpi.nl:5432/corpusstructure')");
		System.err.println("INF: -u=<DB user>  the username to use when connecting to the database (default: 'imdiArchive')");
		System.err.println("INF: -p=<DB password>  the password to use when connecting to the database (default: '')");
		System.err.println("INF: -d=<DIR>  the directory where to output the policy files to. (default: './generatedPolicies/')");
		System.err.println("INF: -g=<integer>  replace groups with more than <integer> users by the 'authenticated' user. (default: -1, do not replace)");
		System.err.println("INF: -f=<format>  the username format to use in te output XACML. Possible values are: 'strip' (remove '@domain' from usernames), "
				+ "'keep' (leave usernames as they are returned from the corpusstructure) and 'both' (generate both versions for each user). (default: keep)");
	}

	
	/**
	 * The main method
	 */
	public static void main(String [] args) throws Exception {
		xacmlHandler = new XACMLtemplateHandler();
		// check command line arguments
		OptionParser parser = new OptionParser( "c:u:p:d:g:f:?*" );
		OptionSet options = parser.parse(args);
		if (options.has("c"))
			csdbURL = (String) options.valueOf("c");
		if (options.has("u"))
			csdbUser = (String) options.valueOf("u");
		if (options.has("p"))
			csdbPassword = (String) options.valueOf("p");
		if (options.has("d")) {
			xacmlHandler.setPoliciesDir((String) options.valueOf("d"));
		}
		if (options.has("g")) {
			int number = Integer.parseInt((String) options.valueOf("g"));
			xacmlHandler.setMaxUsersPerGroup(number);
		}
		if (options.has("f")) {
			usernameFormat = (String) options.valueOf("f");
			if (!usernameFormat.equals("keep") && !usernameFormat.equals("strip") && !usernameFormat.equals("both")) {
				showHelp();
				System.exit(0);
			}
		}
		if (options.has("?")) {
			showHelp();
			System.exit(0);
		}

		List<?> noArgs = options.nonOptionArguments();
		
		if (noArgs.size() < 1 ){
			System.err.println("ERR: At least one <start nodeId> argument should be supplied!");
			showHelp();
			System.exit(1);
		} else {
			for (Object arg : noArgs) {
				String a = (String) arg;
				if (a.startsWith("MPI") && a.endsWith("#")) {
					startNodeIds.add(a);
				} else {
					System.err.println("ERR: Invalid <start nodeId>:" + a + " make sure it is in the form: 'MPI12345#'");
					showHelp();
					System.exit(1);
				}
			}
		}

		init();

		List<String> nodeIds = csDAO.getAllLinkedNodes(startNodeIds);
		nodeIds.addAll(startNodeIds);

		for (String nodeId : nodeIds) {
                    boolean onSite;
                    System.out.println("NodeID: "+nodeId);
                    onSite = csDAO.getOnsite(nodeId);
                    if (!onSite) {
                        System.out.println("Node is not onsite, no access permissions known: "+nodeId); 
                    }
                    else {
			generateXACMLDocument(csDAO.getNodeType(nodeId), nodeId);
			String handle = csDAO.getHandleFor(nodeId);
			xacmlHandler.storeXACMLfile(handle);
                    }
		}
		
		csDAO.closeCorpusStructureDB();
	}

	public static void init() throws Exception {
		//fill in defaults
		if (startNodeIds.isEmpty())
			startNodeIds.add("MPI301420#");
		if (csdbUser == null)
			csdbUser = "imdiArchive";
		if (csdbPassword == null)
			csdbPassword = "";
		if (csdbURL == null)
			csdbURL = "jdbc:postgresql://lux08.mpi.nl:5432/corpusstructure";
		else
			csdbURL = "jdbc:postgresql://" + csdbURL;
		if (usernameFormat == null)
			usernameFormat = "keep";

		csDAO = new CorpusStructureDAO(csdbURL, csdbUser, csdbPassword);

		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();

		templateReadObjDSXPath = xpath.compile("/Policy/Rule[@RuleId='deny-dsid-mime']/Condition"
				+ "//SubjectAttributeDesignator[@AttributeId='urn:fedora:names:fedora:2.1:subject:loginId']"
				+ "/following-sibling::node()/AttributeValue[1]");

		templateReadObjDSRuleXPath = xpath.compile("/Policy/Rule[@RuleId='deny-dsid-mime']");

		templateManageObjXPath = xpath.compile("/Policy/Rule[@RuleId='deny-management-functions']/Condition"
				+ "//SubjectAttributeDesignator[@AttributeId='urn:fedora:names:fedora:2.1:subject:loginId']"
				+ "/following-sibling::node()/AttributeValue[1]");

		templateManageObjRuleXPath = xpath.compile("/Policy/Rule[@RuleId='deny-management-functions']");

	}

	private static void generateXACMLDocument(int type, String node) throws Exception {
		xacmlHandler.resetXACMLtemplateDocument();
		Node templateNode;
		Node nodeToRemove;
		List<String> allowedUsers;
		
		if (type == CorpusNode.CATALOGUE || type == CorpusNode.SESSION || 
				type == CorpusNode.CORPUS || type == CorpusNode.UNKNOWN) {
			//Only deal with write rights. CMDI nodes are always readable.
			allowedUsers = csDAO.getWriteRightsFor(node);
			templateNode = xacmlHandler.getXPathTemplateNode(templateManageObjXPath);
			nodeToRemove = xacmlHandler.getXPathTemplateNode(templateReadObjDSRuleXPath);
		} else {
			//Only deal with read rights on object's OBJ data stream.
			allowedUsers = csDAO.getReadRightsFor(node);
			templateNode = xacmlHandler.getXPathTemplateNode(templateReadObjDSXPath);
			nodeToRemove = xacmlHandler.getXPathTemplateNode(templateManageObjRuleXPath);
		}
		
		xacmlHandler.generateXACMLAccessList(allowedUsers, templateNode, nodeToRemove, usernameFormat);
                
                
	}

}
