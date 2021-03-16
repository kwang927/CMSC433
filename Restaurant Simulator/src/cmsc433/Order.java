package cmsc433;

import java.util.ArrayList;
import java.util.List;

public class Order {
	
	public List<Food> this_order;
	public int this_order_number;
	
	public Order(List<Food> order, int num){
		this_order=  order;
		this_order_number = num;
	}
	
	

}
