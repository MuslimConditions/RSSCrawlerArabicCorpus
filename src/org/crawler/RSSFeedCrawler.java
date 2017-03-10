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
import static java.net.URLEncoder.encode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.crawler.sha.SHA2.toSHA2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

public class RSSFeedCrawler {

    /**
     * @param args
     */
    public static void main(String[] args) {

        String arg1 = "-sys_conf";
        String arg2 = "sys_conf.txt";
        String[] args1 = {arg1, arg2};
        RSSFeedCrawler crawler = new RSSFeedCrawler();

        crawler.readSystemConfig(args1);
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

    public String srcUrl;

    public boolean DBCleanUp;

    public String srcDatePattern;

    public String desDatePattern;

    public List<String> relations = new ArrayList<>();

    public List<String> relationsDate = new ArrayList<>();

    public List<String> relationsDomain = new ArrayList<>();

    public List<String> relationsTitle = new ArrayList<>();

    public String links = "";
    
    public String domainLine = "";
    
    public String titleLine = "";
    
    public String languageName = "";
    
    public String dbName = "";

    String initialChannelName = "";

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

        System.out.println("Initializing "
                + getClass().getSimpleName() + "...");

        RSSFeedMap = new TreeMap<String, HashMap<String, String>>();
        dataDir = "";
        crawlConf = "";
        sysConf = "";
        options = new Properties();
        DBConn = null;
        DBUser = "";
        DBPass = "";
        srcUrl = "";
        DBCleanUp = false;
        srcDatePattern = "EEE, d MMM yyyy HH:mm:ss z";
        desDatePattern = "yyyyMMddHHmmssz";

        System.out.println("Current time: "
                + new SimpleDateFormat(srcDatePattern)
                .format(Calendar.getInstance().getTime()));

    }

    private static void showUsage() {
        System.out.println("Usage: java -jar $path/RSSFeedCrawler.jar -sys_conf <conf-file> [-db_clean_up]");
        System.exit(1);
    }

