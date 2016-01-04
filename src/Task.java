import java.io.IOException;

/**
 * A Task is an object that designated to be enqueued into the thread pool.
 *
 */
public abstract class Task implements Runnable {
	
	/**
	 * This method is called when the thread pool is closing.
	 * Any subclass should take care for closing its own stuff. 
	 * @throws IOException If any socket error occurs during the shutdown process.
	 */
	protected abstract void shutdown() throws IOException;
}
