package wifi;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import java.util.*;

/**
 * Receiver will monitor the network for incoming data, which it will remove from it's packet and store in 
 * an ArrayBlockingQueue until the data is requested by the client layer. This class is also responsible for
 * handling ACKs after receiving data.
 * 
 * @author Kyle Muir
 * @version 11.3.19
 *
 */
public class Receiver implements Runnable {
	
	private short id;
	private RF rf;
	private ArrayBlockingQueue<Packet> acks;
	
	//TODO: Create a local byte[] that holds just the data from each packet that comes in
	private PriorityQueue<byte[]> dataArray;
	
	
	/**
	 * Constructor for the Receiver object.
	 * @param id	this is the "MAC address"
	 * @param rf	this is the RF layer the Receiver is using
	 */
	public Receiver(RF rf, short id, ArrayBlockingQueue<Packet> acks) {
		this.id = id;
		this.rf = rf;
		this.acks = acks;
	}

	
	/**
	 * Checks the RF layer to see if there is any data waiting to be received. Since the RF layer
	 * stores packets as byte arrays, this method will convert the byte array into a Packet object
	 * (as defined by the Packet class), then check the frame type to determine what type of packet 
	 * it is and handle each one according to its type. If it is a data packet, it will be stored 
	 * in the Packets ArrayBlockingQueue and if it is an ack, then it will be stored in the 
	 * Acks ArrayBlockingQueue.
	 * 
	 */
	@Override
	public void run() {
		while(true) {
			System.out.println("Receiver " + id + " is initialized. Tell me your secrets!");
			try {
				byte[] incoming = rf.receive();  
				Packet newPacket = new Packet(incoming); 
				
				// check the frame type
				if (newPacket.getType().equals("Data")) {  //if its data, add packet to Packets ABQ
					dataArray.add(extractData(newPacket));
				}
				else if (newPacket.getType().equals("ACK")) {  //if its an ack, add to Acks ABQ
					acks.add(newPacket);
				}
			}
			catch(Exception ex) {
				System.err.println("There was an issue receiving the packet.");
			}
		}
	}
	
	/**
	 * Takes a transmitted packet and extracts the data from it.
	 * 
	 * @param pack	the packet containing the needed data
	 * @return		the data pulled from the packet, stored as a byte array
	 */
	public static byte[] extractData(Packet pack) {
		byte[] data = pack.getData();
		return data;
	}
	
}
