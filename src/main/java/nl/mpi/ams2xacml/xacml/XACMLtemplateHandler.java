package nl.mpi.ams2xacml.xacml;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import nl.mpi.ams2xacml.conversion.CSrights2xacml;
import nl.mpi.corpusstructure.AccessInfo;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * A class to do generate XACML documents and write them to disk.
 * This class uses the defaultPolicy.xacml file as a template to
 * generate and manipulate new XACML policy files
 *
 * @author André Moreira
 */
public class XACMLtemplateHandler {
	private int maxUsersPerGroup = -1;
	private String policiesDir = "generatedPolicies/";
	private Document templateXACMLdocument;
	private Document workingXACMLdocument;
	
	public XACMLtemplateHandler () throws Exception {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		templateXACMLdocument = docBuilder.parse(CSrights2xacml.class.getResourceAsStream("/defaultPolicy.xml"));
	}
	
	/**
	 * Given a list of <i>users</i>, generates a new <i>templateNode</i> in the
	 * template policy {@link org.w3c.dom.Document Document} for each of them 
	 * and removes the <i>nodeToRemove</i>
	 * @param users The list of users to generate new nodes based on <i>templateNode</i>
	 * @param templateNode The {@link org.w3c.dom.Node Node} to repeat for
	 * each of the <i>users</i>
	 * @param nodeToRemove The {@link org.w3c.dom.Node Node} to remove from
	 * the template policy file
	 */
	public void generateXACMLAccessList(List<String> users, Node templateNode, Node nodeToRemove) {
		Node newUserNode = templateNode.cloneNode(true);
		if (users.size() > 1) {
			users.remove(0);
			if (maxUsersPerGroup == -1 || users.size() < maxUsersPerGroup) {
				for (String user : users) {
					newUserNode.setTextContent(user);
					templateNode.getParentNode().appendChild(newUserNode);
				}
			} else {
				newUserNode.setTextContent("authenticated");
				templateNode.getParentNode().appendChild(newUserNode);
			}
		} else if (users.get(0) == AccessInfo.EVERYBODY) {
			newUserNode.setTextContent("anonymous");
			templateNode.getParentNode().appendChild(newUserNode);
		} else if (users.get(0) == AccessInfo.ALL_AUTH) {
			newUserNode.setTextContent("authenticated");
			templateNode.getParentNode().appendChild(newUserNode);
		}

		nodeToRemove.getParentNode().removeChild(nodeToRemove);
	}

	
	/**
	 * Stores the XACML {@link org.w3c.dom.Document Document} passed
	 * in the <i>doc</i> parameter, on a file named after the <i>handle</i> parameter
	 * inside the specified policiesDir
	 * @param doc The document to save
	 * @param handle The handle to name the file after
	 */
	public void storeXACMLfile(String handle) throws Exception {
		int partIdentifierIdx = handle.indexOf("@");
		
		if (partIdentifierIdx != -1)
			handle = handle.substring(0, partIdentifierIdx);
		handle = handle.replaceAll("[^a-zA-Z0-9]", "_").replace("hdl:", "lat:");

		File resultFile = new File(policiesDir + handle + ".xml");

		if(!resultFile.getParentFile().exists() && !resultFile.getParentFile().mkdirs()) {
			System.err.println("Cannot create destination XACML directory!");
			return;
		}

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(workingXACMLdocument);

		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

		StreamResult result =  new StreamResult(new FileOutputStream(resultFile));
		transformer.transform(source, result);
	}

	/**
	 * Sets the maximum number of users within the same XACML authorization list,
	 * after which the authorization list will be collapsed to the 'authenticated'
	 * user only.
	 * @param xacmlMaxUsers Maximum number of users
	 */
	public void setMaxUsersPerGroup(int xacmlMaxUsers) {
		this.maxUsersPerGroup = xacmlMaxUsers;
	}
	
	/**
	 * Sets the output directory for the generated XACML files
	 * @param directory The output directory
	 */
	public void setPoliciesDir(String directory) {
		this.policiesDir = directory;
	}
	
	/**
	 * Resets the template XACML policy {@link org.w3c.dom.Document Document}
	 * to its original state
	 */
	public void resetXACMLtemplateDocument() {
		this.workingXACMLdocument = (Document) this.templateXACMLdocument.cloneNode(true);
	}
	
	/**
	 * Returns the XACML template {@link org.w3c.dom.Node Node} specified by the <i>modeXPath</i>
	 * parameter
	 * @param nodeXPath The XPath expression of the node to return
	 * @return  A XACML template {@link org.w3c.dom.Node Node}
	 * @throws XPathExpressionException 
	 */
	public Node getXPathTemplateNode(XPathExpression nodeXPath) throws XPathExpressionException {		
		return  (Node) nodeXPath.evaluate(this.workingXACMLdocument, XPathConstants.NODE);
	}
}
