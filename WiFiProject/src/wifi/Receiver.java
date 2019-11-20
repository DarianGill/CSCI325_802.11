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
	private PriorityQueue<byte[]> dataQ;
	private ArrayBlockingQueue<Transmission> trans;
	private HashMap<Short, Integer> seqs;
	
	/**
	 * Constructor for the Receiver object.
	 * @param id	this is the "MAC address"
	 * @param rf	this is the RF layer the Receiver is using
	 */
	public Receiver(RF rf, short id, ArrayBlockingQueue<Packet> acks, ArrayBlockingQueue<Transmission> trans, HashMap<Short, Integer> seqs) {
		this.id = id;
		this.rf = rf;
		this.acks = acks;
		this.trans = trans;
		this.seqs = seqs;
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
		System.out.println("Receiver " + id + " is initialized. Tell me your secrets!");
		
		while(true) {
			try {
				byte[] incoming = rf.receive();
				Packet newPacket = new Packet(incoming); 
				System.out.println("Packet received from MAC " + newPacket.getSrcAddr());

				// check the frame type
				if (newPacket.getType().equals("Data")) {  //if it's data, extract data and store in dataQ
					System.out.println("Your secret is safe with me.");
					Transmission newTrans = new Transmission(newPacket.getSrcAddr(), id, newPacket.getData());
					trans.add(newTrans);
				}
				else if (newPacket.getType().equals("ACK")) {  //if its an ack, add to Acks ABQ
					acks.add(newPacket);
					System.out.println("Acknowledgment received.");
				}
			}
			catch(Exception ex) {
				ex.printStackTrace();
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
