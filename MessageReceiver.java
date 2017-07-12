import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.models.XBeeMessage;

/**
 * This class represents a data receiver that wait for incoming messages, parses
 * their content and applies their effects to the agent.
 */
public class MessageReceiver implements IDataReceiveListener {

	/* The agent this receiver is attached to */
	private Agent thisAgent;

	/**
	 * Initialise the receiver by indicating the agent it is attached to
	 * 
	 * @param agent the agent this receiver is attached to
	 * */
	public MessageReceiver(Agent thisAgent) {
		this.thisAgent = thisAgent;
	}

	@Override
	public void dataReceived(XBeeMessage xbeeMessage) {
		String data = xbeeMessage.getDataString();
		
		/* The fields are separated with whitespaces */
		String[] fields = data.split(" ");

		Integer otherAgentID = null;
		Integer timeStamp = null;
		Float belief = null;

		if (fields[0].equals("0")) {
			/* Belief message */
			try {
				otherAgentID = Integer.parseInt(fields[1]);
				timeStamp = Integer.parseInt(fields[2]);
				belief = Float.parseFloat(fields[3]);

				/* Adding the belief */
				thisAgent.addBelief(otherAgentID, timeStamp, belief);
			} catch (NumberFormatException e) {
				System.out.println("Bad packet!");
			}
		} else if (fields[0].equals("1")) {
			/* Action state change message */
			thisAgent.debugMsg("Action change message received");
			try {
				Integer actionStateCode = Integer.parseInt(fields[1]);

				Float d = Float.parseFloat(fields[2]);

				/* Changing agent action state */
				if (actionStateCode.equals(1)) {
					thisAgent.setNextActionState(true);
					thisAgent.setD(d);
				} else if (actionStateCode.equals(0)) {
					thisAgent.setNextActionState(false);
					thisAgent.setD(-1);
				} else {
					System.out.println("Bad packet!");
				}

			} catch (NumberFormatException e) {
				System.out.println("Bad packet!");
			}
		} else {
			System.out.println("Bad packet!");
		}

	}
}