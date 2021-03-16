package cmsc433;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MainServer {
	public static LinkedList<Order> order_queue;
	public static Object order_queue_lock= new Object();
	private static int orderNum = 0;
	
	
	
	public static ArrayList<Table> table_list;
	public static Object table_lock = new Object();
	public static int tableNum;
	
	public static ArrayList<Integer> finished_orders;
	public static Object finish_order_lock = new Object();
	
	public static Machines fryers;
	public static Machines ovens;
	public static Machines grill_Presses;
	public static Machines soda_Machines;
	
	
	public static void init(int table_number, int machineCount) {
		order_queue = new LinkedList<Order>();
		table_list = new ArrayList<Table>();
		finished_orders = new ArrayList<Integer>();
		for(int i=0; i<table_number; i++) {
			table_list.add(new Table());
		}
		tableNum = table_number;
		
		fryers = new Machines(Machines.MachineType.fryers,FoodType.fries,machineCount);
		ovens = new Machines(Machines.MachineType.ovens,FoodType.pizza,machineCount);
		grill_Presses = new Machines(Machines.MachineType.grillPresses,FoodType.subs,machineCount);
		soda_Machines = new Machines(Machines.MachineType.sodaMachines,FoodType.soda,machineCount);
		
		
	}
	
	public static int placeOrder(List<Food> order) {
		
			orderNum++;
			order_queue.add(new Order(order, orderNum));
			return orderNum;
		
		
	}
	
	public static Order takeOrder() {
		
			return order_queue.poll();
		
	}
	
	public static boolean tryTable(int i) {
		Table this_table = table_list.get(i);
		if(this_table.captured == false) {
			this_table.sit();
			return true;
		}else {
			return false;
		}
		
		
	}
	
	
	public static void finishOrder(int orderNum) {
		
			finished_orders.add(orderNum);
		
		
	}
	
	public static void turnOffMachines() {
		Simulation.logEvent(SimulationEvent.machinesEnding(fryers));
		Simulation.logEvent(SimulationEvent.machinesEnding(ovens));
		Simulation.logEvent(SimulationEvent.machinesEnding(grill_Presses));
		Simulation.logEvent(SimulationEvent.machinesEnding(soda_Machines));
	}
	
	public static boolean checkWhetherFinished(int orderNum) {
		
			return finished_orders.contains(orderNum);
		
		
	}
	
	

}
