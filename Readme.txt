RSSFeedCrawler is a crawler for multiple RSS feed sites written in Java. Both text and images could be scraped via HTML parsing.

Description:
CSS selector expression is used to specify the DOM locations for the text and image path.
SHA256 is used instead of MD5 to digest URLs.

Usage:
java -jar path_of_RSSFeedCrawler.jar -sys_conf path_of_sys_conf.txt

e.g.,
java -jar /home/czhai/mqian2/Toolbox/Java/RSSFeedCrawler/RSSFeedCrawler.jar -sys_conf /home/czhai/mqian2/Toolbox/Java/RSSFeedCrawler/sys_conf.txt

If you want to clean up the old database and create a new database before crawling, please use the following command:
java -jar /home/czhai/mqian2/Toolbox/Java/RSSFeedCrawler/RSSFeedCrawler.jar -sys_conf /home/czhai/mqian2/Toolbox/Java/RSSFeedCrawler/sys_conf.txt -db_clean_up

Configurations:
a. All the parameters for the crawler are initialized from a file named sys_conf.txt. The sys_conf.txt specifies
	1. The saving path for the crawled data
	2. File path of an XML file containing the URLs of the RSS sites and XPath for its text and image content
	3. Username for mysql database
	4. Password for mysql database
b. An XML file should be provided to specify the feed channels and the CSS selector syntax for the text and image content in a DOM tree.

e.g.,
data_dir = ***/RSSFeedCrawler/data
crawl_conf = ***/RSSFeedCrawler/crawl-sites.xml
db_user = root
db_pass = 1234

Dependencies:

jsoup-*.*.*.jar
mysql-connector-java-*.*.**-bin.jar

-----------------------------------
Author: Mingjie Qian
Version: 1.0
Date: Dec. 18th, 2012