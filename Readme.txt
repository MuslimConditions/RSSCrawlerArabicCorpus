RSSFeedCrawler is a crawler for multiple RSS feed sites written in Java. Both text and images could be scraped via HTML parsing.

Description:
CSS selector expression is used to specify the DOM locations for the text and image path.
SHA256 is used instead of MD5 to digest URLs.

Configurations:
a. All the parameters for the crawler are initialized from a file named sys_conf.txt. The sys_conf.txt specifies
	1. The saving path for the crawled data
	2. File path of an XML file containing the URLs of the RSS sites and XPath for its text and image content
	3. Source of the news
	4. URL of the source
	5. Part of code of the line containing the title in related links
	6. Part of code of the line containing the domain in related links
	7. Language name defined in crawl-sites.xml
	8. Username for mysql database
	9. Password for mysql database
b. An XML file should be provided to specify the feed channels and the CSS selector syntax for the text and image content in a DOM tree.

Dependencies:

jsoup-*.*.*.jar
mysql-connector-java-*.*.**-bin.jar

We used the project "https://github.com/MingjieQian/RSSFeedCrawler" as a basis for our project and we modified it according to our need for data.
