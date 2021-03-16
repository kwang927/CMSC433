package cmsc433;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooks are simulation actors that have at least one field, a name.
 * When running, a cook attempts to retrieve outstanding orders placed
 * by Customer and process them.
 */
public class Cook implements Runnable {
	private final String name;
	
	public int completedOrder = 0;
	
	public int numCustomer;
	
	public Object completeOrderLock = new Object();

	/**
	 * You can feel free modify this constructor. It must
	 * take at least the name, but may take other parameters
	 * if you would find adding them useful.
	 *
	 * @param: the name of the cook
	 */
	public Cook(String name, int numCustomer) {
		this.name = name;
		this.numCustomer = numCustomer;
	}

	public String toString() {
		return name;
	}

	/**
	 * This method executes as follows. The cook tries to retrieve
	 * orders placed by Customers. For each order, a List<Food>, the
	 * cook submits each Food item in the List to an appropriate
	 * Machine type, by calling makeFood(). Once all machines have
	 * produced the desired Food, the order is complete, and the Customer
	 * is notified. The cook can then go to process the next order.
	 * If during its execution the cook is interrupted (i.e., some
	 * other thread calls the interrupt() method on it, which could
	 * raise InterruptedException if the cook is blocking), then it
	 * terminates.
	 */
	public void run() {

		Simulation.logEvent(SimulationEvent.cookStarting(this));
		try {
			while (true) {
				
				
				// YOUR CODE GOES HERE..
				if(completedOrder >= this.numCustomer) {
					throw new InterruptedException();
				}
				
				Order this_order;
				synchronized(MainServer.order_queue_lock) {
					if(MainServer.order_queue.isEmpty()) {
						MainServer.order_queue_lock.wait();
						continue;
					}
					this_order = MainServer.takeOrder();
					if(this_order == null) {
						continue;
					}
					Simulation.logEvent(SimulationEvent.cookReceivedOrder(this, this_order.this_order, this_order.this_order_number));
				}
				
				List<Food> current_food_list = this_order.this_order;
				List<Machines.CookAnItem> foodCAI = new ArrayList<Machines.CookAnItem>();
				List<Thread> foodThreads = new ArrayList<Thread>();
				
				for(int i=0; i< current_food_list.size();i++) {
					if(current_food_list.get(i).name.equals(FoodType.fries.name)) {
						foodCAI.add(MainServer.fryers.makeFood());
					}else if(current_food_list.get(i).name.equals(FoodType.pizza.name)) {
						foodCAI.add(MainServer.ovens.makeFood());
					}else if(current_food_list.get(i).name.equals(FoodType.soda.name)) {
						foodCAI.add(MainServer.soda_Machines.makeFood());
					}else {
						foodCAI.add(MainServer.grill_Presses.makeFood());
					}
					Simulation.logEvent(SimulationEvent.cookStartedFood(this, foodCAI.get(i).type, this_order.this_order_number));
					Thread current_food_thread = new Thread(foodCAI.get(i));
					foodThreads.add(current_food_thread);
					current_food_thread.start();
					
				}
				
				//System.out.println("Here");
				
				for(int i=0; i<current_food_list.size(); i++) {
					Machines.CookAnItem cai = foodCAI.get(i);
					//System.out.println("There");
					foodThreads.get(i).join();
					
					Simulation.logEvent(SimulationEvent.cookFinishedFood(this, cai.type, this_order.this_order_number));
				}
				
				
				// Order finished
				
				Simulation.logEvent(SimulationEvent.cookCompletedOrder(this, this_order.this_order_number));
				synchronized(this.completeOrderLock) {
					this.completedOrder++;
				}
				
				synchronized(MainServer.finish_order_lock) {
					MainServer.finishOrder(this_order.this_order_number);
					MainServer.finish_order_lock.notifyAll();
				}



				//throw new InterruptedException(); // REMOVE THIS
			}
		} catch (InterruptedException e) {
			// This code assumes the provided code in the Simulation class
			// that interrupts each cook thread when all customers are done.
			// You might need to change this if you change how things are
			// done in the Simulation class.
			Simulation.logEvent(SimulationEvent.cookEnding(this));
		}
	}
}
