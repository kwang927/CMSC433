package actors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;


import enums.*;
import messages.*;
import utils.*;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import akka.actor.AbstractActor;

public class ResourceManagerActor extends AbstractActor {

	private ActorRef logger; // Actor to send logging messages to

	/**
	 * Props structure-generator for this class.
	 * 
	 * @return Props structure
	 */
	static Props props(ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}

	/**
	 * Factory method for creating resource managers
	 * 
	 * @param logger Actor to send logging messages to
	 * @param system Actor system in which manager will execute
	 * @return Reference to new manager
	 */
	public static ActorRef makeResourceManager(ActorRef logger, ActorSystem system) {
		ActorRef newManager = system.actorOf(props(logger));
		return newManager;
	}

	/**
	 * Sends a message to the Logger Actor
	 * 
	 * @param msg The message to be sent to the logger
	 */
	public void log(LogMsg msg) {
		logger.tell(msg, getSelf());
	}

	/**
	 * Constructor
	 * 
	 * @param logger Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		super();
		this.logger = logger;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AddInitialLocalResourcesRequestMsg.class, this::onAddInitialLocalResourcesRequestMsg)
				.match(AddRemoteManagersRequestMsg.class, this::onAddRemoteManagersRequestMsg)
				.match(AddLocalUsersRequestMsg.class, this::onAddLocalUsersRequestMsg)
				.match(AccessRequestMsg.class, this::onAccessRequestMsg)
				.match(ManagementRequestMsg.class, this::onManagementRequestMsg)
				.match(AccessReleaseMsg.class, this::onAccessReleaseMsg)
				.match(WhoHasResourceRequestMsg.class, this::onWhoHasResourceRequestMsg)
				.match(WhoHasResourceResponseMsg.class, this::onWhoHasResourceResponseMsg).build();
	}

	public ArrayList<ResourceShell> resources_list = new ArrayList<ResourceShell>();


	public ArrayList<Object> awaiting_conformation_list = new ArrayList<Object>();

	public ArrayList<ActorRef> other_managers = new ArrayList<ActorRef>();

	public ArrayList<ActorRef> local_user = new ArrayList<ActorRef>();

	public HashMap<String, ResourceShell> name_resource_map = new HashMap<String, ResourceShell>();

	// public ArrayList<ResourceShell> plan_to_disable = new
	// ArrayList<ResourceShell>();

	public HashMap<ActorRef, ArrayList<String>> manager_resource_map = new HashMap<ActorRef, ArrayList<String>>();

	// Some request on a remote resource need to wait for the response from other
	// resource manager
	public HashMap<Object, ArrayList<ActorRef>> to_do_msg = new HashMap<Object, ArrayList<ActorRef>>();

	private class ResourceShell {
		Resource r;
		boolean accepting_new_access = true;
		LinkedList<AccessRequestMsg> queue = new LinkedList<AccessRequestMsg>();
		ArrayList<ActorRef> access_actors = new ArrayList<ActorRef>();
		AccessType accessType;
		ResourceStatus resourceStatus;

		HashMap<ActorRef, ArrayList<AccessType>> actor_holding_accesses = new HashMap<ActorRef, ArrayList<AccessType>>();

		ManagementRequestMsg managementMsg = null;

	}

	public void onAddRemoteManagersRequestMsg(AddRemoteManagersRequestMsg msg) {
		ArrayList<ActorRef> remoteActors = new ArrayList<ActorRef>();
		if (msg != null) {
			remoteActors.addAll(msg.getManagerList());
			for (ActorRef ar : remoteActors) {
				if (!ar.equals(getSelf())) {
					other_managers.add(ar);
				}
				// initiate the manager_resource_map

				manager_resource_map.put(ar, new ArrayList<String>());

			}

			AddRemoteManagersResponseMsg reMsg = new AddRemoteManagersResponseMsg(msg);
			getSender().tell(reMsg, getSelf());

		} else {

			System.out.println("Get null at AddRemoteManagersRequestMsg");
		}

	}

	public void onAddInitialLocalResourcesRequestMsg(AddInitialLocalResourcesRequestMsg msg) {
		ArrayList<Resource> resources = new ArrayList<Resource>();

		resources.addAll(msg.getLocalResources());
		for (Resource resource : resources) {
			LogMsg changeStatusMsg = LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resource.name,
					ResourceStatus.ENABLED);
			logger.tell(changeStatusMsg, getSelf());

			ResourceShell rs = new ResourceShell();
			rs.r = resource;

			rs.r.enable();

			rs.accepting_new_access = true;
			// rs.access_actors = null;
			rs.accessType = AccessType.CONCURRENT_READ;
			rs.resourceStatus = ResourceStatus.ENABLED;
			resources_list.add(rs);
			

			name_resource_map.put(resource.getName(), rs);
			
			

			LogMsg createResouceMsg = LogMsg.makeLocalResourceCreatedLogMsg(getSelf(), resource.name);
			logger.tell(createResouceMsg, getSelf());

		}

		AddInitialLocalResourcesResponseMsg reMsg = new AddInitialLocalResourcesResponseMsg(msg);

		getSender().tell(reMsg, getSelf());

	}

	public void onAddLocalUsersRequestMsg(AddLocalUsersRequestMsg msg) {
		ArrayList<ActorRef> users = msg.getLocalUsers();
		local_user.addAll(users);
		AddLocalUsersResponseMsg reMsg = new AddLocalUsersResponseMsg(msg);
		getSender().tell(reMsg, getSelf());


	}

	public void onAccessRequestMsg(AccessRequestMsg msg) {

		ActorRef reply_to = msg.getReplyTo();
		AccessRequest ar = msg.getAccessRequest();
		String resource_name = ar.getResourceName();
		AccessRequestType access_request_type = ar.getType();

		LogMsg received_log = LogMsg.makeAccessRequestReceivedLogMsg(reply_to, getSelf(), ar);
		logger.tell(received_log, getSelf());
		

		if (name_resource_map.containsKey(resource_name)) {
			// local case
			ResourceShell rs = name_resource_map.get(resource_name);

			if (rs.accepting_new_access == false || rs.resourceStatus == ResourceStatus.DISABLED) {
				// The resource is disabled or not accepting new access

			

				AccessRequestDeniedMsg denied_message = new AccessRequestDeniedMsg(msg,
						AccessRequestDenialReason.RESOURCE_DISABLED);
				reply_to.tell(denied_message, getSelf());
				
				

				LogMsg dinied_log = LogMsg.makeAccessRequestDeniedLogMsg(reply_to, getSelf(), ar,
						AccessRequestDenialReason.RESOURCE_DISABLED);
				logger.tell(dinied_log, getSelf());
				return;

			}
			

			if (rs.access_actors != null && rs.access_actors.contains(reply_to)) {
				// reentrant case
				
				

				if (access_request_type == AccessRequestType.CONCURRENT_READ_BLOCKING
						|| access_request_type == AccessRequestType.CONCURRENT_READ_NONBLOCKING) {
					LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
					logger.tell(granted_log, getSelf());

					AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
					reply_to.tell(granted_msg, getSelf());

					rs.access_actors.add(reply_to);
					/* Note that access_actors can have replicate of one user */
					rs.actor_holding_accesses.get(reply_to).add(AccessType.CONCURRENT_READ);

				} else {
					
					// The access type must be exclusive write
					if (rs.accessType == AccessType.CONCURRENT_READ) {
						int count = 0;
						for(ActorRef holder:rs.access_actors) {
							if(holder==reply_to) {
								count++;
							}
						}
						
						if(count==rs.access_actors.size()) {
							// This is the case that the request should be granted
							rs.accessType = AccessType.EXCLUSIVE_WRITE;
							AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
							reply_to.tell(granted_msg, getSelf());

							LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
							logger.tell(granted_log, getSelf());

							rs.access_actors.add(reply_to);
							/* Note that access_actors can have replicate of one user */
							rs.actor_holding_accesses.get(reply_to).add(AccessType.EXCLUSIVE_WRITE);
							return;
						}
						
						
						if (access_request_type == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING) {
							rs.queue.add(msg);
						} else {
							// access_request_type is non_blocking

							LogMsg dinied_log = LogMsg.makeAccessRequestDeniedLogMsg(reply_to, getSelf(), ar,
									AccessRequestDenialReason.RESOURCE_BUSY);
							logger.tell(dinied_log, getSelf());

							AccessRequestDeniedMsg denied_message = new AccessRequestDeniedMsg(msg,
									AccessRequestDenialReason.RESOURCE_BUSY);
							reply_to.tell(denied_message, getSelf());
						}

					} else {
						AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
						reply_to.tell(granted_msg, getSelf());

						LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
						logger.tell(granted_log, getSelf());

						rs.access_actors.add(reply_to);
						/* Note that access_actors can have replicate of one user */
						rs.actor_holding_accesses.get(reply_to).add(AccessType.EXCLUSIVE_WRITE);
					}

				}
			
				return;
			}

			if (access_request_type == AccessRequestType.CONCURRENT_READ_BLOCKING) {
				if (rs.accessType == AccessType.CONCURRENT_READ || rs.access_actors.isEmpty()) {
					// log here
					AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
					reply_to.tell(granted_msg, getSelf());
					LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
					logger.tell(granted_log, getSelf());

					rs.accessType = AccessType.CONCURRENT_READ;
					rs.access_actors.add(reply_to);
					if (!rs.actor_holding_accesses.containsKey(reply_to)) {
						ArrayList<AccessType> new_list = new ArrayList<AccessType>();
						new_list.add(AccessType.CONCURRENT_READ);
						rs.actor_holding_accesses.put(reply_to, new_list);
					} else {
						rs.actor_holding_accesses.get(reply_to).add(AccessType.CONCURRENT_READ);
					}

				} else {
					rs.queue.add(msg);
				}
				return;

			} else if (access_request_type == AccessRequestType.CONCURRENT_READ_NONBLOCKING) {

				if (rs.accessType == AccessType.CONCURRENT_READ || rs.access_actors.isEmpty()) {
					AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
					reply_to.tell(granted_msg, getSelf());

					// log here
					LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
					logger.tell(granted_log, getSelf());
					rs.accessType = AccessType.CONCURRENT_READ;
					rs.access_actors.add(reply_to);

					if (!rs.actor_holding_accesses.containsKey(reply_to)) {
						ArrayList<AccessType> new_list = new ArrayList<AccessType>();
						new_list.add(AccessType.CONCURRENT_READ);
						rs.actor_holding_accesses.put(reply_to, new_list);
					} else {
						rs.actor_holding_accesses.get(reply_to).add(AccessType.CONCURRENT_READ);
					}

				} else {
					LogMsg dinied_log = LogMsg.makeAccessRequestDeniedLogMsg(reply_to, getSelf(), ar,
							AccessRequestDenialReason.RESOURCE_BUSY);
					logger.tell(dinied_log, getSelf());

					AccessRequestDeniedMsg denied_message = new AccessRequestDeniedMsg(msg,
							AccessRequestDenialReason.RESOURCE_BUSY);
					reply_to.tell(denied_message, getSelf());

				}

				return;

			} else if (access_request_type == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING) {

				if (rs.access_actors.isEmpty()) {
					AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
					reply_to.tell(granted_msg, getSelf());
					LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
					logger.tell(granted_log, getSelf());
					rs.accessType = AccessType.EXCLUSIVE_WRITE;
					rs.access_actors.add(reply_to);

					if (!rs.actor_holding_accesses.containsKey(reply_to)) {
						ArrayList<AccessType> new_list = new ArrayList<AccessType>();
						new_list.add(AccessType.EXCLUSIVE_WRITE);
						rs.actor_holding_accesses.put(reply_to, new_list);
					} else {
						rs.actor_holding_accesses.get(reply_to).add(AccessType.EXCLUSIVE_WRITE);
					}

				} else {
					rs.queue.add(msg);
				}

				return;

			} else if (access_request_type == AccessRequestType.EXCLUSIVE_WRITE_NONBLOCKING) {

				if (rs.access_actors.isEmpty()) {
					AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(msg);
					reply_to.tell(granted_msg, getSelf());
					LogMsg granted_log = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, getSelf(), ar);
					logger.tell(granted_log, getSelf());
					rs.accessType = AccessType.EXCLUSIVE_WRITE;
					rs.access_actors.add(reply_to);

					if (!rs.actor_holding_accesses.containsKey(reply_to)) {
						ArrayList<AccessType> new_list = new ArrayList<AccessType>();
						new_list.add(AccessType.EXCLUSIVE_WRITE);
						rs.actor_holding_accesses.put(reply_to, new_list);
					} else {
						rs.actor_holding_accesses.get(reply_to).add(AccessType.EXCLUSIVE_WRITE);
					}

				} else {
					LogMsg dinied_log = LogMsg.makeAccessRequestDeniedLogMsg(reply_to, getSelf(), ar,
							AccessRequestDenialReason.RESOURCE_BUSY);
					logger.tell(dinied_log, getSelf());

					AccessRequestDeniedMsg denied_message = new AccessRequestDeniedMsg(msg,
							AccessRequestDenialReason.RESOURCE_BUSY);
					reply_to.tell(denied_message, getSelf());

				}

				return;

			} else {
				System.out.println("There are some problem");

			}

			return;

		} else {

			// The remote case:

			ActorRef rm_manager_with_resource = findResource(resource_name, msg);

			if (rm_manager_with_resource == null) {
				// This case should be handled in onWhoHasResourceResponseMsg
				return;
			}

			if (rm_manager_with_resource == getSelf()) {
				System.out.println("Problem on distinguish a remote case in AccessRequest");
				return;

			}

			// In this case, rm_manager_with_resource is a known remote manager

			LogMsg forward_access_request_log = LogMsg.makeAccessRequestForwardedLogMsg(getSelf(),
					rm_manager_with_resource, ar);
			logger.tell(forward_access_request_log, getSelf());

			rm_manager_with_resource.tell(msg, getSelf());

		}

	}

	public void onManagementRequestMsg(ManagementRequestMsg msg) {
		

		ManagementRequest mr = msg.getRequest();
		ManagementRequestType this_type = mr.getType();
		ActorRef reply_to = msg.getReplyTo();
		String resource_name = mr.getResourceName();
		ResourceShell rs = name_resource_map.get(resource_name);

		LogMsg received_log = LogMsg.makeManagementRequestReceivedLogMsg(reply_to, getSelf(), mr);
		logger.tell(received_log, getSelf());

		if (!name_resource_map.containsKey(resource_name) || rs==null) {

			ActorRef rm_manager_with_resource = findResource(resource_name, msg);

			if (rm_manager_with_resource == null) {
				// This case should be handled in onWhoHasResourceResponseMsg
				return;
			}

			if (rm_manager_with_resource == getSelf()) {
			}

			// The resource is known so tell the remoteManger to deal with the request

			LogMsg management_request_forward_log = LogMsg.makeManagementRequestForwardedLogMsg(getSelf(),
					rm_manager_with_resource, mr);
			logger.tell(management_request_forward_log, getSelf());

			rm_manager_with_resource.tell(msg, getSelf());
			return ;

		}

		// Below is the local case
		if (this_type == ManagementRequestType.DISABLE) {
			

			
			if (rs.access_actors.contains(reply_to)) {
				ManagementRequestDeniedMsg reMsg = new ManagementRequestDeniedMsg(msg,
						ManagementRequestDenialReason.ACCESS_HELD_BY_USER);
				LogMsg logmsg = LogMsg.makeManagementRequestDeniedLogMsg(reply_to, getSelf(), mr,
						ManagementRequestDenialReason.ACCESS_HELD_BY_USER);
				logger.tell(logmsg, getSelf());
				reply_to.tell(reMsg, getSelf());
				return;
			} else {

				
				

				if (rs.resourceStatus == ResourceStatus.DISABLED) {
					ManagementRequestGrantedMsg remsg = new ManagementRequestGrantedMsg(msg);
					LogMsg granted_log = LogMsg.makeManagementRequestGrantedLogMsg(reply_to, getSelf(), mr);
					logger.tell(granted_log, getSelf());
					reply_to.tell(remsg, getSelf());
					return;
				}

				rs.accepting_new_access = false;
				

				for (AccessRequestMsg armsg : rs.queue) {
					AccessRequest ar = armsg.getAccessRequest();
					ActorRef this_reply_to = armsg.getReplyTo();
					AccessRequestDeniedMsg remsg = new AccessRequestDeniedMsg(armsg,
							AccessRequestDenialReason.RESOURCE_DISABLED);
					this_reply_to.tell(remsg, getSelf());
					/* some log here */

					LogMsg access_denied_log = LogMsg.makeAccessRequestDeniedLogMsg(this_reply_to, getSelf(), ar,
							AccessRequestDenialReason.RESOURCE_DISABLED);
					logger.tell(access_denied_log, getSelf());
				}

				
				rs.managementMsg = msg;
				rs.queue.clear();


				if (rs.access_actors.isEmpty()) {
					// no user is accessing this resource, just disable it
					LogMsg ManagementRequestGrantedMsg = LogMsg.makeManagementRequestGrantedLogMsg(reply_to, getSelf(),
							mr);
					logger.tell(ManagementRequestGrantedMsg, getSelf());
					LogMsg changed_status_log = LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resource_name,
							ResourceStatus.DISABLED);
					logger.tell(changed_status_log, getSelf());

					ManagementRequestGrantedMsg remsg = new ManagementRequestGrantedMsg(msg);
					reply_to.tell(remsg, getSelf());
					
					rs.r.disable();
					rs.resourceStatus=ResourceStatus.DISABLED;

				}
				
				return;

				/*
				 * In this case, there is a current user accessing the resource, and
				 * resourceStatus is changed to disabled in when dealing with accessRelease
				 */

			}

		} else if (this_type == ManagementRequestType.ENABLE) {
//			
			
			if (rs.resourceStatus == ResourceStatus.ENABLED) {
				ManagementRequestGrantedMsg remsg = new ManagementRequestGrantedMsg(msg);
				LogMsg granted_log = LogMsg.makeManagementRequestGrantedLogMsg(reply_to, getSelf(), mr);
				logger.tell(granted_log, getSelf());

				reply_to.tell(remsg, getSelf());
				return;
			} else {
				
				rs.accepting_new_access = true;
				LogMsg granted_log = LogMsg.makeManagementRequestGrantedLogMsg(reply_to, getSelf(), mr);
				logger.tell(granted_log, getSelf());
				LogMsg changed_log = LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resource_name,
						ResourceStatus.ENABLED);
				logger.tell(changed_log, getSelf());
				
				//System.out.println(changed_log);
				ManagementRequestGrantedMsg remsg= new ManagementRequestGrantedMsg(msg);
				rs.resourceStatus = ResourceStatus.ENABLED;
				rs.r.enable();
				reply_to.tell(remsg, reply_to);
				return;
			}
		}

	}

	public void onAccessReleaseMsg(AccessReleaseMsg msg) {
		AccessRelease ar = msg.getAccessRelease();
		ActorRef sender = msg.getSender();
		String resource_name = ar.getResourceName();
		AccessType type = ar.getType();

		LogMsg received_log = LogMsg.makeAccessReleaseReceivedLogMsg(sender, getSelf(), ar);
		logger.tell(received_log, getSelf());

		if (name_resource_map.containsKey(resource_name)) {
			// local issue

			ResourceShell rs = name_resource_map.get(resource_name);
			ArrayList<AccessType> actor_access_list = rs.actor_holding_accesses.get(sender);
			ArrayList<ActorRef> accessing_list = rs.access_actors;

			if (actor_access_list == null || accessing_list == null) {
				// need some log but do nothing
				// Ignore the release
				LogMsg ignored_log = LogMsg.makeAccessReleaseIgnoredLogMsg(sender, getSelf(), ar);
				logger.tell(ignored_log, getSelf());

				return;
			}

			if (actor_access_list.contains(type) && accessing_list.contains(sender)) {
				// Release some access

				LogMsg released_log = LogMsg.makeAccessReleasedLogMsg(sender, getSelf(), ar);
				logger.tell(released_log, getSelf());

				actor_access_list.remove(type);
				accessing_list.remove(sender);

				if (accessing_list.isEmpty() && actor_access_list.isEmpty()) {
					if (rs.accepting_new_access == false && rs.resourceStatus == ResourceStatus.ENABLED) {
						
						ActorRef management_sender = rs.managementMsg.getReplyTo();
						LogMsg managementGrant_log = LogMsg.makeManagementRequestGrantedLogMsg(management_sender, getSelf(),
								rs.managementMsg.getRequest());
						logger.tell(managementGrant_log, getSelf());

						LogMsg status_change_log = LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resource_name,
								ResourceStatus.DISABLED);
						logger.tell(status_change_log, getSelf());

						ManagementRequestGrantedMsg remsg = new ManagementRequestGrantedMsg(rs.managementMsg);
						management_sender.tell(remsg, getSelf());

						rs.resourceStatus = ResourceStatus.DISABLED;
						rs.r.disable();

						return;
					}
					if (!rs.queue.isEmpty()) {
						/* deque method */

						do {
							dequeue(rs.queue, rs, getSelf(), logger);

						} while (firstRead(rs.queue));

					}
					return;

				} else {
					// The case that there are still some user holding the access

					if (type == AccessType.EXCLUSIVE_WRITE) {

						if (!actor_access_list.isEmpty() && !actor_access_list.contains(AccessType.EXCLUSIVE_WRITE)) {
							// Need to consider the special case that the current user used to hold writing
							// and reading access, and now the writing access is released
							// In this case, should start allowing the reading access at the beginning of
							// the queue
							while (firstRead(rs.queue)) {
								dequeue(rs.queue, rs, getSelf(), logger);
							}

						}

					}

					return;

				}

			} else {
				// ignore the release
				LogMsg ignored_log = LogMsg.makeAccessReleaseIgnoredLogMsg(sender, getSelf(), ar);
				logger.tell(ignored_log, getSelf());

				return;
			}

		} else {
			// remote case

			ActorRef rem_manager_has_resource = findResource(resource_name, msg);

			if (rem_manager_has_resource == null) {
				// System.out.println("Cannot find the resource when releasing it");
				LogMsg ignored_log = LogMsg.makeAccessReleaseIgnoredLogMsg(sender, getSelf(), ar);
				logger.tell(ignored_log, getSelf());

				return;

			}

			if (rem_manager_has_resource.equals(getSelf())) {
				System.out.println("Something is wrong in remote case");
				return;
			}

			LogMsg forwar_log = LogMsg.makeAccessReleaseForwardedLogMsg(getSelf(), rem_manager_has_resource, ar);
			logger.tell(forwar_log, getSelf());
			rem_manager_has_resource.tell(msg, getSelf());

		}

	}

	public void onWhoHasResourceRequestMsg(WhoHasResourceRequestMsg msg) {

		String resource_to_find = msg.getResourceName();
		ActorRef reply_to = msg.getSender();

		if (name_resource_map.containsKey(resource_to_find)) {
			// The resources to find is a local resource for this resourceManager
			WhoHasResourceResponseMsg reMsg = new WhoHasResourceResponseMsg(msg, true, getSelf());
			reply_to.tell(reMsg, getSelf());

		} else {
			// The resources to find is not a local resource for this resourceManager

			WhoHasResourceResponseMsg reMsg = new WhoHasResourceResponseMsg(msg, false, getSelf());
			reply_to.tell(reMsg, getSelf());

		}

	}

	public void onWhoHasResourceResponseMsg(WhoHasResourceResponseMsg msg) {

		boolean found = msg.getResult();
		Object related_msg = msg.related_msg();
		ArrayList<ActorRef> waiting_list = to_do_msg.get(related_msg);
		ActorRef sender = msg.getSender();
		String resource_name = msg.getResourceName();

		if (waiting_list == null) {
			System.out.println("The waiting_list is null, which should never happen !!!!!!!!!!!!!!!!!!");
			return;
		}

		if (found) {
			// This response finds the resource

			//waiting_list.clear();

			ArrayList<String> resource_of_sender = manager_resource_map.get(sender);

			resource_of_sender.add(resource_name);

			LogMsg resource_discovered_log = LogMsg.makeRemoteResourceDiscoveredLogMsg(getSelf(), sender,
					resource_name);
			logger.tell(resource_discovered_log, getSelf());

			if (related_msg instanceof AccessRequestMsg) {
				AccessRequestMsg access_request_msg = (AccessRequestMsg) related_msg;
				AccessRequest request = access_request_msg.getAccessRequest();

				// log the forward accessRequestMsg
				LogMsg forward_access_log = LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), sender, request);
				logger.tell(forward_access_log, getSelf());
				// pass the accessRequestMsg to the remote manager

				sender.tell(access_request_msg, getSelf());

			} else if (related_msg instanceof ManagementRequestMsg) {
				ManagementRequestMsg management_request_msg = (ManagementRequestMsg) related_msg;
				ManagementRequest request = management_request_msg.getRequest();

				// log the forward ManagementRequestMsg
				LogMsg forward_management_log = LogMsg.makeManagementRequestForwardedLogMsg(getSelf(), sender, request);
				logger.tell(forward_management_log, getSelf());
				// pass the managementRequestMsg to the remote manager

				sender.tell(management_request_msg, getSelf());

			}

		} else {
			// This response does not find the resource

			// check the to_do_msg
			waiting_list.remove(sender);

			if (waiting_list.isEmpty()) {
				// Need to deny the related_Msg
				if (related_msg instanceof AccessRequestMsg) {
					AccessRequestMsg access_request_msg = (AccessRequestMsg) related_msg;
					ActorRef request_sender = access_request_msg.getReplyTo();
					AccessRequest request = access_request_msg.getAccessRequest();
					// log the access request deny
					LogMsg access_request_deny_log = LogMsg.makeAccessRequestDeniedLogMsg(request_sender, getSelf(),
							request, AccessRequestDenialReason.RESOURCE_NOT_FOUND);
					logger.tell(access_request_deny_log, getSelf());
					// Deny message to the user
					AccessRequestDeniedMsg replyMsg = new AccessRequestDeniedMsg(access_request_msg,
							AccessRequestDenialReason.RESOURCE_NOT_FOUND);
					request_sender.tell(replyMsg, getSelf());
					return;

				} else if (related_msg instanceof ManagementRequestMsg) {

					ManagementRequestMsg manage_request_msg = (ManagementRequestMsg) related_msg;
					ActorRef request_sender = manage_request_msg.getReplyTo();
					ManagementRequest request = manage_request_msg.getRequest();

					// log the management request deny
					LogMsg management_request_deny_log = LogMsg.makeManagementRequestDeniedLogMsg(request_sender,
							getSelf(), request, ManagementRequestDenialReason.RESOURCE_NOT_FOUND);
					logger.tell(management_request_deny_log, getSelf());

					// Deny message to the user
					ManagementRequestDeniedMsg replyMsg = new ManagementRequestDeniedMsg(manage_request_msg,
							ManagementRequestDenialReason.RESOURCE_NOT_FOUND);
					request_sender.tell(replyMsg, getSelf());

				}

				System.out.println("This should not happen, since the msg can only be accessReq or ManagementREq");

			}

			// Do nothing

			return;

		}

	}

	private static boolean firstRead(LinkedList<AccessRequestMsg> queue) {
		if (queue.isEmpty()) {
			return false;
		}

		AccessRequestMsg arm = queue.getFirst();
		AccessRequest ar = arm.getAccessRequest();
		// ActorRef reply_to = arm.getReplyTo();
		AccessRequestType type = ar.getType();

		return type == AccessRequestType.CONCURRENT_READ_BLOCKING
				|| type == AccessRequestType.CONCURRENT_READ_NONBLOCKING;
	}

	private static void dequeue(LinkedList<AccessRequestMsg> queue, ResourceShell r, ActorRef self, ActorRef logger) {
		AccessRequestMsg arm = queue.getFirst();
		AccessRequest ar = arm.getAccessRequest();
		ActorRef reply_to = arm.getReplyTo();
		AccessRequestType type = ar.getType();
		if (type == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING
				|| type == AccessRequestType.CONCURRENT_READ_NONBLOCKING) {
			AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(arm);
			reply_to.tell(granted_msg, self);
			LogMsg accreqGranted = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, self, ar);
			logger.tell(accreqGranted, self);

			r.access_actors.add(reply_to);
			if (r.actor_holding_accesses.containsKey(reply_to)) {
				r.actor_holding_accesses.get(reply_to).add(AccessType.EXCLUSIVE_WRITE);

			} else {
				ArrayList<AccessType> new_list = new ArrayList<AccessType>();
				new_list.add(AccessType.EXCLUSIVE_WRITE);
				r.actor_holding_accesses.put(reply_to, new_list);
			}
			queue.removeFirst();

			r.accessType = AccessType.EXCLUSIVE_WRITE;

		} else {
			// For the reading case

			AccessRequestGrantedMsg granted_msg = new AccessRequestGrantedMsg(arm);
			reply_to.tell(granted_msg, self);
			LogMsg accreqGranted = LogMsg.makeAccessRequestGrantedLogMsg(reply_to, self, ar);
			logger.tell(accreqGranted, self);

			r.access_actors.add(reply_to);
			if (r.actor_holding_accesses.containsKey(reply_to)) {
				r.actor_holding_accesses.get(reply_to).add(AccessType.CONCURRENT_READ);

			} else {
				ArrayList<AccessType> new_list = new ArrayList<AccessType>();
				new_list.add(AccessType.CONCURRENT_READ);
				r.actor_holding_accesses.put(reply_to, new_list);
			}

			queue.removeFirst();

			r.accessType = AccessType.CONCURRENT_READ;

		}

	}

	// If this method return null, it means the program is trying to find the
	// location of the resource
	public ActorRef findResource(String resource_name, Object related_msg) {
		if (name_resource_map.containsKey(resource_name)) {
			return getSelf();
			// This is the case that the resource_name going to be found is local

		} else {
			for (ActorRef remoteManager : manager_resource_map.keySet()) {
				if (manager_resource_map.get(remoteManager).contains(resource_name)) {
					return remoteManager;
				}
			}

			
			// In this case, the resource_name is not stored in the manager_resource_map
			// Need to find the resource

			// Send WhoHasResourceRequestMsg to all of the remote managers

			for (ActorRef remoteManager : other_managers) {
				WhoHasResourceRequestMsg finding_msg = new WhoHasResourceRequestMsg(resource_name, getSelf(),
						related_msg);
				remoteManager.tell(finding_msg, getSelf());
			}
			ArrayList<ActorRef> waiting_rm_mangers = new ArrayList<ActorRef>();
			waiting_rm_mangers.addAll(other_managers);

			to_do_msg.put(related_msg, waiting_rm_mangers);

			return null;

		}

	}

	// You may want to add data structures for managing local resources and users,
	// storing
	// remote managers, etc.
	//
	// REMEMBER: YOU ARE NOT ALLOWED TO CREATE MUTABLE DATA STRUCTURES THAT ARE
	// SHARED BY
	// MULTIPLE ACTORS!

	/*
	 * (non-Javadoc)
	 * 
	 * You must provide an implementation of the onReceive() method below.
	 * 
	 * @see akka.actor.AbstractActor#createReceive
	 */

	public void onReceive(Object msg) throws Exception {

	}

}
