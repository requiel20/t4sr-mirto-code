import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.XBeeException;

import uk.ac.mdx.cs.asip.JMirtoRobot;

/**
 * This class represent an agent able to:
 * <ul>
 * <li>Move in the environment;</li>
 * <li>Evaluate a binary physical property of the point in space they are in,
 * building a local belief about how much of environment has the same property;
 * </li>
 * <li>Communicate its belief to neighbours, and receive other agents' belief;
 * </li>
 * <li>Aggregate all the beliefs it possesses to estimate the belief of the
 * swarm;</li>
 * <li>Start and stop an action as a consequence of the belief of the swarm;
 * </li>
 * <li>Communicate that it is starting or stopping the action, and receive
 * similar communications from other agents.</li>
 * </ul>
 */
public class Agent {
	/**
	 * The values of this enumeration type are used to represent what the agent
	 * will broadcast to its neighbours:
	 * <ul>
	 * <li>0 : the agent will broadcast the gathered local beliefs;</li>
	 * <li>1 : the agent will broadcast a mandatory action change, in order to
	 * spread (de)activation through the swarm.</li>
	 * </ul>
	 */
	public static enum StateCode {
		ONE, ZERO;
	}

	/* True if the agent is acting */
	private volatile boolean isActionInProgress;

	/* Enable debug prints */
	private boolean debug = true;

	/* Enable xbee debug prints */
	private boolean debugXBEE = false;

	/* Integer identifier, taken from the xbee address */
	private Integer ID;

	/* Random number generator */
	private Random random;

	/* Internal time counter */
	private volatile int time;

	/* Timer for action broadcast */
	private Integer actionBroadcastTime;

	/* Writing proxy for isActionInProgress, coming from message reads */
	private volatile Boolean nextActionState;

	/* 1:broadcasting action, 0:broadcasting beliefs */
	private StateCode stateCode;

	/* The robot that this agent controls */
	private JMirtoRobot robot;

	/* The communication module */
	private XBeeDevice xBee;

	/* To start/stop the agent */
	private volatile boolean canMove;

	/* To start/stop broadcasting */
	private volatile boolean isBroadcasting;

	/* To store binary observations on the environment */
	private boolean[] memory;

	/* To always update the oldest observation once memory is full */
	private int lastMemoryWrite;

	/* Estimation of the world state (when acting) */
	private volatile float d;

	/* Estimation of cells that will be cleaned in this step (when acting) */
	private double c;

	/* Map to store the local beliefs */
	private Map<Integer, AgentBelief> beliefTable = Collections.synchronizedMap(new HashMap<>());

	/* Socket to send action state to a supervisor node */
	private Socket socket;

	/* Object stream to write objects on the socket */
	private ObjectOutputStream out;

	/*
	 * Boolean flag representing the validity of this agent's local belief. It
	 * is true if at least AgentsParameters.MEMORY_SIZE observations have been
	 * made
	 */
	private boolean isBeliefValid;

	/**
	 * Initialise an agent, also connecting it to the robot and the XBee
	 * communication module.
	 * 
	 * @param parameters the parameters to initialise this agent with
	 */
	public Agent(AgentParameters parameters) throws XBeeException {
		/* Connecting with the rest of the robot (sensors/motors) */
		robot = new JMirtoRobot();
		robot.initialize("/dev/ttyAMA0");
		robot.setup();

		System.out.println("Robot initialized");

		robot.clearLCDScreen();
		robot.writeLCDLine("trust4swarmrobotics", 0);
		robot.writeLCDLine("MIRTO", 2);
		robot.writeLCDLine("cleaning program", 3);

		/* Initializing the Xbee module */
		xBee = new XBeeDevice("/dev/ttyUSB0", 9600);
		while (!xBee.isOpen()) {
			xBee.open();
			xBee.addDataListener(new MessageReceiver(this));
		}

		System.out.println("XBEE initialized");

		/* Opening the socket */
		try {
			socket = new Socket(AgentParameters.SUPERVISOR_IP, AgentParameters.PORT_NUMBER);
			out = new ObjectOutputStream(socket.getOutputStream());
			System.out.println("Socket initialized, supervisor online");
		} catch (Exception e) {
			System.out.println("Supervisor offline");
			System.err.println(e.getMessage());
		}

		/* The agent ID is the XBee's 16 bit address */
		this.ID = Integer.parseInt(xBee.get16BitAddress().toString(), 16);

		System.out.println("This agent ID is " + this.ID);

		this.random = new Random();

		/*
		 * Set isActionInProgress to false and writes "not acting" on the screen
		 */
		this.stopAction();

		this.canMove = false;
		this.isBroadcasting = false;

		this.time = 0;

		this.actionBroadcastTime = -1;

		this.nextActionState = null;

		this.memory = new boolean[AgentParameters.MEMORY_SIZE];
		Arrays.fill(this.memory, false);

		this.lastMemoryWrite = -1;

		this.setStateCode(StateCode.ZERO);

		this.isBeliefValid = false;

	}

