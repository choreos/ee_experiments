
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StressTest1 implements Runnable {

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
		
		Thread thr3 = new Thread(new Runnable() {

			@Override
			public void run() {
				execute();
			}
		});
		
		thr1.start();
		thr2.start();
		thr3.start();
		
		try {
			thr1.join();
			thr2.join();
			thr3.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	private long fibo(int n) {
		if (n <= 1)
			return 1;
		else
			return fibo(n - 1) + fibo(n - 2);
	}

	private void execute() {
		while (true) {
			try {
				long t1 = System.currentTimeMillis();
				long ff = fibo(35);
				System.out.println("Res: " + ff + "; Time: "
						+ (System.currentTimeMillis() - t1) + "ms");
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		new Thread(new StressTest1()).start();
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

