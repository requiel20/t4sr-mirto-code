import java.io.Serializable;

/**
 * This class represents an agent's action state and is used to communicate it
 * to a supervisor over a socket
 */
public class ActionState implements Serializable {

	private static final long serialVersionUID = -7565443204494509975L;

	private int agentID;
	private int state;

	/**
	 * Initialise an action state
	 * 
	 * @param agentID the ID of the agent this action state refers to
	 * @param state the action state value
	 * */
	public ActionState(int agentID, int state) {
		this.agentID = agentID;
		this.state = state;
	}

	/**
	 * @return the ID of the agent this action state refers to
	 * */
	public int getAgentID() {
		return agentID;
	}

	/**
	 * @return the action state value
	 * */
	public int getState() {
		return state;
	}

	@Override
	/**
	 * Equality is only based on the agent ID
	 * */
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + agentID;
		return result;
	}

	@Override
	/**
	 * Equality is only based on the agent ID
	 * */
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ActionState other = (ActionState) obj;
		if (agentID != other.agentID)
			return false;
		return true;
	}
}
