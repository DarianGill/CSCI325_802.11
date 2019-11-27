package wifi;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import java.util.*;
import java.io.PrintWriter;

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

	private static final boolean DEBUG = false;
	private static final short BCAST = -1;

	private PrintWriter output;
	private State theState;
	private short ourMAC;
	private RF rf;
	private ArrayBlockingQueue<Packet> acks;
	private ArrayBlockingQueue<Transmission> trans;
	private HashMap<Short, Short> seqs;

	enum State {
		DATA, ACK
	}

	/**
	 * Constructor for the Receiver object.
	 * @param id	this is the "MAC address"
	 * @param rf	this is the RF layer the Receiver is using
	 */
	public Receiver(RF rf, short ourMAC, ArrayBlockingQueue<Packet> acks, ArrayBlockingQueue<Transmission> trans, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.rf = rf;
		this.acks = acks;
		this.trans = trans;
		this.seqs = new  HashMap<>();
		this.theState = State.DATA;
		this.output = output;
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
		if (DEBUG) System.out.println("Receiver " + ourMAC + " is initialized. Tell me your secrets!");

		while(true) {
			try {
				byte[] incoming = rf.receive();
				Packet newPacket = new Packet(incoming);

				switch(theState) {
				case DATA: 
					if (DEBUG) System.out.println("Packet received from MAC " + newPacket.getSrcAddr());
					if (newPacket.getType().equals("ACK")) {	 // switch to ACK if it's an ACK packet
						theState = State.ACK;
					}
					else {

						//check if it's data and the destAddr is ourMAC or Broadcast
						if ((newPacket.getDestAddr() == ourMAC || newPacket.getDestAddr() == BCAST)) {

							// if MAC not in seqs, add MAC and seq#
							if (!seqs.containsKey(newPacket.getSrcAddr())) {
								seqs.put(newPacket.getSrcAddr(), newPacket.getSeq());
								Transmission newTrans = new Transmission(newPacket.getSrcAddr(), ourMAC, newPacket.getData());
								trans.add(newTrans);
								if (DEBUG) System.out.println(newPacket.getSrcAddr() + " added to HashMap with seq#: " + newPacket.getSeq());
							}

							// if MAC is in seqs...
							else {
								// if we already have this packet from this MAC, do nothing
								if (seqs.get(newPacket.getSrcAddr()) == newPacket.getSeq()) {
									if (DEBUG) System.out.println("Already have that data.");
								}
								// if this is a new packet, add it to the trans queue and update seqs with new seq# for this MAC
								else {
									if ( seqs.get(newPacket.getSrcAddr()) != newPacket.getSeq() - 1) {
										output.print ("Packet received out of order");
										if (DEBUG) System.out.println("Packet out of order. Packet seq #: " + newPacket.getSeq());
									}
									seqs.replace(newPacket.getSrcAddr(), newPacket.getSeq());
									Transmission newTrans = new Transmission(newPacket.getSrcAddr(), ourMAC, newPacket.getData());
									trans.add(newTrans);
									if (DEBUG) System.out.println("Updated " + newPacket.getSrcAddr() + "'s seq# to: " + newPacket.getSeq());
								}
							}
						}

						// only send ACK is packet was sent directly to ourMAC
						if (newPacket.getDestAddr() == ourMAC) {
							sendAck(newPacket);
						}
					}
					break;
					
				case ACK:
					// if addressed to ourMAC
					if (newPacket.getDestAddr() == ourMAC) {
						acks.add(newPacket);
						if (DEBUG) System.out.println("Acknowledgment received.");
					}

					// packet not addressed to ourMAC
					else {
						if (DEBUG) System.out.println("Saw a packet...not for me...released it back into the wild.");
					}
					theState = State.DATA;
					break;
				}
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		}
	}


	public void sendAck(Packet ack) {
		if(!rf.inUse()) {
			try {
				// set frame type to ACK
				ack.setType("ACK");

				// swap destAddr and srcAddr
				short temp = ack.getDestAddr();
				ack.setDestAddr(ack.getSrcAddr());
				ack.setSrcAddr(temp);

				// wait SIFS then send ack
				Thread.sleep(RF.aSIFSTime);
				rf.transmit(ack.getPacket());

				if (DEBUG) System.out.println("Sent ack to " + ack.getDestAddr());
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
