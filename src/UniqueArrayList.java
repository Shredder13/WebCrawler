import java.util.ArrayList;
import java.util.Collection;

public class UniqueArrayList<T> {

	private final Object lock = new Object();
	private ArrayList<T> list;
	
	public UniqueArrayList () {
		list = new ArrayList<>();
	}
	
	public boolean addIfNotExists(T object) {
		synchronized (lock) {
			if (!list.contains(object)) {
				list.add(object);
				return true;
			}
			
			return false;
		}
	}
	
	public void addAllIfNotExists(Collection<T> objects) {
		synchronized (lock) {
			for (T obj : objects) {
				addIfNotExists(obj);
			}
		}
	}
	
	public T get(int index) {
		synchronized (lock) {
			if (index >= list.size() || index < 0) {
				return null;
			}
			
			return list.get(index);
		}
	}
	
	public boolean remove(T object) {
		synchronized (lock) {
			return list.remove(object);
		}
	}

	public boolean contains(T object) {
		synchronized (lock) {
			return (list.contains(object));
		}
	}
}
