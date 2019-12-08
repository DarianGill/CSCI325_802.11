package wifi;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import java.util.HashMap;
import java.io.PrintWriter;
import java.util.zip.CRC32; 

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

	private static final short BCAST = -1;

	private PrintWriter output;
	private short ourMAC;
	private RF rf;
	private ArrayBlockingQueue<Packet> acks;
	private ArrayBlockingQueue<Transmission> trans;
	private HashMap<Short, Short> seqs;
	private HashMap<Short, Short> bcast;
	private int[] control;

	/**
	 * Constructor for the Receiver object.
	 * @param id	this is the "MAC address"
	 * @param rf	this is the RF layer the Receiver is using
	 */
	public Receiver(RF rf, short ourMAC, ArrayBlockingQueue<Packet> acks, ArrayBlockingQueue<Transmission> trans, PrintWriter output, int[] control) {
		this.ourMAC = ourMAC;
		this.rf = rf;
		this.acks = acks;
		this.trans = trans;
		this.seqs = new  HashMap<>();
		this.bcast = new HashMap<>();
		this.output = output;
		this.control = control;
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
			try {
				if (control[1] == -1) output.println("Receive has blocked, awaiting data");
				byte[] incoming = rf.receive();
				Packet newPacket = new Packet(incoming);

				// Verify Checksum
				CRC32 c = new CRC32();
				c.update(newPacket.getPacket(), 0, (newPacket.getPacket().length-4));	  // Calculates checksum from control, addresses, and data
				if (newPacket.getChksum() == (int) c.getValue()) {
					
					// HANDLING BEACONS
					if (newPacket.getType().equals("Beacon")){
						if (control[1] == -1) output.println("Beacon packet!");
					}

					// HANDLING DATA PACKETS
					else if (newPacket.getType().equals("Data")) {
						// for packets sent to our MAC specifically
						if ((newPacket.getDestAddr() == ourMAC)) {						
							sendAck(newPacket);

							// if MAC not in seqs, add MAC and seq#
							if (!seqs.containsKey(newPacket.getSrcAddr())) {
								seqs.put(newPacket.getSrcAddr(), newPacket.getSeq());
								Transmission newTrans = new Transmission(newPacket.getSrcAddr(), ourMAC, newPacket.getData());
								trans.add(newTrans);
								if (control[1] == -1) output.println("Queued incoming DATA packet with good CRC: " + newPacket.toString());
							}

							// if MAC is in seqs...
							else {
								// if we already have this packet from this MAC, do nothing
								if (seqs.get(newPacket.getSrcAddr()) == newPacket.getSeq()) {
									if (control[1] == -1) output.println("Already have that data.");
								}
								// if this is a new packet, add it to the trans queue and update seqs with new seq# for this MAC
								else {
									if ( seqs.get(newPacket.getSrcAddr()) != newPacket.getSeq() - 1) {
										output.print ("Packet received out of order");
										if (control[1] == -1) output.println("Packet out of order. Packet seq #: " + newPacket.getSeq());
									}
									seqs.replace(newPacket.getSrcAddr(), newPacket.getSeq());
									Transmission newTrans = new Transmission(newPacket.getSrcAddr(), ourMAC, newPacket.getData());
									trans.add(newTrans);
									if (control[1] == -1) output.println("Queued incoming DATA packet with good CRC: " + newPacket.toString());
								}
							}
						}

						// HANDLING PACKETS SENT TO BROADCAST ADDRESS
						if ((newPacket.getDestAddr() == BCAST)) {
							if (!bcast.containsKey(newPacket.getSrcAddr())) {
								bcast.put(newPacket.getSrcAddr(), newPacket.getSeq());
								Transmission newTrans = new Transmission(newPacket.getSrcAddr(), ourMAC, newPacket.getData());
								trans.add(newTrans);
								if (control[1] == -1) output.println("Queued incoming DATA packet with good CRC: " + newPacket.toString());							}

							// if MAC is in bcast...
							else {
								// if we already have this packet from this MAC, do nothing
								if (bcast.get(newPacket.getSrcAddr()) == newPacket.getSeq()) {
									if (control[1] == -1) output.println("Already have that broadcast.");
								}
								// if this is a new packet, add it to the trans queue and update bcast with new seq# for this MAC
								else {
									if (bcast.get(newPacket.getSrcAddr()) != newPacket.getSeq() - 1) {
										output.print ("Packet received out of order");
										if (control[1] == -1) output.println("Packet out of order. Packet seq #: " + newPacket.getSeq());
									}
									bcast.replace(newPacket.getSrcAddr(), newPacket.getSeq());
									Transmission newTrans = new Transmission(newPacket.getSrcAddr(), ourMAC, newPacket.getData());
									trans.add(newTrans);
									if (control[1] == -1) output.println("Queued incoming DATA packet with good CRC: " + newPacket.toString());								}
							}
						}
					}

					// HANDLING ACK PACKETS
					else if (newPacket.getType().contentEquals("ACK")){
						// if addressed to ourMAC
						if (newPacket.getDestAddr() == ourMAC) {
							acks.add(newPacket);
							if (control[1] == -1) output.println("Acknowledgment received.");
						}

						// packet not addressed to ourMAC
						else {
							if (control[1] == -1) output.println("Saw a packet...not for me...released it back into the wild.");
						}
					}
				}
				else {
					if (control[1] == -1) output.println("Sike! That's the wrong numbah! (wrong checksum)");
				}
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		}
	}


	public void sendAck(Packet newPacket) {
		if(!rf.inUse()) {
			try {
				Packet ack = new Packet(newPacket.getPacket());
				// set frame type to ACK
				ack.setType("ACK");

				// swap destAddr and srcAddr
				short temp = ack.getDestAddr();
				ack.setDestAddr(ack.getSrcAddr());
				ack.setSrcAddr(temp);

				// wait SIFS then send ack
				long number = rf.clock();
				Thread.sleep(50-(number%50));
				Thread.sleep(RF.aSIFSTime);
				Thread.sleep(50-(number%50));
				if (control[1] == -1) output.println("Idle waited until " + rf.clock());
				if (control[1] == -1) output.println("Sending ACK back to " + ack.getDestAddr() + ": " + ack.toString());
				rf.transmit(ack.getPacket());
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