	/**
	 * Set the motor speed. Values other than (0, 0) are effective only if this
	 * agent is allowed to move
	 * 
	 * @param speedM1 the speed of the first motor
	 * @param speedM2 the speed of the second motor
	 */
	protected void setMotors(int speedM1, int speedM2) {
		if ((speedM1 == 0 && speedM2 == 0)
				|| (canMove && speedM1 > -255 && speedM1 < 255 && speedM2 > -255 && speedM2 < 255))
			robot.setMotors(speedM1, speedM2);
	}

	/**
	 * Write an observation of the world in the memory
	 * 
	 * @param cellRead
	 *            the boolean value read in this point in space
	 */
	protected void writeToMemory(boolean cellRead) {
		lastMemoryWrite = (lastMemoryWrite + 1) % AgentParameters.MEMORY_SIZE;

		// setting flag when the memory is full for the first time
		if (lastMemoryWrite == AgentParameters.MEMORY_SIZE - 1)
			isBeliefValid = true;

		synchronized (this.memory) {
			memory[lastMemoryWrite] = cellRead;
		}
	}

	/*
	 * Checks if the action must continue (only effective if acting). If the
	 * estimation on the state of the world is under the target threshold, stop.
	 * If the estimation on the state of the world minus the cells cleaned on
	 * this step is over the target threshold, keep acting. Otherwise, not all
	 * the swarm must keep acting (less than c cells are necessary to reach the
	 * target) so a stochastical decision is made.
	 */
	private void actingContinuationDecision() {
		if (isActionInProgress) {
			/* Rate of cells the swarm will clean */
			c = (AgentParameters.ACTIVATION_RATE * AgentParameters.SWARM_DENSITY * d);

			if (d < AgentParameters.TARGET_THRESHOLD) {
				this.debugMsg("Stopping action from continuation decision");
				stopAction();
				d = -1;
			} else if (d - c > AgentParameters.TARGET_THRESHOLD) {
				this.debugMsg("Keep acting deterministically from continuation decision");
				d = (float) (d - c);
			} else if (random.nextFloat() < (d - AgentParameters.TARGET_THRESHOLD) / c) {
				this.debugMsg("Keep acting stochastically from continuation decision");
				d = (float) ((float) (d - c) * ((d - AgentParameters.TARGET_THRESHOLD) / c));
			} else {
				this.debugMsg("Stop acting stochastically from continuation decision");
				stopAction();
				d = -1;
			}
			this.debugMsg("d = " + d);
		}
	}

	/**
	 * Adds a belief to the belief table, if not present or present with an
	 * older time stamp
	 * 
	 * @param otherAgentID
	 *            the ID of the agent that originated the belief
	 * @param timeStamp
	 *            the time the belief was created
	 * @param belief
	 *            the value of the belief
	 */
	protected void addBelief(Integer otherAgentID, Integer timeStamp, Float belief) {
		if (belief < 0 || belief > 1)
			return;

		if (!beliefTable.containsKey(otherAgentID)) {
			// inserting if not present
			beliefTable.put(otherAgentID, new AgentBelief(timeStamp, belief));
		} else if (beliefTable.get(otherAgentID).getTimeStamp() < timeStamp) {
			// inserting if present with an older timestamp
			beliefTable.put(otherAgentID, new AgentBelief(timeStamp, belief));
		}
	}

	/**
	 * Playing a sound as no cleaning capabilities are present. Also calls
	 * actingContinuationDecision() to check if the action should continue
	 */
	protected void action() {
		/* Middle C for half a second */
		this.robot.playNote(256, 500);
		actingContinuationDecision();
	}

