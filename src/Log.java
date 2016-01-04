import java.util.Calendar;

public class Log {

	public static synchronized void d(String msg) {
		//TODO: Log in the format of "HH:MM:SS.MS : <msg>"
		Calendar c = Calendar.getInstance();
		int hour = c.get(Calendar.HOUR_OF_DAY);
		int minute = c.get(Calendar.MINUTE);
		int second = c.get(Calendar.SECOND);
		int millis = c.get(Calendar.MILLISECOND);
		
		String log = String.format("%d:%d:%d.%d (%d) : %s", hour, minute, second, millis, Thread.currentThread().getId(), msg);
		System.out.println(log);
	}
}
