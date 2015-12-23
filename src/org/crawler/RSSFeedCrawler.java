package org.crawler;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// import org.crawler.md5.MD5;
import static org.crawler.sha.SHA2.toSHA2;
// import static org.crawler.support.DOMScreen.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/***
 * This is a web crawler for RSS feeds.
 * 
 * CSS selector expression is used to specify the DOM locations
 * for the text and image path.
 * 
 * A crawl-sites.xml file should be provided to specify the feed
 * channels and the CSS selector syntax for the text and image content
 * in a DOM tree.
 * 
 * @author Mingjie Qian
 * @version 1.0, Dec. 18th, 2012
 */
public class RSSFeedCrawler {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length < 2) {
			showUsage();
		}

		RSSFeedCrawler crawler = new RSSFeedCrawler();

		crawler.readSystemConfig(args);
		
		crawler.crawl();
		
	}

	public TreeMap<String, HashMap<String, String>> RSSFeedMap;

	public String dataDir;

	public String crawlConf;

	public String sysConf;

	public Properties options;

	public Connection DBConn;
	
	public String DBUser;
	
	public String DBPass;
	
	public boolean DBCleanUp;
	
	public String srcDatePattern;
	
	public String desDatePattern;

	public RSSFeedCrawler() {
		
		System.out.println("# ***************** #");
		System.out.println(getClass().getSimpleName());
		System.out.println("Author: Mingjie Qian");
		System.out.println("# ***************** #");
		System.out.println("     (^)     (^)");
		System.out.println("          |");
		System.out.println("        _____");
		System.out.println("");
		System.out.println("# ***************** #");
		
		System.out.println("Initializing " + 
				   getClass().getSimpleName() + "...");
		
		RSSFeedMap = new TreeMap<String, HashMap<String, String>>();
		dataDir = "";
		crawlConf = "";
		sysConf = "";
		options = new Properties();
		DBConn = null;
		DBUser = "";
		DBPass = "";
		DBCleanUp = false;
		srcDatePattern = "EEE, d MMM yyyy HH:mm:ss z";
		desDatePattern = "yyyyMMddHHmmssz";
		
		System.out.println("Current time: " +
						   new SimpleDateFormat(srcDatePattern)
							   .format(Calendar.getInstance().getTime()));
		
	}
	
	private static void showUsage() {
		System.out.println("Usage: java -jar $path/RSSFeedCrawler.jar -sys_conf <conf-file> [-db_clean_up]");
		System.exit(1);
	}
	
	private void configureDatabase() {

		try
		{
			String url = "jdbc:mysql://localhost/";
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			DBConn = DriverManager.getConnection(url, DBUser, DBPass);
			System.out.println("Database connection established.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.err.println("Cannot connect to database server!");
			System.exit(1);
		}
		
		Statement s;
		try {
			s = DBConn.createStatement();
			if (this.DBCleanUp) {
				s.executeUpdate("DROP DATABASE IF EXISTS RSSFeedCrawler;");
				System.out.println("Old database dropped.");
			}
			s.executeUpdate("CREATE DATABASE IF NOT EXISTS RSSFeedCrawler;");
			s.executeUpdate("USE RSSFeedCrawler;");
			for(String channelName : RSSFeedMap.keySet()) {
				s.executeUpdate(String.format(
						"CREATE TABLE IF NOT EXISTS %s (", channelName)
						+ "id INT(10) UNSIGNED NOT NULL AUTO_INCREMENT, "
						+ "goodPage BOOLEAN NOT NULL DEFAULT 1, "
						+ "SHA2 CHAR(64) NOT NULL, "
						+ "PRIMARY KEY (id), "
						+ "KEY SHA2_idx (SHA2));"
						);
			}
			s.close();
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
	private void disconnectDatabase() {
		
		try
		{
			DBConn.close ();
			System.out.println("Database connection terminated.");
		}
		catch (Exception e) {  /* ignore close errors */  }
		
	}

	public void readSystemConfig(String[] args) {
		
		/*
		 * Arguments: -sys_conf sys_conf.txt
		 */
		if ((args.length >= 2) && args[0].toLowerCase().equals("-sys_conf")) {
			try {
				
				/*
				 * Properties Class instance should be constructed first 
				 * before use
				 */
				options.load(new FileInputStream(args[1]));
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				System.exit(1);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if ((args.length == 3)&& args[2].toLowerCase().equals("-db_clean_up")) {
				this.DBCleanUp = true;
			}
		} else {
			RSSFeedCrawler.showUsage();
		}

		// Get the path to the executable
		String appPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		String appDir = new File(appPath).getParent();
		String dataDir = appDir + File.separator + "data";
		this.dataDir = options.getProperty("data_dir", dataDir).trim();;
		
		this.crawlConf = options.getProperty("crawl_conf").trim();
		
		this.DBUser = options.getProperty("db_user").trim();
		this.DBPass = options.getProperty("db_pass").trim();
		
		buildRSSFeedMap();

	}

	public void buildRSSFeedMap() {

		Document confDOM = null;

		String baseURL = "";
		String charSet = null;
		int flag = 1;
		switch (flag) {
		case 1:
			charSet = "UTF-8";
			break;
		case 2:
			charSet = "ISO-8859-1";
			break;
		default:
			charSet = null;
		}

		try {
			
			if (this.crawlConf != null) {
				confDOM = Jsoup.parse(new File(this.crawlConf), charSet, baseURL);
			} else {
				try {
					confDOM = Jsoup.parse(getClass().getResourceAsStream("./conf/crawl_conf"), 
							charSet, baseURL);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					System.exit(1);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}

		} catch (IOException e1) {
			e1.printStackTrace();
		}

		Elements channelElements = confDOM.select("language[name = english] > channel");

		for (Element channelElement : channelElements) {

			HashMap<String, String> channelMap = new HashMap<String, String>();

			String channelName = channelElement.attr("name");
			String channelURL = channelElement.select("url").text();
			String textXPath = channelElement.select("xpath").text();
			String imgXPath = channelElement.select("img_xpath").text();

			channelMap.put("url", channelURL);
			channelMap.put("xpath", textXPath);
			channelMap.put("img_xpath", imgXPath);

			RSSFeedMap.put(channelName, channelMap);

		}

	}

	private String crawlURL(String HTMLURL, String channelName) {

		BufferedReader br = null;
		String line= "";
		StringBuilder sbuilder = new StringBuilder(100);

		String charSet = RSSFeedMap.get(channelName).get("encoding");

		try {

			URL url = new URL(HTMLURL);
			InputStream is = null;
			try {
				is = url.openStream();
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println(String.format("Cannot open %s!", HTMLURL));
				return "";
			}

			try {
				br = new BufferedReader(new InputStreamReader(is, charSet));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				System.err.format("%s is unsupported, use UTF-8 instead.", charSet);
				System.out.println();
				try {
					br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				}
			}

			try {
				while ((line = br.readLine()) != null) {
					sbuilder.append(line);
					sbuilder.append(System.getProperty("line.separator"));
				}

				br.close();

			} catch (IOException e) {
				e.printStackTrace();
				System.err.println(String.format("Cannot read %s!", HTMLURL));
				// System.exit(1);
				return "";
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return sbuilder.toString();

	}

	private void crawlChannel(String channelName) {

		String channelURL = RSSFeedMap.get(channelName).get("url");
		String charSet = "UTF-8";
		String baseURL = "";
		Document RSSDOM = null;
		// TimeZone srcTimeZone = null;
		SimpleDateFormat srcSimpleDateFormat = new SimpleDateFormat(srcDatePattern);
		SimpleDateFormat desSimpleDateFormat = new SimpleDateFormat(desDatePattern);
		String titlePubDate = "";
		
		String channelDir = this.dataDir + File.separator + channelName;
		if (!new File(channelDir).exists()) {
			new File(channelDir).mkdirs();
		}

		/*if (channelName.equals("foxnews-world") || channelName.equals("cnn-top")) {
			int a = 1;
			a = a + 1;
		}*/

		try {

			baseURL = channelURL;
			URL url = new URL(baseURL);
			RSSDOM = Jsoup.parse(url.openStream(), charSet, baseURL);
			Node commentNode = RSSDOM.childNode(0);
			if (commentNode.getClass().getSimpleName().compareTo("Comment") == 0) {
				String commentContent = ((Comment)commentNode).getData();
				Pattern charsetPattern = Pattern.compile("encoding\\s*=\\s*\"([-\\w0-9]+)\"");
				Matcher charsetMatcher = charsetPattern.matcher(commentContent);
				if (charsetMatcher.find()) {
					charSet = charsetMatcher.group(1).toUpperCase();
				}
			}
			this.RSSFeedMap.get(channelName).put("encoding", charSet);
			
			// showNode(RSSDOM.body().child(0));
			Elements itemElements = RSSDOM.select("item");
			// HashSet<String> crawledURLSet = crawledChannelURLSetMap.get(channelName);
			int docID = 0;
			Statement s;
			try {
				s = DBConn.createStatement();
				ResultSet rs = s.executeQuery(String.format("SELECT COUNT(*) FROM %s WHERE goodPage = 1;", channelName));
				while (rs.next()) {
					docID = rs.getInt(1);
				}
				s.close();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}		
			
			// int docID = crawledURLSet.size();
			// HashSet<String> newURLSet = new HashSet<String>();
			for (Element itemElement : itemElements) {

				String title = itemElement.select("title").text();
				String description = itemElement.select("description").first().ownText();

				Document desDOM = Jsoup.parse(description);
				/*showNode(desDOM);
				System.out.println(desDOM.body().ownText());*/
				Element desElement = desDOM.body();
				if (desElement.getElementsByTag("p").isEmpty()) {
					description = desElement.ownText();
				} else {
					description = desElement.getElementsByTag("p").first().ownText();
				}

				// showNode(itemElement.select("description").first());

				// description = Jsoup.parse(description).body().ownText();
				String pubDate = itemElement.select("pubDate").text();
				if (!pubDate.isEmpty()) {
					/*if (srcTimeZone == null) {
						try {
							srcSimpleDateFormate.parse(pubDate);
							srcTimeZone = srcSimpleDateFormate.getTimeZone();
							desSimpleDateFormate.setTimeZone(srcTimeZone);
						} catch (ParseException e1) {
							e1.printStackTrace();
						}
					}*/
					try {
						titlePubDate = desSimpleDateFormat.format(srcSimpleDateFormat.parse(pubDate));
					} catch (ParseException e) {
						
						e.printStackTrace();
						
						String[] datePatternStrings = {"[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2} [0-9]{2,2}:[0-9]{2,2}:[0-9]{2,2}"};
						String[] dateFormatStrings = {"yyyy-MM-dd HH:mm:ss"};
						
						Pattern[] patterns = new Pattern[datePatternStrings.length];
						for (int i = 0; i< datePatternStrings.length; i++) {
							patterns[i] = Pattern.compile(datePatternStrings[i]);
						}
						Matcher matcher = null;
						
						for (int i = 0; i < patterns.length; i++) {
							matcher = patterns[i].matcher(pubDate);
							if (matcher.find()) {
								try {
									titlePubDate = desSimpleDateFormat.format(new SimpleDateFormat(dateFormatStrings[i]).parse(pubDate));
									break;
								} catch (ParseException e1) {
									e1.printStackTrace();
									continue;
								}
							} else {
								titlePubDate = "Unrecognized";
							}
						}
						
					}
				} else {
					titlePubDate = "";
				}
				
				/*
				 * guid: Stands for Globally Unique Identifier.
				 * A guid could be a URL pointing to the RSS item content, or not.
				 * It has an attribute called isPermaLink, which is optional. 
				 * If set to true, the reader may assume that it is a permalink 
				 * to the item (a url that points to the full item described by 
				 * the <item> element). The default value is true. If set to false, 
				 * the guid may not be assumed to be a URL.
				 */

				/*String linkURL = itemElement.select("guid").text();
				if (itemElement.select("guid").attr("isPermaLink").equalsIgnoreCase("false")) {
					linkURL = itemElement.select("link").text();
				}*/

				String linkURL = itemElement.select("link").text();
				if (itemElement.select("link").first().tag().isSelfClosing()) {
					/*linkURL = itemElement.html();
					Element el = itemElement.select("link").first().nextElementSibling();
					String str = itemElement.select("link").first().outerHtml();
					Element ell = itemElement.select("link").first().parent();
					Document linkDOM = Jsoup.parse(itemElement.html());*/
					Node node = itemElement.select("link").first().nextSibling();
					if (node.getClass().getSimpleName().compareTo("TextNode") == 0) {
						linkURL = ((TextNode) node).text();
					}

					// linkURL = itemElement.select("item").first().ownText();
				}
				// String linkURL = itemElement.select("guid").text();
				// String linkURL = itemElement.select("link").text();

				if (linkURL.isEmpty()) {
					continue;
				}

				if (linkURL.indexOf("http") == -1) {
					linkURL = itemElement.baseUri() + linkURL;
				}
				
				String SHA2 = toSHA2(linkURL);
				
				try {
					
					s = DBConn.createStatement();
					ResultSet result = s.executeQuery(String.format("SELECT id, goodPage FROM %s WHERE SHA2 = '%s';", channelName, SHA2));
					
					String quality = "";
					if (result.next()) {
						boolean goodPage = result.getBoolean(2);
						if (goodPage) {
							quality = "good";
						} else {
							quality = "bad";
						}
						System.out.println(String.format("Find an already-processed %s link: %s", quality, linkURL));
						continue;
					}
					s.close();		
					
				} catch (SQLException e) {
					e.printStackTrace();
					continue;
				}
				
				boolean ifSucceed = false;

				System.out.println("Find a new link: " + linkURL);
				System.out.println("Crawling... " + linkURL);
				
				
				String HTMLContent = crawlURL(linkURL, channelName);
				
				if (HTMLContent.isEmpty()) {
					continue;
				}
				
				Document docDOM = null;
				try {
					docDOM = Jsoup.parse(HTMLContent);
				} catch (Exception e) {
					System.err.println(e);
					continue;
				}
				
				/* To call showNode without parent class, please use
				 * static import.
				 */
				// showNode(docDOM);
				// System.out.println(HTMLContent);
				docID++;
				
				String fileName = String.format("%s-%08d-%s.html", channelName, docID, titlePubDate);
				String filePath = channelDir + File.separator + fileName;
				ifSucceed = saveHTMLContent(filePath, docID, title, description, pubDate, linkURL, docDOM, channelName, HTMLContent);

				if (!ifSucceed)
		            docID -= 1;
		            
				try {
					PreparedStatement ps;
					ps = DBConn.prepareStatement(String.format(
								"INSERT INTO %s (SHA2, goodPage) VALUES(?, ?)", channelName));
					ps.setString(1, SHA2);
					ps.setBoolean(2, ifSucceed);
					ps.executeUpdate();
					ps.close();
				} catch (SQLException e) {
					e.printStackTrace();
					continue;
				}

			}

			// this.newChannelURLSetMap.put(channelName, newURLSet);

		}catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void crawl() {

		configureDatabase();
		
		for(String channelName : RSSFeedMap.keySet()) {
			/*if (!channelName.equals("foxnews_politics"))
				continue;*/
			crawlChannel(channelName);
		}
		
		disconnectDatabase();

		System.out.println("Mission Complete!");

	}

	/**
	 * Encoding should be automatically detected.
	 * 
	 * @param filePath
	 * @param title
	 * @param description
	 * @param pubDate
	 * @param linkURL
	 * @param docDOM
	 * @param channelName
	 */
	private boolean saveHTMLContent(String filePath, int docID, String title, String description,
			String pubDate, String linkURL, Document docDOM, String channelName, String HTMLContent) {

		String textXPath = RSSFeedMap.get(channelName).get("xpath");
		// String text = "";
		String imgXPath = RSSFeedMap.get(channelName).get("img_xpath");

		String charSet = RSSFeedMap.get(channelName).get("encoding");

		String contentFilePath = filePath.substring(0, filePath.lastIndexOf('.')) + ".txt";

		try {
			
			PrintWriter printer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(contentFilePath), "UTF-8")));

			printer.println("<DOC>");
			printer.println(String.format("<DOCNO>%d</DOCNO>", docID));
			printer.println(String.format("<URL>%s</URL>", linkURL));
			printer.println(String.format("<TITLE>%s</TITLE>", title));
			printer.println(String.format("<TIME>%s</TIME>", pubDate));
			printer.println("<ABSTRACT>");
			printer.println(description);
			printer.println("</ABSTRACT>");
			printer.println("<TEXT>");

			/**
			 * The textXPath CSS selector syntax should be accurate enough
			 * to specify the location of element having text contents.
			 */
			Elements elements = docDOM.select(textXPath);

			// StringBuilder sbuilder = new StringBuilder(100);
			/*for (Element element : elements) {
				printer.println(element.text());	
			}*/

			/**
			 * The following code has a logic error. If the first text paragraph
			 * doesn't have the same parent with the other text paragraphs we 
			 * really want, printTextContent() will ignore the other text 
			 * paragraph we need.
			 */
			/*if (!elements.isEmpty()) {
				Element parent = elements.first().parent();
				printTextContent(parent, printer);
				printer.println("</TEXT>");
				printer.println("</DOC>");
				printer.close();
			} else {
				printer.close();
				System.err.println("Empty content!");
				new File(contentFilePath).delete();
				return false;
			}*/
			
			/**
			 * We will simply extract the text contents from every element
			 * of elements. Thus the key is to build good CSS selector
			 * syntax for the text paragraphs we really want.
			 */
			if (!elements.isEmpty()) {
				printTextContent(elements, printer);
				printer.println("</TEXT>");
				printer.println("</DOC>");
				printer.close();
			} else {
				printer.close();
				System.err.println("Empty content!");
				new File(contentFilePath).delete();
				return false;
			}

		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			// System.exit(1);
			return false;
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			// System.exit(1);
			return false;
		}
		
		String HTMLFilePath = filePath;

		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(HTMLFilePath), charSet));
			try {
				// writer.write(showNodeByString(docDOM.select("html").first(), "", "  ").toString());
				writer.write(HTMLContent);
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
				return true;
			}
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			System.exit(1);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			System.exit(1);
		}

		if (imgXPath.isEmpty()) {
			return true;
		}
		
		/* 
		 * We need to replace any white space with %20
		 * in order for URL.openStream to work.
		 */
		String imgLink = docDOM.select(imgXPath).attr("src").replace(" ", "%20");
		
		/*if (title.equals("Blizzard pounds upper Midwest")) {
			int a = 1;
			a = a + 1;
		}*/
		
		if (channelName.contains("cnn") && (imgLink.isEmpty() || imgLink.endsWith(".gif"))) {
			
			boolean imgLinkFound = false;
			imgLink = "";
			Elements elements = docDOM.select(imgXPath.substring(0, imgXPath.lastIndexOf(" img[src]")));
			List<Node> childNodeList = elements.first().childNodes();		 
			for (Node subNode : childNodeList) {
				if (subNode.getClass().getSimpleName().compareTo("Element") == 0) {
					for (Node subSubNode : subNode.childNodes()) {
						if (subSubNode.getClass().getSimpleName().compareTo("DataNode") == 0) {
							String script = ((DataNode) subSubNode).getWholeData();
							int endIdx = script.lastIndexOf(".jpg");
							if (endIdx == -1) {
								continue;
							}
							int startIdx = script.lastIndexOf("http", endIdx);
							if (startIdx != -1) {
								imgLink = script.substring(startIdx, endIdx + 4);
								imgLinkFound = true;
								break;
							}
						}
					}
					if (imgLinkFound)
						break;
					// showNode(subNode);
					// System.out.println(((DataNode) subNode).getWholeData());
				} /*else if (subNode.getClass().getSimpleName().compareTo("DataNode") == 0) {
					String script = ((DataNode) subNode).getWholeData();
					int endIdx = script.lastIndexOf(".jpg");
					if (endIdx == -1) {
						continue;
					}
					int startIdx = script.lastIndexOf("http", endIdx);
					if (startIdx != -1) {
						imgLink = script.substring(startIdx, endIdx + 4);
						imgLinkFound = true;
						break;
					}
				}*/
			}
			
			/*if (!imgLinkFound)
				return true;*/
			
		}
		
		if (imgLink.isEmpty()) {
			return true;
		}
		
		// Get the extension of the image file
		String extension = imgLink.substring(imgLink.lastIndexOf('.'));
		if (extension.indexOf('?') != -1) {
			extension = extension.substring(0, extension.indexOf('?'));
		}
		
		if (extension.equals(".gif")) {
			return true;
		}
		
		// The original imgLink may not include the extension
		if (imgLink.lastIndexOf('.') < imgLink.length() / 2) {
			extension = ".jpg";
		}
		
		/* Sometimes there are additional characters appending 
		 * the extension.
		 */
		if (extension.length() > 5) {
			char c = extension.charAt(4);
			if ('a' <= c && c <= 'z' || 'A' <= c && c <= 'Z') {
				extension = extension.substring(0, 5);
			} else {
				extension = extension.substring(0, 4);
			}
		}
		
		String imgFilePath = filePath.substring(0, filePath.lastIndexOf('.')) + extension;
		
		InputStream is = null;

		try {

			URL url = new URL(imgLink);
			try {
				is = url.openStream();
			} catch (IOException e) {
				e.printStackTrace();
				return true;
			}	

			DataOutputStream binaryOut = null;
			try {
				binaryOut = new DataOutputStream(// Write data into a byte output stream 
						new BufferedOutputStream(// Use a buffer to store an output stream 
								new FileOutputStream(imgFilePath)));// Write an output stream into a file

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
				return true;
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return true;
		}
		
		return true;

	}
	
	/**
	 * Extract the text contents from every element of an element list.
	 * 
	 * @param elements an element list composed of all elements satisfying
	 * 			       a CSS selector syntax
	 * 
	 * @param printer a {@code PrintWriter} object to save the text contents
	 *                  to
	 */
	private void printTextContent(Elements elements, PrintWriter printer) {
		
		for (Element element : elements) {
			String content = element.text();
			if (!content.isEmpty())
				printer.println(content);
		}
		
	}

	@SuppressWarnings("unused")
	private void printTextContent(Node node, PrintWriter printer) {
		
		if (node == null) {
			return;
		}
		
		boolean isContentNode = false;
		if (node instanceof Element) {
			Element element = (Element) node;
			if (element.tagName().equals("p")) {
				printer.println(element.text());
				isContentNode = true;
				// System.out.println(element.text());
			}
		} else if (node instanceof TextNode) {
			TextNode textNode = (TextNode) node;
			isContentNode = true;
			if (!textNode.isBlank()) {
				printer.println(textNode.text());
				// System.out.println(textNode.text());
			}
		}
		
		if (isContentNode) {
			return;
		}
		
		for (Node child : node.childNodes()) {
			printTextContent(child, printer);
		}

	}
	
	@SuppressWarnings("unused")
	private void printTextContent2(Node parent, PrintWriter printer) {
		if (parent != null) {
			for (Node child : parent.childNodes()) {
				if (child instanceof Element) {
					Element element = (Element) child;
					if (element.tagName().equals("p")) {
						printer.println(element.text());
						// System.out.println(element.text());
					}
				} else if (child instanceof TextNode) {
					TextNode textNode = (TextNode) child;
					if (!textNode.isBlank()) {
						printer.println(textNode.text());
						// System.out.println(textNode.text());
					}
				}
			}
		}
	}

}
