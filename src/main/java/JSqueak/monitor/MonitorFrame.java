package jsqueak.monitor;

import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class MonitorFrame extends JFrame implements Monitor {
	JLabel lbStatus;
	JTextArea taLog;
	JButton btDoSomething;

	public MonitorFrame() throws HeadlessException {
		lbStatus = new JLabel("--current status--");
		taLog = new JTextArea(35, 30);
		btDoSomething = new JButton("Do something...");
		JScrollPane scrollPane = new JScrollPane(taLog,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		GridBagLayout gridBag = new GridBagLayout();
		Container contentPane = getContentPane();
		contentPane.setLayout(gridBag);
		GridBagConstraints gridCons1 = new GridBagConstraints();
		gridCons1.gridwidth = GridBagConstraints.REMAINDER;
		gridCons1.fill = GridBagConstraints.HORIZONTAL;
		contentPane.add(btDoSomething, gridCons1);
		contentPane.add(lbStatus, gridCons1);
		GridBagConstraints gridCons2 = new GridBagConstraints();
		gridCons2.weightx = 1.0;
		gridCons2.weighty = 1.0;
		contentPane.add(scrollPane, gridCons2);
		this.setSize(380, 650);
		pack();
		setVisible(true);
		btDoSomething.setActionCommand("do_something");
		//btDoSomething.addActionListener(this);
	}

	public void actionPerformed(ActionEvent e) {
	    if ("do_something".equals(e.getActionCommand())) {
	        this.logMessage("Oops! I did it.");
	    }
	} 

	
	public MonitorFrame(GraphicsConfiguration gc) {
		super(gc);
	}

	public MonitorFrame(String title) throws HeadlessException {
		super(title);
	}

	public MonitorFrame(String title, GraphicsConfiguration gc) {
		super(title, gc);
	}

	@Override
	public void logMessage(String message) {
		taLog.append(message+"\n");
	}

	@Override
	public void setStatus(String status) {
		lbStatus.setText(status);
	}

}
