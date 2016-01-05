import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Port scanner. Given a port range, asynchronously scans it.
 * The number of scan-threads is defined by <code>MAX_THREADS</code>.
 * The scanner starts PortScanTask bounded to a single port, testing if it's open.
 * When the port scan is finished, a listener is notified and is given a list of opened ports.
 */
public class PortScanner {
	
	boolean isShutDown = false;
	
	interface IOnSinglePortScanned {
		void onSinglePortScanned(int port, boolean isOpen);
	}
	
	interface IOnPortScanFinished {
		void onPortScanFinished(ArrayList<Integer> openedPorts);
	}
	
	private final static int MAX_THREADS = 10;
	
	/** Timeout for a single port scan. */
	private final static int SINGLE_PORT_TIMEOUT = 500;	
	
	private String host;
	private ThreadPool threadPool;
	private ArrayList<Integer> openedPorts;
	
	/** Listener for when a single-port scan have finished */
	private IOnSinglePortScanned onSinglePortScannedListener;
	
	public PortScanner(String host) {
		this.host = host.replace("http://", "");
		openedPorts = new ArrayList<>();
		
		threadPool = new ThreadPool(MAX_THREADS);
		threadPool.start();
		Log.d("Port scanner is ready");
	}
	
	public boolean startScanAsync(int start, int end, final IOnPortScanFinished listener) {
		
		if (isShutDown) {
			Log.d("Port scanner is shutdown. Instansiate a new one.");
			return false;
		}
		
		Log.d(String.format("Starting port scan asynchronously from %d to %d.", start, end));
		
		//Check if something is wrong with the given port range
		if (end < 0 || start < 0 || end > 65535 || start > 65535 || end < start) {
			Log.d(String.format("Illegal port range: end=%d, start=%d.", end, start));
			return false;
		}
		
		final int numOfPorts = end - start;
		Log.d(String.format("numOfPorts = %d", numOfPorts));
		
		//When a port was scanned, the listener is notified
		onSinglePortScannedListener = new IOnSinglePortScanned() {
			
			int scannedPortCount = 0;
			
			@Override
			public synchronized void onSinglePortScanned(int port, boolean isOpen) {
				if (isOpen) {
					openedPorts.add(port);
					Log.d(String.format("Port %d is open!", port));
				}
				
				if (scannedPortCount >= numOfPorts) {
					//Finished the scan, wer'e good.
					listener.onPortScanFinished(openedPorts);
				}
				
				scannedPortCount++;
				Log.d(String.format("scannedPortCount = %d", scannedPortCount));
			}
		};
		
		//Enqueue a single-port check.
		for(int port = start; port <= end; port++) {
			threadPool.submit(new PortScanTask(port, onSinglePortScannedListener));
		}
		
		Log.d("Port scan was sent to threadpool.");
		return true;
	}
	
	/**
	 * PortScanTask tries to connect the given <code>host</code> to a single <code>port</code>.
	 * It notifies the registered <code>IOnSinglePortScanned</code> listener with the results.
	 */
	class PortScanTask extends Task {
		
		private int port;
		IOnSinglePortScanned listener;
		Socket socket = new Socket();
		
		public PortScanTask(int port, IOnSinglePortScanned listener) {
			this.port = port;
			this.listener = listener;
		}
		
		@Override
		public void run() {
			try {
				socket.connect(new InetSocketAddress(host, port), SINGLE_PORT_TIMEOUT);
				if (socket.isConnected() && listener != null) {
					listener.onSinglePortScanned(port, true);
				}
			} catch (Exception e) {
				//e.printStackTrace();
				Log.d(String.format("Port %d is closed", port));
				if (listener != null) {
					listener.onSinglePortScanned(port, false);
				}
			}
		}

		@Override
		protected void shutdown() throws IOException {
			listener = null;
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public void shutdown() {
		isShutDown = true;
		threadPool.shutDown();
	}
	
	/*public static void main(String[] args) {
		final PortScanner scanner = new PortScanner("www.walla.co.il");
		scanner.startScanAsync(80, 100, new IOnPortScanFinished() {
			
			@Override
			public void onPortScanFinished(ArrayList<Integer> openedPorts) {
				Log.d("Port scan have finished");
				scanner.shutdown();
			}
		});
	}*/
}
