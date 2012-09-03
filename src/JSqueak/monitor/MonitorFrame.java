package JSqueak.monitor;

import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class MonitorFrame extends JFrame implements Monitor {
	JLabel jlbCurrentMessage = null;
	JTextArea jtAreaOutput;

	public MonitorFrame() throws HeadlessException {
		jlbCurrentMessage = new JLabel("--current message--");
		jtAreaOutput = new JTextArea(35, 30);
		JScrollPane scrollPane = new JScrollPane(jtAreaOutput,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		GridBagLayout gridBag = new GridBagLayout();
		Container contentPane = getContentPane();
		contentPane.setLayout(gridBag);
		GridBagConstraints gridCons1 = new GridBagConstraints();
		gridCons1.gridwidth = GridBagConstraints.REMAINDER;
		gridCons1.fill = GridBagConstraints.HORIZONTAL;
		contentPane.add(jlbCurrentMessage, gridCons1);
		GridBagConstraints gridCons2 = new GridBagConstraints();
		gridCons2.weightx = 1.0;
		gridCons2.weighty = 1.0;
		contentPane.add(scrollPane, gridCons2);
		this.setSize(380, 650);
		pack();
		setVisible(true);
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
		jlbCurrentMessage.setText(message);
		jtAreaOutput.append(message+"\n");
	}

}
