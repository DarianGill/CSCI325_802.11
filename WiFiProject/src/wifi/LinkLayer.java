package wifi;
import java.io.PrintWriter;

import rf.RF;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.HashMap;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards, Darian Gill, Josh McMillan, Kyle Muir
 */
public class LinkLayer implements Dot11Interface 
{
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	private ArrayBlockingQueue<Packet> packets;
	private ArrayBlockingQueue<Packet> acks;
	private ArrayBlockingQueue<Transmission> trans;
	private int[] stat;
	private int[] control;
	private Long fudge;

	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.stat = new int[] {0};
		if(ourMAC == -1) {
			System.exit(1);
		}
		else { 
			this.ourMAC = ourMAC;
		}
		this.output = output;   
		try {
			theRF = new RF(null, null);
		}
		catch (Exception e) {
			this.stat[0] = 3;  // RF layer failed to initialize
			e.printStackTrace();
		}
		this.packets = new ArrayBlockingQueue(10);
		this.acks = new ArrayBlockingQueue(10);
		this.trans = new ArrayBlockingQueue<Transmission>(10);
		this.control = new int[] {0, 0, 0, 0};
		this.fudge = 0L;

		Receiver rec = new Receiver(theRF, ourMAC, acks, trans, output, control, fudge, stat);
		new Thread(rec).start();

		Sender send = new Sender(theRF, ourMAC, this.packets, this.acks, output, control, fudge, stat);
		new Thread(send).start();

		this.stat[0] = 1; // 802.11~ Initialized
		output.println("LinkLayer initialized with MAC address " + ourMAC);
		output.println("Send command 0 to see a list of supported commands");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		if (packets.size() >= 4) {
			stat[0] = 10; // Outgoing transmission rejected due to insufficient buffer space
			return 0;
		}
		else {
			output.println("LinkLayer: Sending "+len+" bytes to "+dest);
			Packet packet = new Packet("Data", false, (short)0, dest, this.ourMAC, data, len + 10); //adding 10 to len here is to make the length of the full packet vs. just the data length
			packets.add(packet);
			return len;
		}
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		int numBytes = 0;
		if (t == null) {
			stat[0] = 7; // Null pointer
		}
		else {
			if (trans.size() >= 4) {
				// stat = 10; // Incoming transmission rejected due to insufficient buffer space
				return 0;
			}
			else {
				while(true) {
					if (trans.peek() != null) {
						Transmission temp = trans.poll();
						t.setBuf(temp.getBuf());
						t.setDestAddr(temp.getDestAddr());
						t.setSourceAddr(temp.getSourceAddr());
						numBytes = t.getBuf().length;
						break;
					}
				}
			}
		}
		return numBytes;
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		return stat[0];
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		if (cmd > 3 || cmd < 0) {
			this.stat[0] = 9;  // illegal argument 
			return 0;
		}
		else {
 			control[cmd] = val;
 			int beaconTime = control[3];
 			if (cmd == 0) {
				output.println("--------------- Commands and Settings ---------------");
				output.println("Cmd #0: Display command options and current settings");
				output.println("Cmd #1: Set Debug. Currently at 0");
				output.println("        Use -1 for full debug output, 0 for no output");
				output.println("Cmd #2: Set slot selection method. Currently random");
				output.println("        Use 0 for random slot selection, any other value to use maxCW");
				output.println("Cmd #3: Set beacon interval. Currently at " + beaconTime + " seconds");
				output.println("        Value specifies seconds between the start of the beacons; -1 disables");
				output.println("-----------------------------------------------------");
			}
			return 0;
		}
	}	
}
