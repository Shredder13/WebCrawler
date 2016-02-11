import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HtmlParser extracts the relevant resources' links from a given HTML text.
 */
public class HtmlParser {
	
	private String html;
	
	//links that refer only to a local file are being completed to absolute (including http).
	private String absServerPath;
	
	//Output lists
	private ArrayList<String> images;
	private ArrayList<String> videos;
	private ArrayList<String> docs;
	private ArrayList<String> links;
	
	//non-href extensions.
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
	
	/**
	 * Parsing the HTML using a regex, for extracting links and fill the output lists.
	 */
	public void parse() {
		//build the regex dynamically according to config.ini (the extensions lists).
		String regex = buildRegex();
		
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(html);
		while(m.find()) {
			//For every match, we classify it according to it's extension.
			//BONUS: that allows us to extract links that aren't bounded to HTML tags,
			//such as in javascripts.
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
						//If sulamit is found, we consider it as link without it.
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
	
	/**
	 * When a link is found it sometimes has to go thorugh some modifications.
	 * The modifications are making the link absolute (to include http & domain),
	 * and also handling dots in the path that point to parent / current folders
	 * (such as "/../" or "/./").
	 * @param resPath - the resource path
	 * @param resList - the relevant resource list for the resource.
	 */
	private void addResourceToList(String resPath, ArrayList<String> resList) {
		
		//Completing the path
		resPath = makeAbsolutePath(resPath);
		
		//modifying according to the dots
		if (resPath.contains("..") || resPath.contains("/./")) {
			resPath = handleDots(resPath);
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
	private String handleDots(String resPath) {
		String[] urlPartsArr = resPath.split("/");
		
		ArrayList<String> resultUrlParts = new ArrayList<>();
		
		//For each url parts - if its ".." we delete the last url-part from the result list,
		//otherwise we add the url part.
		for (int i=0; i<urlPartsArr.length; i++) {
			if (urlPartsArr[i].equals("..")) {
				resultUrlParts.remove(i-1);	
			} else {
				if (!urlPartsArr[i].equals(".")) {
					resultUrlParts.add(urlPartsArr[i]);
				}
			}
		}
		
		//Rebuilding the new path.
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
	 * Build a regex dynamically, depending on the extensions defined at config.ini.
	 * 
	 * @return regex in the form<br>
	 * ["']([^"']+\.((bmp|jpg|png|gif|ico)|(avi|mpg|mp4|wmv|mov|flv|swf|mkv)|(pdf|doc|docx|xls|xlsx|ppt|pptx)))["']|(a\s*href\s*=\s*["'](.+?)["'])
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
		StringBuilder regexSB = new StringBuilder("[\"']([^\"']+\\.(");
		
		//add image extensions to regex
		addExtToRegex(imgExts, regexSB);
		regexSB.append("|");
		//add video exts to regex
		addExtToRegex(videoExts, regexSB);
		regexSB.append("|");
		//add docs exts to regex
		addExtToRegex(docExts, regexSB);
		regexSB.append("))[\"']|(a\\s+href\\s*=\\s*[\"'](.+?)[\"'])");
		
		return regexSB.toString();
	}
	
	/**
	 * Helper method for adding an extension handling to the regex in <code>buildRegex</code> method.
	 * @param exts - the list of extensions to add.
	 * @param regexSB - the StringBuilder that the extensions is added to.
	 * @return the string that regexSB holds after adding the current extensions to it. 
	 */
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
	
	/**
	 * helper method for completing a given url, such as adding "http"
	 * and removing trailing "/".
	 * @param url
	 * @return the modified url.
	 */
	private String parseUrl(String url) {
		
		url = url.replace('\\', '/');
		if (!url.startsWith("http://")) {
			url = "http://" + url;
		}
		
		int hostPartEnd = url.lastIndexOf("/");
		if (hostPartEnd != -1 && hostPartEnd > "https://".length()) {
			url = url.substring(0, hostPartEnd);
		}
		
		return url;
	}
	
	/**
	 * If a resource is linked to a local path (such as "/images/img.gif")
	 * we complete it to be "http://www.crawled-domain.com/images/img.gif".
	 * @param src - resource path.
	 * @return absolute url for the resource.
	 */
	private String makeAbsolutePath(String src) {
		String result = "";
		if (src.startsWith("/")) {
			try {
				HttpUrl url = new HttpUrl(absServerPath);
				url.getHost();
				result = String.format("%s://%s%s", url.getProtocol(), url.getHost(), src);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
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
}
