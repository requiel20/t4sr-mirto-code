/**
 * This class represents an agent's local belief, enclosing a time stamp and a
 * belief value
 */
public class AgentBelief {
	private int timeStamp;
	private float belief;

	/**
	 * Initialise the belief
	 * 
	 * @param timeStamp
	 *            the time the belief was created
	 * @param belief
	 *            the value of the belief
	 */
	public AgentBelief(int timeStamp, float belief) {
		this.timeStamp = timeStamp;
		this.belief = belief;
	}

	/**
	 * @return the time the belief was created
	 */
	public int getTimeStamp() {
		return timeStamp;
	}

	/**
	 * @return the value of the belief
	 */
	public double getBelief() {
		return belief;
	}
}
