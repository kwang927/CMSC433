package messages;

import akka.actor.ActorRef;

public class WhoHasResourceRequestMsg {	
	private final String resource_name;
	private final ActorRef sender;
	private final Object related_msg;
	
	
	public WhoHasResourceRequestMsg (String resource, ActorRef sender, Object related_msg) {
		this.resource_name = resource;
		this.sender = sender;
		this.related_msg = related_msg;
	}
	
	public String getResourceName () {
		return resource_name;
	}
	
	public ActorRef getSender() {
		return sender;
	}
	
	public Object getRelated_msg() {
		return related_msg;
	}
	
	@Override 
	public String toString () {
		return "Who has " + resource_name + "?";
	}
}
