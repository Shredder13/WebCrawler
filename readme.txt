Our webserver is implemented as described below:

- Our webserver index.html have changed and now contains the crawler-form fields and crawling history. The form method is POST, and action is "execResult.html".
- it also contains a placeholder for the crawling history which happens dynamically.
- if a request to "execResult.html" or any crawling-result page is missing the "referer: localhost" header, we return "403 Forbidden" error.
- execResult.html loads its content dynamically. it has 2 place holders, one for the message (success or failure) and another for the crawling history.
- The crawler functionality begins in the WebServerHttpResponse class, when recognizing that "execResult.html" page is called.
	The response is according to the crawler state, or if there was any error (success or failure).
- WebCrawler.start() method is where the flow begins.
	PortScanner.java - A multi-threaded port scanner is started if requested.
	WebCrawler.handleRespectRobots() - filling a "black-list" and "white-list" for crawling, and sending them to the downloaders queue.

- The downloaders-analyzers loop is handled as follows:
	- Our threadpool from Lab1 has a job-queue, and have maxThreads.
	- So for making 10 downloaders, we start a threadpool (downloadersPool) with maxThreads = 10.
	- same thing with analyzers - called analyzersPool.
	- When a downloader finished a download (using CrawlerHttpConnection.java), if its HTML file it push an "AnalyzerTask" to the analyzersPool.
	- When an analyzer finish parsing an HTML file (using HtmlParser.java) it pushs the content it found, each link as "DownloaderTask" to the downloadersPool.
	- When both threadpools are empty, the process finishes.
		Note: we used a counter to know how much analyzers & downloaders are alive. The number is increased when a DownloaderTask constructor is called (before it is pushed to the downlaodersPool),
			and decreased when the task is DONE, after it submits a task to the AnalyzersPool. That way we assure that the process isn't finished too early.
		Note2: same thing happens for analyzers counter.

- To perform network connection, the class CrawlerHttpConnection is used.
- The WebServer holds a CrawlerData instance - which aggregates the crawling statistics.
- NOTE: we count pages & images that return HTTP 200 OK only.
- When the crawling is finished, a statistics page is created (look at StatisticsPageBuilder.java), and an email is sent using javax.mail API (jar included in sources & serverroot folder).
