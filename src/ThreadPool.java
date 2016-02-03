import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A thread pool.
 * Holds <code>maxThreads</code active threads for executing task in its blocking-queue.
 *
 */
public class ThreadPool {
	
	private int mMaxThreads;
	private boolean closed = false;
	private LinkedBlockingQueue<Task> mQ;
	private boolean started = false;
	private ArrayList<WorkerThread> workers;
	
	public ThreadPool(int maxThreads) {
		mMaxThreads = maxThreads;
		mQ = new LinkedBlockingQueue<>();
		workers = new ArrayList<>();
	}

	/**
	 * Enqueue new task in the blocking queue.
	 * @param task The task object to execute
	 */
	public void submit(Task task) {
		if (!closed) {
			try {
				mQ.put(task);
			} catch (InterruptedException e) {
				Log.d("ThreadPool.submit(task) : Could not put task in queue!");
				//e.printStackTrace();
			}
		} else {
			Log.d("pool is closed!");
		}
	}
	
	/**
	 * Shutdown the thread pool. Shutting down all tasks in execution.
	 */
	public void shutDown() {
		
		closed = true;
		
		Task task = null;
		while ((task = mQ.poll()) != null) {
			try {
				task.shutdown();
			} catch (IOException e) {
				Log.d("error closing a socket while shutdown a task");
				//e.printStackTrace();
			}
		}
		
		mQ.clear();
		
		for (WorkerThread worker : workers) {
			worker.interruptSilently();
		}
		
		workers.clear();
		
		Log.d("Thread pool is shutting down");
	}
	
	/**
	 * Starts the threadpool, Creating <code>maxThreads</code> new threads
	 */
	public synchronized void start() {
		if (started) {
			Log.d("ThreadPool.start() : Pool has been already started");
			return;
		}
		
		started = true;
		
		for (int i = 0; i < mMaxThreads; i++) {
			WorkerThread worker = new WorkerThread();
			workers.add(worker);
			worker.start();
		}
	}
	
	/**
	 * A worker thread. As long as there are items in the queue, it pulls them and executes.
	 *
	 */
	class WorkerThread extends Thread {
		
		private	boolean silentInterruption;
		
		/**
		 * Dequeuing tasks from the queue and executes them until the thread pool is closed.
		 */
		@Override
		public void run() {
			
			//Until the ThreadPool does not shutdown, the worker threads pull from the queue infinitely
			while (!closed) {
				try {
					Task t = mQ.take();
					t.run();
				} catch (InterruptedException e) {
					if (!silentInterruption) {
						Log.d("WorkerThread.run() : Cannot pull task from queue!");
					}
					//e.printStackTrace();
				} catch (Exception e) {
					//Any other exception, just print it but don't kill the worker...
					e.printStackTrace();
				}
			}
			
			Log.d("WorkerThread.run() : Worker thread finished!");
		}
		
		public void interruptSilently() {
			silentInterruption = true;
			interrupt();
		}
	}
}

