package hello;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.dom.DOMDocument;
import org.dom4j.dom.DOMDocumentFactory;
import org.dom4j.io.DOMReader;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.tidy.Tidy;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ScheduledTasks {
	private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
	Tidy tidy = getTidy();
	DOMReader domReader = new DOMReader();

	//develop
//	private static String workDir = "/home/roman/jura/workshop-manuals1991/";
	//prodaction
	private static String workDir = "/home/holweb/jura/workshop-manuals1991/";

	String domain = "http://workshop-manuals.com";
	private static String dirJsonName = workDir + "OUT1json/";
	private static String dirPdfName = workDir+ "OUT1pdf/";
	private static String dirLargeHtmlName = workDir+ "OUT1html/";
	DateTime startMillis;
	static PeriodFormatter hmsFormatter = new PeriodFormatterBuilder()
			.appendHours().appendSuffix("h ")
			.appendMinutes().appendSuffix("m ")
			.appendSeconds().appendSuffix("s ")
			.toFormatter();
	final static Path dirJsonStart = Paths.get(dirJsonName);
	private	int fileIdx = 0;
	int filesCount;

	@Scheduled(fixedRate = 500000000)
	public void reportCurrentTime() {
		domReader.setDocumentFactory(new DOMDocumentFactory());
		startMillis = new DateTime();
		System.out.println("The time is now " + dateFormat.format(startMillis.toDate()));
		filesCount = countFiles2(dirJsonStart.toFile());
		logger.debug("Files count " + filesCount + ". The time is now " + dateFormat.format(new Date()));
		logger.debug(dirJsonName);
		try {
			makeLargeHTML();
			//			makePdfFromHTML();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void makeLargeHTML() throws IOException {
		logger.debug("Start folder : "+dirJsonStart);
		Files.walkFileTree(dirJsonStart, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				final FileVisitResult visitFile = super.visitFile(file, attrs);
				fileIdx++;
				logger.debug(fileIdx + "" + "/" + filesCount +  procentWorkTime() + file);
				String fileStr = file.toFile().toString();
				String[] split = fileStr.split("/");
				String manufacturerName = split[split.length-2];
				Map<String, Object> jsonMap = readJsonDbFile2map(fileStr);
				String autoName = (String) jsonMap.get("autoName");
				String autoNameWithManufacturer = manufacturerName+"_-_"+autoName;
				//				String autoNameWithManufacturer = manufacturerName+ " :: "+autoName;
				logger.debug(autoNameWithManufacturer);

				Element autoDocBody = createAutoDocument(autoNameWithManufacturer);
				autoTileNr = 0;
				addGroup(2,getIndexList(jsonMap));
				
				buildBookmark(autoDocument);
				
				try{
					String htmlOutFileName = dirLargeHtmlName+autoNameWithManufacturer+".html";
					saveHtml(autoDocument, htmlOutFileName);
				}catch(Exception e){
					e.printStackTrace();
				}

				return visitFile;
			}

			private List<Map<String, Object>> getIndexList(Map<String, Object> jsonMap) {
				return (List<Map<String, Object>>) jsonMap.get("indexList");
			}

			private void addGroup(int hIndex, List<Map<String, Object>> indexList) {
				if(indexList != null){
					int numInGroup = 0;
					for (Map<String, Object> map : indexList) {
						numInGroup++;
//						Element autoDocBody = (Element) autoDocument.selectSingleNode("/html/body");
						String text = (String) map.get("text");
						Element hElement;
						if(hIndex>6)
						{
							hElement = autoDocBody.addElement("div").addAttribute("data-bookmark", "h"+hIndex);
						}else{
							hElement = autoDocBody.addElement("h"+hIndex).addAttribute("data-bookmark", "h"+hIndex);
						}
						hElement.addElement("a").addAttribute("name", "h" + hIndex + "_"+numInGroup).addText(text);
						String url = (String) map.get("url");
						if(url != null)
						{
							addRealInfo(url);
						}
						
						addGroup(hIndex+1, getIndexList(map));
					}
				}
			}


		});

	}
	int autoTileNr;
	private void addRealInfo(String autoTileHref) {
		autoTileNr++;
		Document domFromStream = getDomFromStream(autoTileHref);
		addAutoTile(autoTileNr, "t1", domFromStream);
	}
	private HttpURLConnection getUrlConnection(String url) {
		try {
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			con.setDoOutput(true);
			con.setRequestProperty("Content-Type", "text/html"); 
			con.setRequestProperty("charset", "utf-8");
			return con;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (ProtocolException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	private DOMDocument getDomFromStream(String url) {
		HttpURLConnection urlConnection = getUrlConnection(url);
		try {
			return getDomFromStream(urlConnection);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	private DOMDocument getDomFromStream(HttpURLConnection urlConnection) throws IOException {
		InputStream requestBody = urlConnection.getInputStream();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(requestBody));
		String line = null;
		StringBuilder responseData = new StringBuilder();
		while((line = in.readLine()) != null) {
			if(line.contains("font size>")){
				line = line.replace("font size>", "font>");
			}
			if(line.contains("<g:plusone size=\"small\" annotation=\"none\"></g:plusone>")){
				line = line.replace("<g:plusone size=\"small\" annotation=\"none\"></g:plusone>", "");
			}
			responseData.append(line);
		}
		InputStream byteArrayInputStream = new ByteArrayInputStream(responseData.toString().getBytes(StandardCharsets.UTF_8));
		
		org.w3c.dom.Document html2xhtml = tidy.parseDOM(byteArrayInputStream, null);
//		org.w3c.dom.Document html2xhtml = tidy.parseDOM(requestBody, null);
		DOMDocument document = (DOMDocument) domReader.read(html2xhtml);
		return document;
	}
	private Tidy getTidy() {
		Tidy tidy = new Tidy();
		tidy.setShowWarnings(false);
		tidy.setXmlTags(false);
		tidy.setInputEncoding("UTF-8");
		tidy.setOutputEncoding("UTF-8");
		tidy.setXHTML(true);// 
		tidy.setMakeClean(true);
		tidy.setQuoteNbsp(false);
		return tidy;
	}

	private void changeImgUrl(Element autoTileElement) {
		List<Element> selectNodes = autoTileElement.selectNodes(".//img");
		for (Element bagroundImage : selectNodes) {
			Attribute srcImg = bagroundImage.attribute("src");
			if(!srcImg.getValue().contains(domain))
			srcImg.setValue(domain+"/"+srcImg.getValue());
		}
//		Element bagroundImage = (Element) autoTileElement.selectSingleNode("img[1]");
	}
	private String addAutoTile(int autoTileNr, String autoTileName, Document domFromStream  ) {
		Element autoTileElement = (Element) domFromStream.selectSingleNode("/html/body//div[@id='page1-div']");
		if(autoTileElement != null){
			autoTileElement.attribute("id").setValue("auto_tile_"+autoTileNr);
			changeImgUrl(autoTileElement);
		}else{
			//audi
			Element autoTileElement2 = (Element) domFromStream.selectSingleNode(
					"/html/body/div/table//td[div/h2]");
			changeImgUrl(autoTileElement2);
			Element autoTileNameElement = (Element) autoTileElement2.selectSingleNode("div/h2");
			autoTileNameElement.setText(autoTileName);
			/*
			List<Element> breadcrum = autoTileElement2.selectNodes("div/h3");
			for (Element element : breadcrum) {
				element.detach();
			}
			 * */
			autoTileElement = autoDocBody.addElement("div");
			autoTileElement.addAttribute("id","auto_tile_"+autoTileNr);
			
			for (Iterator iterator = autoTileElement2.elementIterator(); iterator.hasNext();) {
				Element element = (Element) iterator.next();
				autoTileElement.add(element.detach());
			}
		}
		/* neccesary
		 * */
		List<Element> selectNodes = autoTileElement.selectNodes(".//*[text()]");
		for (Element element : selectNodes) {
			String replace = element.getText().replace("‘", "'").replace("’", "'");
			element.setText(replace);
		}
		Node detach = autoTileElement.detach();
		String asXML = detach.asXML();
		autoDocBody.add(detach);
		return asXML;
	}
	private void makeBookmark(Element h234InHead, Element h234Element, String id) {
		Element h234A_Element = (Element) h234Element.selectSingleNode("a");
		String text = h234A_Element.getText();
		h234InHead.addAttribute("name", text);
		h234InHead.addAttribute("href", "#"+id);
		h234A_Element.addAttribute("name", id);
	}
	private void buildBookmark(Document autoDocument) {
		List<Element> bookmarkEls = autoDocument.selectNodes("/html/body//*[@data-bookmark]");
		logger.debug(""+bookmarkEls.size());
		Element headEl = (Element) autoDocument.selectSingleNode("/html/head");
		Element bookmarks = headEl.addElement("bookmarks")
		,h2 = null, h3 = null, h4 = null
		,h5 = null, h6 = null, h7 = null
		,h8 = null, h9 = null, h10 = null;
		int h2i = 0, h3i = 0, h4i = 0;
		int h5i = 0, h6i = 0, h7i = 0;
		int h8i = 0, h9i = 0, h10i = 0;
		String h2id = null, h3id = null, h4id = null;
		String h5id = null, h6id = null, h7id = null;
		String h8id = null, h9id = null, h10id = null;
		int i = 0;
		for (Element h234Element : bookmarkEls) {
			i++;
			if(h234Element.attribute("data-bookmark").getValue().equals("h2"))
			{
				h2 = bookmarks.addElement("bookmark");
				h2id = "bm"+h2i++;
				makeBookmark(h2, h234Element, h2id);
			}
			else
				if(h234Element.attribute("data-bookmark").getValue().equals("h3"))
				{
					h3 = h2.addElement("bookmark");
					h3id = h2id + "."+h3i++;
					makeBookmark(h3, h234Element, h3id);
				}
				else
					if(h234Element.attribute("data-bookmark").getValue().equals("h4"))
					{
						h4 = h3.addElement("bookmark");
						h4id = h3id + "."+h4i++;
						makeBookmark(h4, h234Element, h4id);
					}
					else
						if(h234Element.attribute("data-bookmark").getValue().equals("h5"))
						{
							h5 = h4.addElement("bookmark");
							h5id = h4id + "."+h5i++;
							makeBookmark(h5, h234Element, h5id);
						}
						else
							if(h234Element.attribute("data-bookmark").getValue().equals("h6"))
							{
								h6 = h5.addElement("bookmark");
								h6id = h5id + "."+h6i++;
								makeBookmark(h6, h234Element, h6id);
							}
							else
								if(h234Element.attribute("data-bookmark").getValue().equals("h7"))
								{
									h7 = h6.addElement("bookmark");
									h7id = h6id + "."+h7i++;
									makeBookmark(h7, h234Element, h7id);
								}
								else
									if(h234Element.attribute("data-bookmark").getValue().equals("h8"))
									{
										h8 = h7.addElement("bookmark");
										h8id = h7id + "."+h8i++;
										makeBookmark(h8, h234Element, h8id);
									}
									else
										if(h234Element.attribute("data-bookmark").getValue().equals("h9"))
										{
											h9 = h8.addElement("bookmark");
											h9id = h8id + "."+h9i++;
											makeBookmark(h9, h234Element, h9id);
										}
										else
											if(h234Element.attribute("data-bookmark").getValue().equals("h10"))
											{
												h10 = h9.addElement("bookmark");
												h10id = h9id + "."+h10i++;
												makeBookmark(h10, h234Element, h10id);
											}
			
		}
	}
	OutputFormat prettyPrintFormat = OutputFormat.createPrettyPrint();
	private void writeToHtmlFile(Document document, String htmlOutFileName) {
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(htmlOutFileName);
			//					HTMLWriter xmlWriter = new HTMLWriter(fileOutputStream, prettyPrintFormat);
			XMLWriter xmlWriter = new XMLWriter(fileOutputStream, prettyPrintFormat);
			xmlWriter.write(document);
			xmlWriter.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void saveHtml(Document document, String htmlOutFileName) {
		writeToHtmlFile(document, htmlOutFileName);
	}
	Document autoDocument;
		Element autoDocBody;
	private Element createAutoDocument(String autoName) {
		autoDocument = createAutoDocument();
		autoDocBody = (Element) autoDocument.selectSingleNode("/html/body");
		autoDocBody.addElement("h1").addText(autoName);
		return autoDocBody;
	}
	String procentWorkTime() {
		int procent = fileIdx*100/filesCount;
		String workTime = hmsFormatter.print(new Period(startMillis, new DateTime()));
		String procentSecond = " - html2pdf3 - (" + procent + "%, " + workTime + "s)";
		return procentSecond;
	}
	public static int countFiles2(File directory) {
		int count = 0;
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				count += countFiles2(file); 
			}else
				count++;
		}
		return count;
	}
	private Map<String, Object> readJsonDbFile2map(String fileNameJsonDb) {
		logger.debug(fileNameJsonDb);
		File file = new File(fileNameJsonDb);
		logger.debug(" o - "+file);
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> readJsonDbFile2map = null;// = new HashMap<String, Object>();
		try {
			readJsonDbFile2map = mapper.readValue(file, Map.class);
			//			logger.debug(" o - "+readJsonDbFile2map);
		} catch (JsonParseException e1) {
			e1.printStackTrace();
		} catch (JsonMappingException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		//		logger.debug(" o - "+readJsonDbFile2map);
		return readJsonDbFile2map;
	}

	Document createAutoDocument() {
		Document autoDocument = DocumentHelper.createDocument();
		Element htmElAutoDocument = autoDocument.addElement("html");
		Element headElAddElement = htmElAutoDocument.addElement("head");
		addUtf8(headElAddElement);
		htmElAutoDocument.addElement("body");
		return autoDocument;
	}
	private void addUtf8(Element headEl) {
		headEl.addElement("meta").addAttribute("charset", "utf-8");
	}

}
