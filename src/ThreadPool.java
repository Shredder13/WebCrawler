import java.io.IOException;
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
	
	public ThreadPool(int maxThreads) {
		mMaxThreads = maxThreads;
		mQ = new LinkedBlockingQueue<>();
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
		
		Log.d("Thread pool is shutdown");
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
			new WorkerThread().start();
		}
	}
	
	/**
	 * A worker thread. As long as there are items in the queue, it pulls them and executes.
	 *
	 */
	class WorkerThread extends Thread {
		
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
					Log.d("WorkerThread.run() : Cannot pull task from queue!");
					//e.printStackTrace();
				} 				
			}
			
			Log.d("WorkerThread.run() : Worker thread finished!");
		}
	}
}
