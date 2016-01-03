import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HtmlParser {
	
	private String html;	
	private String absServerPath;
	
	private ArrayList<String> images;
	private ArrayList<String> videos;
	private ArrayList<String> docs;
	private ArrayList<String> links;
	
	private ArrayList<String> imgExts;
	private ArrayList<String> videoExts;
	private ArrayList<String> docExts;
	
	public HtmlParser(String url, String html, HashMap<String, ArrayList<String>> exts) {
		images = new ArrayList<>();
		videos = new ArrayList<>();
		docs = new ArrayList<>();
		links = new ArrayList<>();
		
		this.html = html;
		
		imgExts = exts.get("imageExtensions");
		videoExts = exts.get("videoExtensions");
		docExts = exts.get("documentExtensions");
		
		absServerPath = parseUrl(url);
	}
	
	private void parse() {
		String regex = buildRegex();
		
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(html);
		while(m.find()) {
			String resourcePath = m.group(1);
			String imgExt = m.group(3);
			String videoExt = m.group(4);
			String docExt = m.group(5);
			String hrefLink = m.group(7);
			
			if (resourcePath != null) {
				if (imgExt != null) {
					addResourceToList(resourcePath, images);
				} else if (videoExt != null) {
					addResourceToList(resourcePath, videos);
				} else if (docExt != null) {
					addResourceToList(resourcePath, docs);
				} 
			} else if (hrefLink != null) {
				addResourceToList(hrefLink, links);
			}
		}
		
		for (String s : images) {
			System.out.println(s);
		}
		for (String s : videos) {
			System.out.println(s);
		}
		for (String s : docs) {
			System.out.println(s);
		}
		for (String s : links) {
			System.out.println(s);
		}
	}
	
	private void addResourceToList(String resPath, ArrayList<String> resList) {
		resPath = makeAbsolutePath(resPath);
		resList.add(resPath);
	}
	
	/**
	 * 
	 * @return regex in the form<br>
	 * (\w+\.((bmp|jpg|png|gif|ico)|(avi|mpg|mp4|wmv|mov|flv|swf|mkv)|(pdf|doc|docx|xls|xlsx|ppt|pptx)))["']|(a href="(.+?)")
	 * <br>
	 * group #1 - if not empty, it is a file with one of the extensions.<br>
	 * group #2 - indicates the extension.<br>
	 * group #3 - if not empty, this is an image extension.<br>
	 * group #4 - if not empty, this is a video extension.<br>
	 * group #5 - if not empty, this is a document extension<br><br>
	 * group #6 - if not empty, it is an 'a href=...' link.<br>
	 * group #7 - this is the link content of group #6.
	 */
	private String buildRegex() {
		StringBuilder regexSB = new StringBuilder("(\\w+\\.(");
		
		addExtToRegex(imgExts, regexSB);
		regexSB.append("|");
		addExtToRegex(videoExts, regexSB);
		regexSB.append("|");
		addExtToRegex(docExts, regexSB);
		regexSB.append("))[\"']|(a href=[\"'](.+?)[\"'])");
		
		return regexSB.toString();
	}
	
	private String addExtToRegex(ArrayList<String> exts, StringBuilder regexSB) {
		regexSB.append("(");
		for (int i=0; i < exts.size(); i++) {
			regexSB.append(exts.get(i));
			if (i+1 < exts.size()) {
				regexSB.append("|");
			}
		}
		regexSB.append(")");
		
		return regexSB.toString();
	}
	
	private String parseUrl(String url) {
		
		if (!url.startsWith("http://")) {
			url = "http://" + url;
		}
		
		url = url.replace('\\', '/');
		int pathEnd = url.lastIndexOf("/");
		return url.substring(0, pathEnd + 1);
	}
	
	private String makeAbsolutePath(String src) {
		String result = "";
		if (src.startsWith("/")) {
			result = absServerPath + src;
		} else if (!src.matches("^http://.+")) {
			//if the source starts with something different than http://
			result =  absServerPath + src;
		} else {
			result = src;
		}
		
		return result;
	}
	
	/*public static void main(String[] args) {
		HashMap<String, ArrayList<String>> exts = new HashMap<>();
		ArrayList<String> imageExtensions = new ArrayList<>();
		ArrayList<String> videoExtensions = new ArrayList<>();
		ArrayList<String> docsExtensions = new ArrayList<>();
		
		imageExtensions.add("bmp");
		imageExtensions.add("jpg");
		imageExtensions.add("png");
		imageExtensions.add("gif");
		imageExtensions.add("ico");
		
		videoExtensions.add("avi");
		videoExtensions.add("mpg");
		videoExtensions.add("mp4");
		videoExtensions.add("wmv");
		videoExtensions.add("mov");
		videoExtensions.add("flv");
		videoExtensions.add("swf");
		videoExtensions.add("mkv");
		
		docsExtensions.add("pdf");
		docsExtensions.add("doc");
		docsExtensions.add("docx");
		docsExtensions.add("xls");
		docsExtensions.add("xlsx");
		docsExtensions.add("ppt");
		docsExtensions.add("pptx");
		
		exts.put("imageExtensions", imageExtensions);
		exts.put("videoExtensions", videoExtensions);
		exts.put("documentExtensions", docsExtensions);
		
		String html = "";
		try {
			BufferedReader reader = new BufferedReader(new FileReader("index.html"));
			String line = "";
			while ((line = reader.readLine()) != null) {
				html += line;
			}
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		HtmlParser parser = new HtmlParser("http://www.blat.com/", html, exts);
		parser.parse();
	}*/
}
