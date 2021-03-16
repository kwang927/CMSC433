package cmsc433.p3;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;



public class StudentMTMazeSolver extends SkippingMazeSolver
{

	

	public StudentMTMazeSolver(Maze maze){
		super(maze);
	}

	public List<Direction> solve() 
	{
		// TODO: Implement your code here
		
		List<Future<List<Direction>>> result_list = new LinkedList<Future<List<Direction>>>();
		LinkedList<DFS_Consumer> task_list = new LinkedList<DFS_Consumer>();
		List<Direction> result = null;
		// Note that the directions in choice is always less than 4
		ExecutorService thread_pool = Executors.newFixedThreadPool(4);
		try{
			Choice start_choice = firstChoice(maze.getStart());
			
			int s = start_choice.choices.size();
			
			for(int i = 0; i < s; i++){
				Choice currChoice = follow(start_choice.at, start_choice.choices.peek());
				
				task_list.add(new DFS_Consumer(currChoice, start_choice.choices.pop()));
				
			}
		}catch (SolutionFound e){
			System.out.println("Should rarely happen");
		}
		try {
//			System.out.println(task_list.size());
			result_list = thread_pool.invokeAll(task_list);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		thread_pool.shutdown();
		for(Future<List<Direction>> res : result_list){
			try {
				
				if(res.get() != null)
					result = res.get();
					
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//System.out.println("Why am I not surprised that the following is faster?");
		return result;
	}

	private class DFS_Consumer implements Callable<List<Direction>>{
		Choice start_choice;
		Direction directions;
		public DFS_Consumer(Choice start, Direction dirs){
			this.start_choice = start;
			this.directions = dirs;
			
		}
		
		// This part is copied from the DFS Solver provided by the project initially
		@Override
		public List<Direction> call() {
			// TODO Auto-generated method stub
			LinkedList<Choice> choiceStack = new LinkedList<Choice>();
			Choice ch;

			try{
				choiceStack.push(start_choice);
				
				while(!choiceStack.isEmpty()){
					ch = choiceStack.peek();
					if(ch.isDeadend()){
						//backtrack
						choiceStack.pop();
						if (!choiceStack.isEmpty()) choiceStack.peek().choices.pop();
						continue;
					}
					choiceStack.push(follow(ch.at, ch.choices.peek()));
				}
				// no solution
				return null;
			}catch (SolutionFound e){
				Iterator<Choice> iter = choiceStack.iterator();
	            LinkedList<Direction> solutionPath = new LinkedList<Direction>();
	        
	           
	            while (iter.hasNext())
	            {
	            	ch = iter.next();
	                solutionPath.push(ch.choices.peek());
	            }
	            solutionPath.push(directions);
	            if (maze.display != null) maze.display.updateDisplay();
	            
	            return pathToFullPath(solutionPath);
			}

		}

	}
}