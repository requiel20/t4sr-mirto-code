/**
 * This class represents a robotic controller that will interact with the robot
 * controlled by the agent, providing motion and sensor reading capabilities
 */
public class MotionControl implements Runnable {

	/* Must be <255 && >0 */
	private final int SPEED = 150;

	/* Must be <255 && >0 */
	private final int TURNING_SPEED = 150;

	/* Time steps to go forward */
	private final double FORWARD_TIME = 3;

	/* Time to spin when it has to change direction */
	private final double TURNING_TIME = 0.75;

	private final double ACTION_INTERVAL = 1;

	/* Time to spin when it hits a wall */
	private final double WALL_TURNING_TIME = 0.25;

	/* Infrared sensor value for black */
	private final int BLACK_IR_VALUE = 130;

	/* The agent that is moved by this motion control */
	private Agent thisAgent;

	/**
	 * Initialise the controller by indicating the agent it is attached to
	 * 
	 * @param agent
	 *            the agent this controller is attached to
	 */
	public MotionControl(Agent thisAgent) {
		this.thisAgent = thisAgent;
	}

	@Override
	public void run() {
		while (thisAgent.canMove()) {
			randomWalkStep();
		}
		thisAgent.setMotors(0, 0);
	}

	/**
	 * This method will:
	 * <ul>
	 * <li>move forward for FORWARD_TIME seconds, turning randomly at the end,
	 * or before if it hit something</li>
	 * <li>get a sample of the world state, simulating the assertion of the
	 * state of a cell</li>
	 * <li>if isActionInProgress() returns true, it will signal the agent to
	 * clean the cell it is standing on</li>
	 * </ul>
	 */
	private void randomWalkStep() {
		/* Going forward */
		thisAgent.setMotors(SPEED, -SPEED);
		readProperty();
		long stepStartTime = System.nanoTime();
		long lastActionTime = System.nanoTime();
		while (System.nanoTime() < stepStartTime + nano(FORWARD_TIME)) {
			if (System.nanoTime() > lastActionTime + nano(ACTION_INTERVAL)) {
				readProperty();
				lastActionTime = System.nanoTime();
			}
			/* If it hits something, turn */
			if (thisAgent.getRobot().isPressed(0) || thisAgent.getRobot().isPressed(1)) {
				turn(TURNING_SPEED, WALL_TURNING_TIME);
				thisAgent.setMotors(SPEED, -SPEED);
				thisAgent.debugMsg("Wall hit");
			}
		}

		/* Turn after a forward step */
		turn(TURNING_SPEED, TURNING_TIME);

		if (thisAgent.isActionInProgress()) {
			thisAgent.action();
		}
	}

	/* Add an observation of the environment to memory */
	private void readProperty() {
		/* Read cell value */
		double cellValue = thisAgent.getRobot().getIR(0);
		cellValue += thisAgent.getRobot().getIR(1);
		cellValue += thisAgent.getRobot().getIR(2);

		cellValue = cellValue / 3;

		/* Add it to memory */
		thisAgent.writeToMemory(cellValue > BLACK_IR_VALUE ? true : false);
	}

	/* Turning */
	private void turn(int turningSpeed, double seconds) {
		thisAgent.setMotors(turningSpeed, turningSpeed);
		long time = System.nanoTime();
		while (System.nanoTime() < time + nano(TURNING_TIME)) {
			/* Wait */
		}

	}

	private long nano(double seconds) {
		return (long) (seconds * 1000000000);
	}
}
