import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * This class represents an application that can receive and show information
 * over the action state of the agents of the swarm.
 */
public class ActionMonitor implements WindowListener {

	private JFrame frame;
	private JPanel mainPanel;
	private ServerSocket serverSocket;
	private boolean closing;

	/**
	 * Launching the application.
	 */
	public static void main(String[] args) {
		try {
			ActionMonitor actionMonitor = new ActionMonitor();
			actionMonitor.frame.setVisible(true);

			while (!actionMonitor.closing) {
				System.out.println("Waiting for socket");
				Socket socket = actionMonitor.serverSocket.accept();
				/* Starting a thread for every agent's incoming socket */
				Thread socketThread = new Thread(actionMonitor.new ActionStateSocketHandler(socket, actionMonitor));
				socketThread.start();
				System.out.println("Socket opened");
			}
		} catch (SocketTimeoutException timeoutException) {
			// do nothing
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creating the application
	 */
	public ActionMonitor() {
		try {
			serverSocket = new ServerSocket(AgentParameters.PORT_NUMBER);
			
			// serverSocket.setSoTimeout(500);
		} catch (IOException e) {
			e.printStackTrace();
		}

		closing = false;

		initialize();
	}

	/**
	 * Initialise the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setTitle("Action monitor");
		frame.setBounds(0, 0, 575, 450);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.addWindowListener(this);

		/* Main panel */
		mainPanel = new JPanel();
		mainPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));

		JScrollPane scrollPane = new JScrollPane(mainPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		/* Title label */
		JLabel lblMirtoActionState = new JLabel("trust4swarmrobotics action state monitor");
		lblMirtoActionState.setHorizontalAlignment(SwingConstants.CENTER);

		/* Legend */
		JPanel legendPanel = new JPanel();
		legendPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JLabel greenSquare = new JLabel("");
		greenSquare.setBorder(BorderFactory.createMatteBorder(10, 10, 10, 10, Color.GREEN));
		legendPanel.add(greenSquare);
		legendPanel.add(new JLabel("= acting"));

		JLabel yellowSquare = new JLabel("");
		yellowSquare.setBorder(BorderFactory.createMatteBorder(10, 10, 10, 10, Color.YELLOW));
		legendPanel.add(yellowSquare);
		legendPanel.add(new JLabel("= not acting"));

		/* Adding everything to the main panel */
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
		frame.getContentPane().add(lblMirtoActionState, BorderLayout.NORTH);
		frame.getContentPane().add(legendPanel, BorderLayout.SOUTH);
	}

	@Override
	public void windowOpened(WindowEvent e) {
		// FIXME Auto-generated method stub

	}

	@Override
	public void windowClosing(WindowEvent e) {
		closing = true;
	}

	@Override
	public void windowClosed(WindowEvent e) {
		try {
			this.serverSocket.close();
		} catch (IOException e1) {
			// FIXME Auto-generated catch block
			e1.printStackTrace();
		}
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// FIXME Auto-generated method stub

	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// FIXME Auto-generated method stub

	}

	@Override
	public void windowActivated(WindowEvent e) {
		// FIXME Auto-generated method stub

	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// FIXME Auto-generated method stub

	}

	/* Class to format a label to show the action state of an agent */
	private class ActionStateLabel extends JLabel {

		private static final long serialVersionUID = 4268040963114955326L;

		/* To hold the agent's ID */
		private int ID;

		public ActionStateLabel(ActionState state) {
			super();
			this.ID = state.getAgentID();

			this.setText("" + state.getAgentID());

			/* Setting the colour based on the action state of the agent */
			if (state.getState() == 0)
				this.setBackground(Color.YELLOW);
			else if (state.getState() == 1)
				this.setBackground(Color.GREEN);
			else
				this.setBackground(Color.BLACK);

			this.setOpaque(true);
		}

		public int getID() {
			return ID;
		}

		@Override
		public void setBackground(Color bg) {
			super.setBackground(bg);
			this.setOpaque(true);
			this.setBorder(BorderFactory.createMatteBorder(15, 30, 15, 30, bg));
		}
	}

	/* Runnable class to handle each agent's socket */
	private class ActionStateSocketHandler implements Runnable {

		Socket socket;
		ActionMonitor actionMonitor;

		/* Assigning the socket to this handler */
		public ActionStateSocketHandler(Socket socket, ActionMonitor actionMonitor) {
			this.socket = socket;
			this.actionMonitor = actionMonitor;
		}

		@Override
		public void run() {
			ObjectInputStream in = null;
			try {
				in = new ObjectInputStream(socket.getInputStream());
				System.out.println("Inputstream opened");

				/* Until closing signal */
				while (!closing) {
					ActionState state;
					/* Waiting for an action state */
					state = (ActionState) in.readObject();

					System.out.println("Action state received");

					/* Only one update at time */
					synchronized (actionMonitor.mainPanel.getTreeLock()) {

						Component[] components = actionMonitor.mainPanel.getComponents();

						/* Searching for the label to edit based on the action state*/
						boolean foundID = false;
						for (int i = 0; i < components.length && foundID == false; i++) {
							System.out.println("Searching components for id " + state.getAgentID() + ", " + (i + 1)
									+ " on " + components.length);
							ActionStateLabel label = (ActionStateLabel) components[i];
							System.out.println("label " + label.getID());
							if (label.getID() == state.getAgentID()) {
								if (state.getState() == 0)
									label.setBackground(Color.YELLOW);
								else if (state.getState() == 1)
									label.setBackground(Color.GREEN);
								else
									label.setBackground(Color.BLACK);
								foundID = true;
								System.out.println("Component found");
							}
						}

						/* If there is no label, create a new one */
						if (!foundID) {
							ActionStateLabel label = actionMonitor.new ActionStateLabel(state);

							actionMonitor.mainPanel.add(label);
							System.out.println("Label " + label.getText() + " added");
						}

						actionMonitor.mainPanel.validate();
					}
				}
			} catch (EOFException eofe) {
				// do nothing
			} catch (ClassNotFoundException | IOException e) {
				e.printStackTrace();
			}
		}
	}
}
