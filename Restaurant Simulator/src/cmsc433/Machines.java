package cmsc433;

import java.util.concurrent.Semaphore;

/**
 * Machines are used to make different kinds of Food. Each Machine type makes
 * just one kind of Food. Each machine type has a count: the set of machines of
 * that type can make that many food items in parallel. If the machines are
 * asked to produce a food item beyond its count, the requester blocks. Each
 * food item takes at least item.cookTime10S seconds to produce. In this
 * simulation, use Thread.sleep(item.cookTime10S) to simulate the actual cooking
 * time.
 */
public class Machines {

	public enum MachineType {
		sodaMachines, fryers, grillPresses, ovens
	};

	// Converts Machines instances into strings based on MachineType.
	public String toString() {
		switch (machineType) {
			case sodaMachines:
				return "Soda Machines";
			case fryers:
				return "Fryers";
			case grillPresses:
				return "Grill Presses";
			case ovens:
				return "Ovens";
			default:
				return "INVALID MACHINE TYPE";
		}
	}

	public final MachineType machineType;
	public final Food machineFoodType;
	public final int machine_count;
	// YOUR CODE GOES HERE...
	
	private final Semaphore current_machine_situation;



	/**
	 * The constructor takes at least the name of the machines, the Food item they
	 * make, and their count. You may extend it with other arguments, if you wish.
	 * Notice that the constructor currently does nothing with the count; you must
	 * add code to make use of this field (and do whatever initialization etc. you
	 * need).
	 */
	public Machines(MachineType machineType, Food foodIn, int countIn) {
		this.machineType = machineType;
		this.machineFoodType = foodIn;
		machine_count = countIn;
		

		// YOUR CODE GOES HERE...
		current_machine_situation = new Semaphore(machine_count);
		Simulation.logEvent(SimulationEvent.machinesStarting(this, foodIn, countIn));



	}

	/**
	 * This method is called by a Cook in order to make the Machines' food item. You
	 * can extend this method however you like, e.g., you can have it take extra
	 * parameters or return something other than Object. It should block if the
	 * machines are currently busy (i.e. #items == count). If not, the method should
	 * return, so the Cook making the call can proceed. You will need to implement
	 * some means to notify the calling Cook when the food item is finished.
	 */
	public CookAnItem makeFood() throws InterruptedException {
		current_machine_situation.acquire();
		return new CookAnItem(machineFoodType);



		
	}
	private Machines out_ref() {
		return this;
	}

	// THIS MIGHT BE A USEFUL METHOD TO HAVE AND USE BUT IS JUST ONE IDEA
	public class CookAnItem implements Runnable {
		public boolean Done = false;
		
		public Food type;
		
		public CookAnItem(Food type) {
			this.type = type;
		}
		
		public void run() {
			Simulation.logEvent(SimulationEvent.machinesCookingFood(out_ref(), type));
			try {
				//YOUR CODE GOES HERE...
				Thread.sleep(machineFoodType.cookTime10S);
				
				
				// throw new InterruptedException(); // REMOVE THIS
			} catch(InterruptedException e) { }
			Simulation.logEvent(SimulationEvent.machinesDoneFood(out_ref(), type));
			Done = true;
			current_machine_situation.release();
			//System.out.println("Machine finished "+type);
		}
	}
}
