package wifi;
import java.io.PrintWriter;

import rf.RF;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.HashMap;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface 
{
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	private ArrayBlockingQueue<Packet> packets;
	private ArrayBlockingQueue<Packet> acks;
	private ArrayBlockingQueue<Transmission> trans;
	public int stat;
	
	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;      
		theRF = new RF(null, null);
		this.packets = new ArrayBlockingQueue(10);
		this.acks = new ArrayBlockingQueue(10);
		this.trans = new ArrayBlockingQueue<Transmission>(10);
		this.stat = 0;
		
		Receiver rec = new Receiver(theRF, ourMAC, acks, trans, output);
		new Thread(rec).start();
		
		Sender send = new Sender(theRF, this.packets, this.acks);
		new Thread(send).start();
		
		
		output.println("LinkLayer: Constructor ran.");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		if (packets.size() >= 4) {
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
		int numBytes;
		if (trans.size() >= 4) {
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
		return numBytes;
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		return stat;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command "+cmd+" with value "+val);
		return 0;
	}
	
}
