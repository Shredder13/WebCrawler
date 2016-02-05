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
	
	//TODO: delete this
	private int findNumOfImg() {
		String findStr = "<img";
		int lastIndex = 0;
		int count = 0;

		while(lastIndex != -1){

		    lastIndex = html.indexOf(findStr,lastIndex);

		    if(lastIndex != -1){
		        count ++;
		        lastIndex += findStr.length();
		    }
		}
		
		return count;
	}
	
	public void parse() {
		String regex = buildRegex();
		
		WebCrawler.numOfImg += findNumOfImg();
		
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(html);
		while(m.find()) {
			try {
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
					int sulamitStart = hrefLink.indexOf('#');
					if (sulamitStart != -1) {
						hrefLink = hrefLink.substring(0, sulamitStart);
					}
					
					addResourceToList(hrefLink, links);
				}
			} catch (IllegalArgumentException e) {
				System.out.println("wtf");
			}
		}
		
		for (String s : images) {
			Log.d(String.format("HtmlParse found image %s", s));
		}
		for (String s : videos) {
			Log.d(String.format("HtmlParse found video %s", s));
		}
		for (String s : docs) {
			Log.d(String.format("HtmlParse found document %s", s));
		}
		for (String s : links) {
			Log.d(String.format("HtmlParse found HREF %s", s));
		}
	}
	
	private void addResourceToList(String resPath, ArrayList<String> resList) {		
		
		resPath = makeAbsolutePath(resPath);
		if (resPath.contains("..")) {
			resPath = removeDouleDots(resPath);
		}
		
		if (!resList.contains(resPath)) {
			resList.add(resPath);
		}
	}
	
	/**
	 * Removing the double dots (up-folder). Fix url with the form of
	 * http://www.website.com/folder/../another_folder/../page.html
	 * @param resPath
	 * @return for the example above, it returns http://www.website.com/page.html
	 */
	private String removeDouleDots(String resPath) {
		String[] urlPartsArr = resPath.split("/");
		
		ArrayList<String> resultUrlParts = new ArrayList<>();
		
		//For each url parts - if its ".." we delete the last url-part from the result list,
		//otherwise we add the url part.
		for (int i=0; i<urlPartsArr.length; i++) {
			if (urlPartsArr[i].equals("..")) {
				resultUrlParts.remove(i-1);
			} else {
				resultUrlParts.add(urlPartsArr[i]);
			}
		}
		
		StringBuilder result = new StringBuilder();
		for (int i=0; i<resultUrlParts.size(); i++) {
			
			result.append(resultUrlParts.get(i));
			
			if (i != resultUrlParts.size() - 1) {
				result.append("/");
			} else if (resPath.endsWith("/")){
				result.append("/");
			}
		}
		
		return result.toString();
	}

	/**
	 * 
	 * @return regex in the form<br>
	 * ["'](.+\.((bmp|jpg|png|gif|ico)|(avi|mpg|mp4|wmv|mov|flv|swf|mkv)|(pdf|doc|docx|xls|xlsx|ppt|pptx)))["']|(a\s*href\s*=\s*"(.+?)")
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
		StringBuilder regexSB = new StringBuilder("[\"'](.+\\.(");
		
		addExtToRegex(imgExts, regexSB);
		regexSB.append("|");
		addExtToRegex(videoExts, regexSB);
		regexSB.append("|");
		addExtToRegex(docExts, regexSB);
		regexSB.append("))[\"']|(a\\s*href\\s*=\\s*[\"'](.+?)[\"'])");
		
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
		//Return the url without trailing "/".
		return url.substring(0, pathEnd);
	}
	
	private String makeAbsolutePath(String src) {
		String result = "";
		if (src.startsWith("/")) {
			result = absServerPath + src;
		} else if (!src.matches("^(http|https)://.+")) {
			//if the source starts with something different than http://
			result =  absServerPath + "/" + src;
		} else {
			result = src;
		}
		
		return result;
	}
	
	public ArrayList<String> getImagesUrls() {
		return images;
	}
	public ArrayList<String> getVideosUrls() {
		return videos;
	}
	public ArrayList<String> getDocsUrls() {
		return docs;
	}
	public ArrayList<String> getHrefUrls() {
		return links;
	}
	
	//TODO: Remove when submitting
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
			BufferedReader reader = new BufferedReader(new FileReader("html_test/index.html"));
			String line = "";
			while ((line = reader.readLine()) != null) {
				html += line;
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		HtmlParser parser = new HtmlParser("http://www.blat.com/", html, exts);
		parser.parse();
	}*/
}
