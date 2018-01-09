import java.text.DecimalFormat;

import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.XBeeException;

/**
 * This runnable class, represents a message sender that broadcast the right
 * content depending on the agent state:
 * <ul>
 * <li>state 0 : the belief table, one belief at time, in the form:
 * <ul>
 * <li>"0 ID timeStamp belief"</li>
 * </ul>
 * </li>
 * <li>state 1 : the current action state and world state estimation, in the
 * form:
 * <ul>
 * <li>"1 actionState worldStateEstimate"</li>
 * </ul>
 * </li> The decimal values have 2 decimal digits
 */
public class MessageSender implements Runnable {

	private Agent thisAgent;

	private DecimalFormat df = new DecimalFormat("#0.00");

	/**
	 * Initialise the sender by indicating the agent it is attached to
	 * 
	 * @param agent
	 *            the agent this sender is attached to
	 */
	public MessageSender(Agent agent) {
		this.thisAgent = agent;
	}

	@Override
	public void run() {
		while (thisAgent.isBroadcasting()) {
			if (thisAgent.getStateCode() == Agent.StateCode.ZERO) {
				/*
				 * As long as the agent remain in state code ZERO, send its
				 * belief
				 */
				while (thisAgent.getStateCode() == Agent.StateCode.ZERO) {
					/* Retrieving the belief from the belief table */
					if (thisAgent.getBeliefTable().containsKey(thisAgent.getId())) {
						AgentBelief belief;
						if (thisAgent.isBeliefValid())
							belief = thisAgent.getBeliefTable().get(thisAgent.getId());
						else
							belief = new AgentBelief(thisAgent.getTime(),
									(float) AgentParameters.NEUTRAL_BELIEF_DEGREE);

						/* Sending the belief */
						try {
							String message = ("0" + " " + thisAgent.getId() + " " + belief.getTimeStamp() + " "
									+ df.format(belief.getBelief()));
							thisAgent.debugXBEEmsg("Broadcasting " + message);
							thisAgent.broadcast(message.getBytes());
						} catch (TimeoutException e) {
							thisAgent.debugXBEEmsg("TIMEOUT while sending xbee broadcast message");
						} catch (XBeeException e) {
							thisAgent.debugXBEEmsg(e.getMessage());
						}
					}
				}
			} else {
				/*
				 * Send this agent action state and the estimation of the world
				 * state d
				 */
				try {
					String message = (("1" + " "
							+ (thisAgent.isActionInProgress() ? ("1 " + df.format(thisAgent.getD())) : ("0 -1"))));
					thisAgent.debugXBEEmsg("Broadcasting " + message);
					thisAgent.broadcast(message.getBytes());
				} catch (TimeoutException e) {
					thisAgent.debugXBEEmsg("TIMEOUT while sending xbee broadcast message");
				} catch (XBeeException e) {
					thisAgent.debugXBEEmsg(e.getMessage());
				}
			}
		}
	}
}
