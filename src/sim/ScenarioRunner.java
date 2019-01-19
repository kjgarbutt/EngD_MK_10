package sim;

public class ScenarioRunner {
	
	public static void main(String [] args) {
		
		for(int x = 0; x < 10; x++) {
			
			EngD_MK_9 dummySim = new EngD_MK_9(System.currentTimeMillis());

			System.out.println("NEW RUN...." + x);
			System.out.println("Loading...");

			dummySim.start();

			System.out.println("Running...");

			while(!dummySim.schedule.scheduleComplete() && dummySim.schedule.getTime() < 288 * 3) {
				dummySim.schedule.step(dummySim);
			}

			dummySim.finish();

			System.out.println("...run finished");
		}
		
	}
}