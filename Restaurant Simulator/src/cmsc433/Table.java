package cmsc433;

public class Table {
	public boolean captured;
	
	public Table() {
		captured = false;
	}
	
	public synchronized void stand() {
		if(captured!=true) {
			throw new RuntimeException("Should not happen when stand up");
		}
		captured=  false;
	}
	
	public synchronized int sit() {
		if(captured) {
			return 0;
		}else {
			captured = true;
			return 1;
		}
		
	}

}
