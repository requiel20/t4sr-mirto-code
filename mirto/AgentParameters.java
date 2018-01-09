/**
 * This class contains several final and static parameters that should be common
 * to every agent deployed
 */
public class AgentParameters {
	/** The rate of cells with phi over the which phi is considered true for the world */
	public static final double PHI_BELIEF_THRESHOLD = 0.75;

	/** The rate of cells with phi under the which not phi is considered true for the world */
	public static final double NOT_PHI_BELIEF_THRESHOLD = 0.55;

	/** The assumed belief for unknown agents */
	public static final double NEUTRAL_BELIEF_DEGREE = (PHI_BELIEF_THRESHOLD + NOT_PHI_BELIEF_THRESHOLD) / 2;

	/** The number of agents deployed */
	public static final double SWARM_SIZE = 5;

	/**
	 * The time steps to broadcast an action state change for
	 */
	public static final int ACTION_BROADCAST_TIME = 15;

	/** The number of cells observation to remember */
	public static final int MEMORY_SIZE = 10;

	/** The ratio of agents that are expected to start acting */
	public static final double ACTIVATION_RATE = 1;

	/** The ratio of agents per world cell */
	public static final double SWARM_DENSITY = 0.05;

	/** The rate of cells with phi that should remain after an action */
	public static final double TARGET_THRESHOLD = NEUTRAL_BELIEF_DEGREE;

	/** The port number to communicate the acting state */
	public static final int PORT_NUMBER = 1025;

	/** The IP address of the supervisor machine */
	public static final String SUPERVISOR_IP = "10.3.219.138";
}
