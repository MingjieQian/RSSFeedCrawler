package org.crawler.support;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

public class DOMScreen {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		DOMScreen doMScreen = new DOMScreen();
		doMScreen.saveImages();
	}
	
	/**
	 * Show the contents of a node in the format of Google Chrome inspect elements console.
	 * 
	 * @param node a {@code Node} whose contents we are going to show.
	 * @param indent a {@code String} indicating the indent string in the current level.
	 * @param indentUnit an incremental string when showing the contents in the next level.
	 */
	public static void showNode(Node node, String indent, String indentUnit) {

		if (node.getClass().getSimpleName().compareTo("TextNode") == 0) {
			if (!((TextNode) node).isBlank()) {
				if (node.parent().childNodes().size() > 1 
						|| ((node.parent().nodeName().compareTo("p") == 0 ) 
								&& ((TextNode) node).text().length() > 40)) {
					System.out.println();
					System.out.print(indent);
					//System.out.print("\"" + ((TextNode) node).text() + "\"");
				} else {
					//System.out.print(((TextNode) node).text());
				}
				System.out.print(((TextNode) node).text());
			}
			return;
		}

		/*
		 * script Node has DataNode
		 */
		if (node.getClass().getSimpleName().compareTo("DataNode") == 0) {
			//System.out.print(((DataNode) node).getWholeData());
			return;
		}

		if (node.getClass().getSimpleName().compareTo("Comment") == 0) {
			System.out.println();
			System.out.print(indent);
			System.out.print("<!--");
			System.out.print(((Comment) node).getData());
			System.out.print("-->");
			return;
		}

		// For debugging
		/*if (node.nodeName().compareTo("p") == 0) {
			int a = 1;
			a = a + 1;
		}*/

		System.out.println();
		System.out.print(indent);

		if (node.getClass().getSimpleName().compareTo("Element") == 0 
				|| node.getClass().getSuperclass().getSimpleName().compareTo("Element") == 0) {

			/*Document.nodeName
			@Override
		    public String nodeName() {
		        return "#document";
		    }*/

			/*Element.nodeName
			@Override
		    public String nodeName() {
		        return tag.getName();
		    }*/

			System.out.print("<" + ((Element) node).tagName());
			Attributes attributes = node.attributes();
			for (Attribute attribute : attributes) {
				System.out.print(String.format(" %s = \"%s\"",
						attribute.getKey(), node.attr(attribute.getKey())));
			}
			System.out.print(">");

			List<Node> childNodeList = node.childNodes();			 
			for (Node subNode : childNodeList) {
				showNode(subNode, indent + indentUnit, indentUnit);
			}			

			/*if (node.nodeName().compareTo("img") != 0 &&
					node.nodeName().compareTo("input") != 0	) {*/
			if (!((Element) node).tag().isSelfClosing()) {

				if (childNodeList.size() == 0 
						|| (childNodeList.size() == 1 && node.nodeName().compareTo("p") != 0 
								&& childNodeList.get(0).getClass().getSimpleName().compareTo("TextNode") == 0)
								|| ((node.nodeName().compareTo("p") == 0)
										&& ((Element) node).text().length() <= 40)) {
					System.out.print(String.format("</%s>", node.nodeName()));
				} else {
					System.out.println();
					System.out.print(indent + String.format("</%s>", ((Element) node).tagName()));	
				}

				if (indent.isEmpty()) {
					System.out.println(System.getProperty("line.separator"));
				}

			}
		}
	}

	/**
	 * Show the contents of a node in the format of Google Chrome inspect elements console
	 * with default initial indent "" and indentUnit "  ".
	 * 
	 * @param node a {@code Node} whose contents we are going to show.
	 */
	public static void showNode(Node node) {
		String indent = "";
		String indentUnit = "  ";
		showNode(node, indent, indentUnit);
	}

	/**
	 * Write the string representation of a node in the format of Google Chrome inspect elements console
	 * into a {@code StringBuffer}.
	 * 
	 * @param node a {@code Node} whose contents we are going to show.
	 * @param indent a {@code String} indicating the indent string in the current level.
	 * @param indentUnit an incremental string when showing the contents in the next level.
	 */
	public static StringBuffer showNodeByString(Node node, String indent, String indentUnit) {

		StringBuffer res = new StringBuffer("");
		if (node.getClass().getSimpleName().compareTo("TextNode") == 0) {
			if (!((TextNode) node).isBlank()) {
				if (node.parent().childNodes().size() > 1 
						|| ((node.parent().nodeName().compareTo("p") == 0 ) 
								&& ((TextNode) node).text().length() > 40)) {
					res.append(System.getProperty("line.separator"));
					//System.out.println();
					res.append(indent);
					//System.out.print(indent);
					res.append(((TextNode) node).text());
					//res.append("\"" + ((TextNode) node).text() + "\"");
					//System.out.print("\"" + ((TextNode) node).text() + "\"");
				} else {
					res.append(((TextNode) node).text());
					//System.out.print(((TextNode) node).text());
				}
			}
			return res;
		}

		/*
		 * script Node has DataNode
		 */
		if (node.getClass().getSimpleName().compareTo("DataNode") == 0) {
			res.append(((DataNode) node).getWholeData());
			//System.out.print(((DataNode) node).getWholeData());
			return res;
		}

		if (node.getClass().getSimpleName().compareTo("Comment") == 0) {

			res.append(System.getProperty("line.separator")
					+ indent
					+ "<!--"
					+ ((Comment) node).getData()
					+ "-->");
			/*System.out.println();
			System.out.print(indent);
			System.out.print("<!--");
			System.out.print(((Comment) node).getData());
			System.out.print("-->");*/
			return res;
		}

		// For debugging
		/*if (node.nodeName().compareTo("p") == 0) {
			int a = 1;
			a = a + 1;
		}*/

		res.append(System.getProperty("line.separator")
				+ indent);
		/*System.out.println();
		System.out.print(indent);*/

		if (node.getClass().getSimpleName().compareTo("Element") == 0 
				|| node.getClass().getSuperclass().getSimpleName().compareTo("Element") == 0) {

			/*Document.nodeName
			@Override
		    public String nodeName() {
		        return "#document";
		    }*/

			/*Element.nodeName
			@Override
		    public String nodeName() {
		        return tag.getName();
		    }*/

			res.append("<" + ((Element) node).tagName());
			//System.out.print("<" + ((Element) node).tagName());
			Attributes attributes = node.attributes();
			for (Attribute attribute : attributes) {
				res.append(String.format(" %s = \"%s\"",
						attribute.getKey(), node.attr(attribute.getKey())));
				/*System.out.print(String.format(" %s = \"%s\"",
						attribute.getKey(), node.attr(attribute.getKey())));*/
			}
			res.append(">");
			//System.out.print(">");

			List<Node> childNodeList = node.childNodes();			 
			for (Node subNode : childNodeList) {
				res.append(showNodeByString(subNode, indent + indentUnit, indentUnit));
			}			

			if (!((Element) node).tag().isSelfClosing()) {

				if (childNodeList.size() == 0 
						|| (childNodeList.size() == 1 && node.nodeName().compareTo("p") != 0 
								&& childNodeList.get(0).getClass().getSimpleName().compareTo("TextNode") == 0)
								|| ((node.nodeName().compareTo("p") == 0)
										&& ((Element) node).text().length() <= 40)) {
					res.append(String.format("</%s>", node.nodeName()));
					//System.out.print(String.format("</%s>", node.nodeName()));
				} else {
					res.append(System.getProperty("line.separator")
							+ indent + String.format("</%s>", ((Element) node).tagName()));	
					/*System.out.println();
					System.out.print(indent + String.format("</%s>", ((Element) node).tagName()));*/	
				}

				if (indent.isEmpty()) {
					res.append(System.getProperty("line.separator"));
					res.append(System.getProperty("line.separator"));
					//System.out.println(System.getProperty("line.separator"));
				}

			}
		}

		return res;

	}
	
	public void saveImages() {

		InputStream is = null;

		try {
			String link = "http://i2.cdn.turner.com/cnn/dam/assets/120111063248-us-drone-story-top.jpg";
			String link2 = "http://a57.foxnews.com/global.fncstatic.com/static/managed/img/fn-latino/politics/660/371/obama romney new bt.jpg";
			System.out.println(link2);
			link2 = "";
			link2 = link2.replace(" ", "%20");
			System.out.println(link2);
			URL url = new URL(link2);
			try {
				is = url.openStream();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			String extension = link.substring(link.lastIndexOf('.') + 1);
			String imgName = "Test." + extension;

			DataOutputStream binaryOut = null;
			try {
				binaryOut = new DataOutputStream(// Write data into a byte output stream 
						new BufferedOutputStream(// Use a buffer to store an output stream 
								new FileOutputStream(imgName)));// Write an output stream into a file

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

			try {
				byte b[] = new byte[1000];
				int numRead = 0;
				while ((numRead = is.read(b)) != -1) {
					binaryOut.write(b, 0, numRead);
				}
				binaryOut.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

	}

}