    private void configureDatabase() {

        try {
            String url = "jdbc:mysql://localhost:3306/";
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            DBConn = DriverManager.getConnection(url, DBUser, DBPass);

            System.out.println("Database connection established.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Cannot connect to database server!");
            System.exit(1);
        }

        Statement s;
        try {

            s = DBConn.createStatement();
            if (this.DBCleanUp) {
                s.executeUpdate("DROP DATABASE IF EXISTS "+dbName+";");
                System.out.println("Old database dropped.");
            }
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS "+dbName+";");
            s.executeUpdate("USE "+dbName+";");
            for (String channelName : RSSFeedMap.keySet()) {
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

        try {
            DBConn.close();
            System.out.println("Database connection terminated.");
        } catch (Exception e) {  /* ignore close errors */        }

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
            if ((args.length == 3) && args[2].toLowerCase().equals("-db_clean_up")) {
                this.DBCleanUp = true;
            }
        } else {
            RSSFeedCrawler.showUsage();
        }

        // Get the path to the executable
        String appPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        String appDir = new File(appPath).getParent();
        String dataDir = appDir + File.separator + "data";
        this.dataDir = options.getProperty("data_dir", dataDir).trim();
        this.srcUrl = options.getProperty("src_URL").trim();
        this.domainLine = options.getProperty("domain_line").trim();
        this.titleLine = options.getProperty("title_line").trim();
        this.languageName = options.getProperty("language_name").trim();
        this.crawlConf = options.getProperty("crawl_conf").trim();
        this.DBUser = options.getProperty("db_user").trim();
        this.DBPass = options.getProperty("db_pass").trim();
        this.dbName=options.getProperty("db_name").trim();

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

        // language defined in crawl-sites.xml
        Elements channelElements = confDOM.select("language[name = "+languageName+"] > channel");

        for (Element channelElement : channelElements) {

            HashMap<String, String> channelMap = new HashMap<String, String>();

            String channelName = channelElement.attr("name");
            initialChannelName = channelName;
            String channelURL = channelElement.select("url").text();
            String textXPath = channelElement.select("xpath").text();
            String imgXPath = channelElement.select("img_xpath").text();
            // String domain = channelElement.select("domain_xpath").text();
            // String href = channelElement.select("href_xpath").text();

            channelMap.put("url", channelURL);
            channelMap.put("xpath", textXPath);
            channelMap.put("img_xpath", imgXPath);
            // channelMap.put("domain_xpath", domain);
            // channelMap.put("href_xpath", href);

            RSSFeedMap.put(channelName, channelMap);

        }

    }

    private String crawlURL(String HTMLURL, String channelName, boolean relation) {

        BufferedReader br = null;
        String line = "";
        String charSet = "";
        StringBuilder sbuilder = new StringBuilder(100);
        if (relation == false) {
            charSet = RSSFeedMap.get(channelName).get("encoding");
        } else {
            charSet = "UTF-8";
        }

        try {
            //   String domain=RSSFeedMap.get(channelName).get("domain_xpath");

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

            int i = 0;
            int l = 0;
            if (relation == false) {

                relations.clear();

                relationsDate.clear();

                relationsDomain.clear();

                relationsTitle.clear();

            }
            try {
                //String theString = IOUtils.toString(is, "UTF-8");
                // String message = org.apache.commons.io.IOUtils.toString(br);
                while ((line = br.readLine()) != null) {
                    if (relation == false) {
                        // this part depends to the web site used
                        //begin 
                        if (line.contains(titleLine)) {
                            String[] link = line.split("\">");
                            String urll = link[1].substring(9);
                            relations.add(i, urll);
                            relationsTitle.add(i, link[2].substring(0, link[2].length() - 9));
                            i++;
                        }
                        if (line.contains(domainLine)) {
                            String[] s = line.split("\">");
                            String domain = s[1].substring(0, s[1].length() - 23);
                            String dat = s[2].substring(0, s[2].length() - 7);
                            relationsDate.add(l, dat);
                            relationsDomain.add(l, domain);
                            l++;
                        }
                        //end
                    }
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
        String channelDir = "";

        try {

            baseURL = channelURL;
            URL url = new URL(baseURL);
            RSSDOM = Jsoup.parse(url.openStream(), charSet, baseURL);
            Node commentNode = RSSDOM.childNode(0);
            if (commentNode.getClass().getSimpleName().compareTo("Comment") == 0) {
                String commentContent = ((Comment) commentNode).getData();
                Pattern charsetPattern = Pattern.compile("encoding\\s*=\\s*\"([-\\w0-9]+)\"");
                Matcher charsetMatcher = charsetPattern.matcher(commentContent);
                if (charsetMatcher.find()) {
                    charSet = charsetMatcher.group(1).toUpperCase();
                }
            }
            this.RSSFeedMap.get(channelName).put("encoding", charSet);

            Elements itemElements = RSSDOM.select("item");

            int docID = 0;
            Statement s;
            for (Element itemElement : itemElements) {

                String[][] encode = {{"d8a1", "ء"}, {"d8a2", "آ"}, {"d8a3", "أ"}, {"d8a4", "ؤ"}, {"d8a5", "إ"}, {"d8a6", "ئ"},
                {"d8a7", "ا"}, {"d8a8", "ب"}, {"d8a9", "ة"}, {"d8aa", "ت"}, {"d8ab", "ث"}, {"d8ac", "ج"}, {"d8ad", "ح"},
                {"d8ae", "خ"}, {"d8af", "د"}, {"d8b0", "ذ"}, {"d8b1", "ر"}, {"d8b2", "ز"}, {"d8b3", "س"}, {"d8b4", "ش"},
                {"d8b5", "ص"}, {"d8b6", "ض"}, {"d8b7", "ط"}, {"d8b8", "ظ"}, {"d8b9", "ع"}, {"d8ba", "غ"}, {"d980", "ـ"},
                {"d981", "ف"}, {"d982", "ق"}, {"d983", "ك"}, {"d984", "ل"}, {"d985", "م"}, {"d986", "ن"}, {"d987", "ه"},
                {"d988", "و"}, {"d989", "ى"}, {"d98a", "ي"}, {"d89f", "؟"}, {"d88c", "،"}, {"d88d", "؍"}, {"d89b", "؛"},
                {"d991", "ّ"}};
                int j = 0;
                int k = 1;

                links += "end of the relations to this link \n";
                channelName = initialChannelName;
                relations.clear();
                relationsDate.clear();
                relationsDomain.clear();
                relationsTitle.clear();
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
                String author = itemElement.select("author").text();

                //******************************************************
                // repeat this part in a loop : the number of links in relation to this link
                String linkURL = itemElement.select("link").text();

                if (itemElement.select("link").first().tag().isSelfClosing()) {

                    Node node = itemElement.select("link").first().nextSibling();
                    if (node.getClass().getSimpleName().compareTo("TextNode") == 0) {
                        linkURL = ((TextNode) node).text();
                    }

                }

                while (k > 0) {

                    boolean relation = true;
                    if (linkURL.isEmpty() || (linkURL.split("/")[linkURL.split("/").length - 3].length() < 1)) {
                        k--;
                        continue;
                    }

                    if (linkURL.indexOf("http") == -1) {
                        linkURL = itemElement.baseUri() + linkURL;
                    }
                    Pattern p = Pattern.compile(srcUrl + "([^=,]+)");
                    Matcher m = p.matcher(linkURL);
                    String arabic = "";
                    String linkUTF = "";
                    String linkArabic = "";
                    while (m.find()) {
                        if (m.group(1).contains("%")) {
                            arabic = m.group(1);
                            arabic = arabic.replace("%", "");
                            arabic = arabic.toLowerCase();
                            for (int i = 0; i < encode.length; i++) {
                                arabic = arabic.replace(encode[i][0], encode[i][1]);
                            }
                            linkUTF = linkURL;
                            linkArabic = srcUrl + arabic;
                        } else {
                            try {
                                linkArabic = linkURL;
                                linkUTF = srcUrl + encode(m.group(1), "UTF-8").replace("%2F", "/");
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(RSSFeedCrawler.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    String SHA2 = toSHA2(linkArabic);
                    String SHA1 = toSHA2(linkUTF);

                    try {
                        ResultSet result;
                        ResultSet result1;
                        s = DBConn.createStatement();
                        if (k < relations.size() + 1) {
                            channelName = relationsDomain.get(k - 1).trim();
                            //this part depends to the web site used: the domain names 
                            switch (channelName) {
                                case "وطنية": {
                                    channelName = "localNews";
                                    break;
                                }
                                case "سياسة": {
                                    channelName = "politic";
                                    break;
                                }
                                case "اقتصاد": {
                                    channelName = "economy";
                                    break;
                                }
                                case "تّكنولوجيا": {
                                    channelName = "technology";
                                    break;
                                }
                                case "ثقافة": {
                                    channelName = "culture";
                                    break;
                                }
                                case "رياضة": {
                                    channelName = "sport";
                                    break;
                                }
                                case "دولية": {
                                    channelName = "internationalNews";
                                    break;
                                }
                                case "متفرقات": {
                                    channelName = "diverse";
                                    break;
                                }
                                case "مجتمع": {
                                    channelName = "society";
                                    break;
                                }

                            }
                        }
                        channelDir = this.dataDir + File.separator + channelName;

                        result = s.executeQuery(String.format("SELECT id, goodPage FROM %s WHERE SHA2 = '%s';", channelName, SHA2));
                        int id = 0;
                        String quality = "";
                        if (result.next()) {
                            
                            boolean goodPage = result.getBoolean(2);

                            if (goodPage) {
                                quality = "good";
                                if (k < relations.size() + 1) {
                                    id = result.getInt(1);
                                    links += relationsDomain.get(k - 1).trim() + "-" + id + " ";

                                }
                            } else {
                                quality = "bad";
                            }
                            System.out.println(String.format("Find an already-processed %s link: %s", quality, linkURL));
                            k--;
                            if (k < relations.size() + 1 && k > 0) {
                                j = 1;
                                linkURL = relations.get(k - 1).trim();
                                pubDate = relationsDate.get(k - 1).trim();
                                description = "";
                                title = relationsTitle.get(k - 1).trim();
                            }
                            continue;
                        }
                        result1 = s.executeQuery(String.format("SELECT id, goodPage FROM %s WHERE SHA2 = '%s';", channelName, SHA1));

                        if (result1.next()) {
                            boolean goodPage1 = result1.getBoolean(2);

                            if (goodPage1) {
                                quality = "good";
                                if (k < relations.size() + 1) {
                                    id = result1.getInt(1);
                                    links += relationsDomain.get(k - 1).trim() + "-" + id + " ";

                                }
                            } else {
                                quality = "bad";
                            }
                            System.out.println(String.format("Find an already-processed %s link: %s", quality, linkURL));
                            k--;
                            if (k < relations.size() + 1 && k > 0) {
                                j = 1;
                                linkURL = relations.get(k - 1).trim();
                                pubDate = relationsDate.get(k - 1).trim();
                                description = "";
                                title = relationsTitle.get(k - 1).trim();
                            }
                            continue;
                        }
                        s.close();

                    } catch (SQLException e) {
                        k--;
                        if (k < relations.size() + 1 && k > 0) {
                            j = 1;
                            linkURL = relations.get(k - 1).trim();
                            pubDate = relationsDate.get(k - 1).trim();
                            description = "";
                            title = relationsTitle.get(k - 1).trim();
                        }
                        e.printStackTrace();
                        continue;
                    }

                    boolean ifSucceed = false;

                    System.out.println("Find a new link: " + linkURL);
                    System.out.println("Crawling... " + linkURL);
                    String HTMLContent;
                    if (j == 1) {
                        relation = true;
                        HTMLContent = crawlURL(linkURL, channelName, relation);
                    } else {
                        relation = false;
                        HTMLContent = crawlURL(linkURL, channelName, relation);
                        if (k == 1) {
                            k = relations.size() + 1;
                        }

                    }

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
                    String addDate = "";
                    if (k == relations.size() + 1) {
                        addDate = pubDate.substring(0, pubDate.length() - 6);
                        addDate = addDate.split("T")[0].replace("-", "");
                    } else { 
                        // this line depends to the date's format written in the web page
                        addDate = relationsDate.get(k - 1).trim().split("/")[2] + relationsDate.get(k - 1).trim().split("/")[1] + relationsDate.get(k - 1).trim().split("/")[0];
                    }
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
                    docID++;
                    String fileName = String.format("%s-%s-%d-%s.html", options.getProperty("source").substring(0, 2).toUpperCase(), channelName, docID, addDate);
                    String filePath = channelDir + File.separator + fileName;
                    ifSucceed = saveHTMLContent(filePath, docID, title, author, description, pubDate, linkURL, docDOM, initialChannelName, HTMLContent, fileName, channelName);
                    links += fileName.split("-")[0] + "-" + fileName.split("-")[1] + "-" + fileName.split("-")[2] + " ";
                    if (!ifSucceed) {
                        docID -= 1;
                    }

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

                    k--;
                    if (k < relations.size() + 1 && k > 0) {
                        j = 1;
                        linkURL = relations.get(k - 1).trim();
                        pubDate = relationsDate.get(k - 1).trim();
                        description = "";
                        title = relationsTitle.get(k - 1).trim();
                    }
                }
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void crawl() {

        configureDatabase();

        for (String channelName : RSSFeedMap.keySet()) {
           
            crawlChannel(channelName);

        }

        disconnectDatabase();

        System.out.println("Mission Complete!");
        System.out.println("these are the relations between links: " + links);

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
    private boolean saveHTMLContent(String filePath, int docID, String title, String author, String description,
            String pubDate, String linkURL, Document docDOM, String channelName, String HTMLContent, String fileName, String categorie) {

        String textXPath = RSSFeedMap.get(channelName).get("xpath");

        String imgXPath = RSSFeedMap.get(channelName).get("img_xpath");

        String charSet = RSSFeedMap.get(channelName).get("encoding");

        String contentFilePath = filePath.substring(0, filePath.lastIndexOf('.')) + ".txt";

        try {

            PrintWriter printer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(contentFilePath), "UTF-8")));

            printer.println("<DOC>");
            printer.println(String.format("<DOCNO>%s</DOCNO>", fileName.substring(0, fileName.indexOf(".html"))));
            printer.println(String.format("<URL>%s</URL>", linkURL));
            printer.println(String.format("<SRC>%s</SRC>", options.getProperty("source").trim()));
            printer.println(String.format("<CAT>%s</CAT>", categorie));
            printer.println(String.format("<TITLE>%s</TITLE>", title));
            printer.println(String.format("<TIME>%s</TIME>", pubDate));
            printer.println(String.format("<AUTHOR>%s</AUTHOR>", author));
            printer.println("<ABSTRACT>");
            printer.println(description);
            printer.println("</ABSTRACT>");
            printer.println("<TEXT>");

            /**
             * The textXPath CSS selector syntax should be accurate enough to
             * specify the location of element having text contents.
             */
            Elements elements = docDOM.select(textXPath);

            /**
             * The following code has a logic error. If the first text paragraph
             * doesn't have the same parent with the other text paragraphs we
             * really want, printTextContent() will ignore the other text
             * paragraph we need.
             */
            /**
             * We will simply extract the text contents from every element of
             * elements. Thus the key is to build good CSS selector syntax for
             * the text paragraphs we really want.
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
        //we add "http://www.jawharafm.net" to get the right link
        String imgLink =docDOM.select(imgXPath).attr("src").replace(" ", "%20");
        if(!imgLink.contains(srcUrl.substring(0, srcUrl.lastIndexOf("net")))){
            imgLink= srcUrl.substring(0, srcUrl.lastIndexOf("net")+3) + imgLink;
        }
            //channelName is defined in the craw-sites.xml file
        if ((channelName.contains("سياسة") || channelName.contains("اقتصاد") || channelName.contains("وطنية")
                || channelName.contains("تّكنولوجيا") || channelName.contains("ثقافة") || channelName.contains("رياضة")
                || channelName.contains("دولية") || channelName.contains("متفرقات") || channelName.contains("مجتمع"))
                && (imgLink.isEmpty() || imgLink.endsWith(".gif"))) {

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
                    if (imgLinkFound) {
                        break;
                    }

                }
            }

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
     * @param elements an element list composed of all elements satisfying a CSS
     * selector syntax
     *
     * @param printer a {@code PrintWriter} object to save the text contents to
     */
    private void printTextContent(Elements elements, PrintWriter printer) {

        for (Element element : elements) {
            String content = element.text();
            if (!content.isEmpty()) {
                printer.println(content);
            }
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
