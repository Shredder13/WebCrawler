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
	
	final Object waitForCompletion = new Object();
	
	private String host;
	private ThreadPool threadPool;
	private ArrayList<Integer> openedPorts;
	
	/** Listener for when a single-port scan have finished */
	private IOnSinglePortScanned onSinglePortScannedListener;
	
	/**
	 * Creates a new PortScanner instance, and raises a threadpool of <code>MAX_THREADS</code> workers.
	 * @param host - he host to scan.
	 */
	public PortScanner(String host) {
		this.host = host.replace("http://", "");
		if (this.host.endsWith("/")) {
			this.host = this.host.substring(0, this.host.length() - 1);
		}
		openedPorts = new ArrayList<>();
		
		threadPool = new ThreadPool(MAX_THREADS);
		threadPool.start();
		Log.d("Port scanner is ready");
	}
	
	/**
	 * Starts a synchronous multi-threaded port scan. The port scanning is multithreaded, but the caller
	 * of this method will wait until the end of execution.
	 * @param start - port range start, inclusive
	 * @param end - port range end, inclusive
	 * @return a list of opened ports
	 * @throws PortScannerException if any error arises while scanning.
	 */
	public ArrayList<Integer> getOpennedPortsSync(int start, int end) throws PortScannerException {
		
		final ArrayList<Integer> result = new ArrayList<>();
		
		//A listener for the port scanning process. When the scan is finished, it notifies that
		//the caller function can continue.
		IOnPortScanFinished finishScanningAll = new IOnPortScanFinished() {
			@Override
			public void onPortScanFinished(ArrayList<Integer> openedPorts) {
				synchronized (waitForCompletion) {
					result.addAll(openedPorts);
					waitForCompletion.notifyAll();
				}
			}
		};
		
		startScanAsync(start, end, finishScanningAll);
		
		try {
			synchronized (waitForCompletion) {
				//Block until all port range is finished.
				waitForCompletion.wait();
			}
		} catch (InterruptedException e) {
			throw new PortScannerException("Unknown error");
		}
		
		return result;
	}
	
	/**
	 * Scans a given port range asynchronously.
	 * @param start
	 * @param end
	 * @param listener - listener when all ports were scanned
	 * @throws PortScannerException - if the port scanner is shutdown or the given port range is illegal.
	 */
	private void startScanAsync(int start, int end, final IOnPortScanFinished listener) throws PortScannerException {
		
		if (isShutDown) {
			throw new PortScannerException("Port scanner is shutdown. Instansiate a new one.");
		}
		
		Log.d(String.format("Starting port scan asynchronously from %d to %d.", start, end));
		
		//Check if something is wrong with the given port range
		if (end < 0 || start < 0 || end > 65535 || start > 65535 || end < start) {
			throw new PortScannerException(String.format("Illegal port range: end=%d, start=%d.", end, start));
		}
		
		final int numOfPorts = end - start;
		Log.d(String.format("numOfPorts = %d", numOfPorts));
		
		//When a port was scanned, the single-port listener is notified
		onSinglePortScannedListener = new IOnSinglePortScanned() {
			
			int scannedPortCount = 0;
			
			@Override
			public synchronized void onSinglePortScanned(int port, boolean isOpen) {
				if (isOpen) {
					//port is open --> add it to the list.
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
	}
	
	/**
	 * PortScanTask tries to connect the given <code>host</code> to a single <code>port</code>.
	 * It notifies the registered <code>IOnSinglePortScanned</code> listener with the results.
	 */
	class PortScanTask extends Task {
		
		private int port;
		IOnSinglePortScanned listener;
		Socket socket = new Socket();
		
		/**
		 * Creates a PortScanTask on a given port. When it is finish the <code>listener</code>
		 * is notified.
		 * @param port
		 * @param listener
		 */
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
}
