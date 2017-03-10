*RSSCrawlerArabicCorpus* is a crawler for multiple RSS feed sites written in Java. Both text and images could be scraped via HTML parsing from different arabic news websites.

# Description:
CSS selector expression is used to specify the DOM locations for the text and image path.

SHA256 is used instead of MD5 to digest URLs.
# How to use:
-Create a new mysql dataBase and write its name in the "sys_conf.txt" file.
-Edit the configurations in the file "sys_conf.txt" to adapt your project
-Run the project
# Configurations:
* All the parameters for the crawler are initialized from a file named sys_conf.txt. The sys_conf.txt specifies
	1. The saving path for the crawled data
	2. File path of an XML file containing the URLs of the RSS sites and XPath for its text and image content
	3. Source of the news
	4. URL of the source
	5. Part of code of the line containing the title in related links
	6. Part of code of the line containing the domain in related links
	7. Language name defined in crawl-sites.xml
	8. DataBase name
	9. Username for mysql database
       10. Password for mysql database
* An XML file should be provided to specify the feed channels and the CSS selector syntax for the text and image content in a DOM tree.

# Configured websites sources
- [x] JawharaFM

# Dependencies:
```java
jsoup-*.*.*.jar
mysql-connector-java-*.*.**-bin.jar
```
## Credits
We used the project "https://github.com/MingjieQian/RSSFeedCrawler" as a basis for our project and we modified it according to our need for data.

---
![Status](https://img.shields.io/badge/status-beta-orange.svg)
![Language](https://img.shields.io/badge/language-Java-brightgreen.svg)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
