package cmsc433;

import java.util.List;

/**
 * Customers are simulation actors that have two fields: a name, and a list
 * of Food items that constitute the Customer's order. When running, an
 * customer attempts to enter the Ratsie's (only successful if the
 * Ratsie's has a free table), place its order, and then leave the
 * Ratsie's when the order is complete.
 */
public class Customer implements Runnable {
	// JUST ONE SET OF IDEAS ON HOW TO SET THINGS UP...
	private final String name;
	private final List<Food> order;
	private final int orderNum;
	
	public Table this_table;
	
	public static Object customer_lock = new Object();

	private static int runningCounter = 0;

	/**
	 * You can feel free modify this constructor. It must take at
	 * least the name and order but may take other parameters if you
	 * would find adding them useful.
	 */
	public Customer(String name, List<Food> order) {
		this.name = name;
		this.order = order;
		this.orderNum = ++runningCounter;
	}

	public String toString() {
		return name;
	}
	
	public void try_to_sit() {
		while(true) {
			int tableNum = MainServer.tableNum;
			synchronized(customer_lock){
				for(int i=0; i<tableNum; i++) {
					if(MainServer.tryTable(i)) {
						this_table=MainServer.table_list.get(i);
						return;
						// Sit down, do not need to wait
					}
				}
				try {
					customer_lock.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					continue;
					// should not happen
				}
			
			}
			
		}
	}
	
	public void try_to_leave() {
		synchronized(customer_lock) {
			this_table.stand();
			Simulation.logEvent(SimulationEvent.customerLeavingRatsies(this));
			customer_lock.notifyAll();
		}
		
	}

	/**
	 * This method defines what an Customer does: The customer attempts to
	 * enter the Ratsie's (only successful when the Ratsie's has a
	 * free table), place its order, and then leave the Ratsie's
	 * when the order is complete.
	 */
	public void run() {
		// YOUR CODE GOES HERE...
		
		Simulation.logEvent(SimulationEvent.customerStarting(this));
		
		// The customer should try to sit down
		// Need to add sth here
		try_to_sit();
		
		Simulation.logEvent(SimulationEvent.customerEnteredRatsies(this));
		
		
		int order_num=0;
		// The customer submit the order
		synchronized(MainServer.order_queue_lock) {
			order_num = MainServer.placeOrder(order);
			Simulation.logEvent(SimulationEvent.customerPlacedOrder(this, order, order_num));
			MainServer.order_queue_lock.notifyAll();
			
		}
		
		
			
		// The customer wait for the order
		
		synchronized(MainServer.finish_order_lock) {
			while(!MainServer.checkWhetherFinished(order_num)) {
				try {
					MainServer.finish_order_lock.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					continue;
				}
			}
			MainServer.finish_order_lock.notifyAll();
		}
		
		// After have the order
		Simulation.logEvent(SimulationEvent.customerReceivedOrder(this, order, order_num));
		
		// Customer try to stand up
		
		try_to_leave();
		



	}
	
	
}
