import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StressTest implements Runnable {

	private ExecutorService pool;
	private static final int NUM_TRD = 200;

	public StressTest() {
		pool = Executors.newFixedThreadPool(NUM_TRD);
	}

	@Override
	public void run() {
		Thread thr1 = new Thread(new Runnable() {

			@Override
			public void run() {
				execute();
			}
		});

		Thread thr2 = new Thread(new Runnable() {

			@Override
			public void run() {
				execute();
			}
		});
		
		thr1.start();
		thr2.start();
		
		try {
			thr1.join();
			thr2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}

	private void execute() {
	//	while (true) {
			pool.submit(new FibTask());

	//		try {
	//			Thread.sleep(700);
	//		} catch (InterruptedException e) {
	//			e.printStackTrace();
	//		}
	//	}
	}

	public static void main(String[] args) {
		new Thread(new StressTest()).start();
	}

	class FibTask implements Callable<Void> {

		@Override
		public Void call() throws Exception {
			long t1 = System.currentTimeMillis();

			System.out.println("Res: " + fib(35) + "; Time: "
					+ (System.currentTimeMillis() - t1) + "ms");
			return null;
		}

		private long fib(int n) {
			if (n <= 1)
				return 1;
			else
				return fib(n - 1) + fib(n - 2);
		}
	}
}