	/**
	 * Check the belief table for distributed knowledge, stopping or starting
	 * the action if necessary
	 */
	protected void checkDistKnowledge() {
		double accumulator = 0;

		/* Adding the known agents' beliefs */
		Integer[] IDs = beliefTable.keySet().toArray(new Integer[0]);
		for (int beliefIndex = 0; beliefIndex < IDs.length; beliefIndex++) {
			accumulator += beliefTable.get(IDs[beliefIndex]).getBelief();
		}

		/* Assuming a neutral value for the unknown agents' belief */
		accumulator += AgentParameters.NEUTRAL_BELIEF_DEGREE * (AgentParameters.SWARM_SIZE - IDs.length);

		/* Averaging */
		double swarmBelief = accumulator / AgentParameters.SWARM_SIZE;

		/* Starting/stopping action accordingly */
		if (!isActionInProgress && swarmBelief > AgentParameters.PHI_BELIEF_THRESHOLD) {
			this.debugMsg("Starting action from table");
			startAction();
			d = (float) swarmBelief;
			this.debugMsg("d = " + d);
			setStateCode(StateCode.ONE);
		} else if (isActionInProgress && swarmBelief < AgentParameters.NOT_PHI_BELIEF_THRESHOLD) {
			this.debugMsg("Stopping action from table");
			stopAction();
			setStateCode(StateCode.ONE);
			d = -1;
		}
	}

	/**
	 * Broadcast the data passed as parameter
	 * 
	 * @param data
	 *            the data to broadcast
	 */
	protected void broadcast(byte[] data) throws TimeoutException, XBeeException {
		if (isBroadcasting)
			xBee.sendBroadcastData(data);
	}

	/**
	 * @return true if this agent is allowed to move, false otherwise
	 */
	public boolean canMove() {
		return canMove;
	}

	/**
	 * Starts motion
	 */
	public void startMotion() {
		if (!canMove) {
			canMove = true;
			/* Starting movement in background */
			Thread motionThread = new Thread(new MotionControl(this));
			motionThread.setDaemon(true);
			motionThread.start();
		}
	}

	/**
	 * Stop motion
	 */
	protected void stopMotion() {
		/* This stops MotionControl.run() and thus kills the thread */
		canMove = false;
		setMotors(0, 0);
	}

	/**
	 * @return true if this agent is broadcasting data, false otherwise
	 */
	public boolean isBroadcasting() {
		return isBroadcasting;
	}

	/**
	 * Starts broadcasting data
	 */
	protected void startBroadcasting() {
		if (!isBroadcasting) {
			isBroadcasting = true;
			/* Starting broadcasting in background */
			Thread broadcastThread = new Thread(new MessageSender(this));
			broadcastThread.setDaemon(true);
			broadcastThread.start();
		}
	}

	/**
	 * Stop broadcasting data
	 */
	protected void stopBroadcasting() {
		/* This stops MessageSender.run() and thus kills the thread */
		isBroadcasting = false;
	}

