package nl.mpi.ams2xacml.conversion;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import nl.mpi.ams2xacml.dao.CorpusStructureDAO;
import nl.mpi.corpusstructure.AccessInfo;
import nl.mpi.corpusstructure.CorpusNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class CSrights2xacml {

	private static Logger log = LoggerFactory.getLogger(CSrights2xacml.class.getName());	

	private static CorpusStructureDAO csDAO;
	private static String csdbURL;
	private static String csdbUser;
	private static String csdbPassword;
	private static String policiesDir = "generatedPolicies/";

	private static DocumentBuilder docBuilder;
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
	}

	
	/**
	 * The main method
	 */
	public static void main(String [] args) throws Exception {

		// check command line arguments
		OptionParser parser = new OptionParser( "c:u:p:d:?*" );
		OptionSet options = parser.parse(args);
		if (options.has("c"))
			csdbURL = (String) options.valueOf("c");
		if (options.has("u"))
			csdbUser = (String) options.valueOf("u");
		if (options.has("p"))
			csdbPassword = (String) options.valueOf("p");
		if (options.has("d"))
			policiesDir = (String) options.valueOf("d");
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
			Document xacml = generateXACMLDocument(csDAO.getNodeType(nodeId), nodeId);
			storeXACMLfile(nodeId, xacml);
		}

		csDAO.closeCorpusStructureDB();
	}

	public static void init() throws Exception {

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

		csDAO = new CorpusStructureDAO(csdbURL, csdbUser, csdbPassword);


		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		docBuilder = docFactory.newDocumentBuilder();

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

	private static Document generateXACMLDocument(int type, String node) throws Exception {

		Document doc = docBuilder.parse(CSrights2xacml.class.getResourceAsStream("/defaultPolicy.xml"));

		Node templateNode;
		Node nodeToRemove;
		List<String> nodeRights;
		if (type == CorpusNode.CATALOGUE || type == CorpusNode.SESSION || 
				type == CorpusNode.CORPUS || type == CorpusNode.UNKNOWN) {
			//Only deal with write rights. CMDI nodes are always readable.
			templateNode = (Node) templateManageObjXPath.evaluate(doc, XPathConstants.NODE);
			nodeRights = csDAO.getWriteRightsFor(node);
			nodeToRemove = (Node) templateReadObjDSRuleXPath.evaluate(doc, XPathConstants.NODE);
		} else {
			//Only deal with read rights on object's OBJ data stream.
			templateNode = (Node) templateReadObjDSXPath.evaluate(doc, XPathConstants.NODE);
			nodeRights = csDAO.getReadRightsFor(node);
			nodeToRemove = (Node) templateManageObjRuleXPath.evaluate(doc, XPathConstants.NODE);
		}

		generateXACMLRights(templateNode, nodeToRemove, nodeRights);

		return doc;
	}

	private static void generateXACMLRights(Node templateNode, Node nodeToRemove, List<String> nodeRights) {
		if (nodeRights.size() > 1) {
			nodeRights.remove(0);
			if (nodeRights.size() < 1000) {
				for (String user : nodeRights) {
					Node newUserNode = templateNode.cloneNode(true);
					newUserNode.setTextContent(user);
					templateNode.getParentNode().appendChild(newUserNode);
				}
			} else {
				Node newUserNode = templateNode.cloneNode(true);
				newUserNode.setTextContent("authenticated");
				templateNode.getParentNode().appendChild(newUserNode);
			}
		} else if (nodeRights.get(0) == AccessInfo.EVERYBODY) {
			Node newUserNode = templateNode.cloneNode(true);
			newUserNode.setTextContent("anonymous");
			templateNode.getParentNode().appendChild(newUserNode);
		} else if (nodeRights.get(0) == AccessInfo.ALL_AUTH) {
			Node newUserNode = templateNode.cloneNode(true);
			newUserNode.setTextContent("authenticated");
			templateNode.getParentNode().appendChild(newUserNode);
		}

		nodeToRemove.getParentNode().removeChild(nodeToRemove);
	}

	private static void storeXACMLfile(String node, Document doc) throws Exception {
		String handle = csDAO.getHandleFor(node);
		int partIdentifierIdx = handle.indexOf("@");
		if (partIdentifierIdx != -1)
			handle = handle.substring(0, partIdentifierIdx);
		handle = handle.replaceAll("[^a-zA-Z0-9]", "_").replace("hdl:", "lat:");

		File resultFile = new File(policiesDir + handle + ".xml");

		if(!resultFile.getParentFile().exists() && !resultFile.getParentFile().mkdirs()) {
			log.error("Cannot create destination XACML directory!");
			return;
		}

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);

		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

		StreamResult result =  new StreamResult(new FileOutputStream(resultFile));
		transformer.transform(source, result);
	}
}