	/* Start the action and communicates to the supervisor  */
	private void startAction() {
		this.isActionInProgress = true;
		/* Communicate on the socket that the action is starting */
		if (this.socket != null && this.socket.isConnected()) {
			try {
				this.out.writeObject(new ActionState(this.getId(), 1));
				this.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		this.robot.writeLCDLine("CLEANING", 0);
		this.debugMsg("ACTION STARTED");
	}

	/* Stop the action and communicates to the supervisor  */
	private void stopAction() {
		/* Communicate on the socket that the action is ending */
		if (this.socket != null && this.socket.isConnected()) {
			this.isActionInProgress = false;
			try {
				this.out.writeObject(new ActionState(this.getId(), 0));
				this.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		this.robot.writeLCDLine("NOT CLEANING", 0);
		this.debugMsg("ACTION ENDED");
	}

	/**
	 * Set the next action state. Will have effect only when the agent is in
	 * state ZERO. If the agent is in state ONE, when it will be back to state
	 * ZERO the last value passed to this method will be set as the action state
	 * 
	 * @param actionState the desired next action state
	 */
	protected void setNextActionState(boolean actionState) {
		this.nextActionState = actionState;
	}

	/**
	 * @return the state code of this agent
	 */
	public StateCode getStateCode() {
		return stateCode;
	}

	/**
	 * @return true if at least MEMORY_SIZE observations have been made from
	 *         this agent
	 */
	public boolean isBeliefValid() {
		return isBeliefValid;
	}

	/**
	 * @return the belief table of this agent
	 */
	protected Map<Integer, AgentBelief> getBeliefTable() {
		return beliefTable;
	}

	/**
	 * @return the action state of this agent
	 */
	protected boolean isActionInProgress() {
		return isActionInProgress;
	}

	/**
	 * Increase the internal seconds counter of this agent
	 */
	private void timeIncrease() {
		time++;
	}

	/**
	 * @return the ID of this agent
	 */
	public Integer getId() {
		return ID;
	}
	
	/**
	 * @return the robot this agent is attached to
	 * */
	protected JMirtoRobot getRobot() {
		return robot;
	}

	/**
	 * Set the estimation of the state of the world d
	 * 
	 * @param d the estimation of the state of the world
	 * */
	protected void setD(float d) {
		this.d = d;
	}

	/**
	 * @return the estimation of the state of the world d
	 * */
	public float getD() {
		return d;
	}

	/*
	 * Stops the agent execution
	 */
	private void shutDown() {
		System.out.println("SHUTTING DOWN...");

		stopMotion();
		setMotors(0, 0);
		stopBroadcasting();
		xBee.close();
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				this.debugMsg(e.getMessage());
			}
		}

		System.out.println("...NOW");
	}

	/*
	 * Sets the agent's state code, also setting the action broadcast timer if
	 * the passed code is StateCode.ONE
	 */
	private void setStateCode(StateCode code) {
		this.stateCode = code;
		if (stateCode == StateCode.ONE) {
			setActionBroadcastTime();
		}
	}

	/* Records the time of action broadcasting start */
	private void setActionBroadcastTime() {
		this.actionBroadcastTime = this.time;
	}

	/* Returns the internal nanoTime() in seconds */
	private int secondsTime() {
		return (int) (System.nanoTime() / 1000000000);
	}

	/**
	 * @return this agent internal time step
	 */
	public int getTime() {
		return time;
	}

	/* Entry point to the application */
	public static void main(String args[]) throws XBeeException {
		/* Creating the agent */
		Agent thisAgent = new Agent(new AgentParameters());
		
		/* Recording the initial time */
		long realTime = thisAgent.secondsTime();

		/* Starting motion and broadcast */
		thisAgent.startMotion();
		thisAgent.startBroadcasting();

		/*
		 * To orderly shutdown if the program is closed (will not work if the
		 * JVM is closed first)
		 */
		Runtime.getRuntime().addShutdownHook(new Thread("agentShutdownHook") {

			@Override
			public void run() {
				thisAgent.shutDown();
			}
		});

		while (true) {
			/* Increasing time and debug printing*/
			if (thisAgent.secondsTime() > realTime) {
				thisAgent.timeIncrease();
				realTime = thisAgent.secondsTime();

				thisAgent.debugMsg("\n");
				thisAgent.debugMsg("Agent " + thisAgent.ID + " - Belief Table:");

				for (Integer beliefID : thisAgent.beliefTable.keySet())
					thisAgent.debugMsg(beliefID + " " + thisAgent.beliefTable.get(beliefID).getBelief());

				thisAgent.debugMsg(thisAgent.isActionInProgress ? "ACTING" : "NOT ACTING");
			}

			/* Adding the local belief to the table */
			synchronized (thisAgent.memory) {
				float localBelief = 0;
				for (boolean observation : thisAgent.memory) {
					if (observation)
						localBelief++;
				}
				localBelief = localBelief / AgentParameters.MEMORY_SIZE;
				thisAgent.beliefTable.put(thisAgent.ID, new AgentBelief(thisAgent.time, localBelief));
			}

			/* Stopping action broadcast after ACTION_BROADCAST_TIME seconds */
			if (thisAgent.stateCode == StateCode.ONE
					&& thisAgent.time - thisAgent.actionBroadcastTime > AgentParameters.ACTION_BROADCAST_TIME) {
				thisAgent.setStateCode(StateCode.ZERO);
				thisAgent.actionBroadcastTime = -1;
			}

			/* Setting action state from messages */
			if (thisAgent.stateCode == StateCode.ZERO && thisAgent.nextActionState != null) {
				if (thisAgent.nextActionState != thisAgent.isActionInProgress) {

					if (thisAgent.nextActionState) {
						thisAgent.debugMsg("Starting action from code 1 message");
						thisAgent.startAction();
					} else {
						thisAgent.stopAction();
						thisAgent.debugMsg("Stopping action from code 1 message");
					}
					thisAgent.setStateCode(StateCode.ONE);
					thisAgent.debugMsg("d = " + thisAgent.getD());
				}
				thisAgent.nextActionState = null;
			}

			/*
			 * if state code = 0, checkDistKnowledge. Can change
			 * isActionInProgress directly
			 */
			if (thisAgent.stateCode == StateCode.ZERO) {
				thisAgent.checkDistKnowledge();
			}
		}
	}

	/** To write debug messages */
	public void debugMsg(String msg) {
		if (debug)
			System.out.println(msg);
	}

	/** To write xbee debug messages */
	public void debugXBEEmsg(String msg) {
		if (debugXBEE)
			System.out.println(msg);
	}
}
