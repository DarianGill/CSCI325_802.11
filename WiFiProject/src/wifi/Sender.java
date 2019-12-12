package wifi;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import java.util.Random;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds packets. Sends them...
 * 
 * Okay but more specifically, there is a state diagram that is looped through which attempts to recreate the 
 * 802.11 spec. There are 4 states in the switch (one for acquiring data, deciding if we need to do additional waiting,
 * backoff assignment and waiting, and ack wait). This runnable method handles the send() calls in link layer
 * which come from up above.
 * 
 * @author Josh McMillan
 *
 */
public class Sender implements Runnable {


	private State theState;
	private RF theRF;
	private ArrayBlockingQueue<Packet> packets;
	private ArrayBlockingQueue<Packet> acks;
	private HashMap<Short, Short> seqs;
	private Packet packToSend;
	private Random rand;
	private PrintWriter output;
	private int[] control;
	private Long fudge;
	private short ourMAC;
	private int[] stat;

	enum State{
		WAITING, HASDATA, BACKOFF, ACKWAIT		
	}

	
	public Sender(RF theRF, short ourMAC, ArrayBlockingQueue<Packet> packets, ArrayBlockingQueue<Packet> acks, PrintWriter output, int[] control, Long fudge, int[] stat) {///take in RF so it can send, a queue that will be manipulated w/ data, what else?
		this.packets = packets;
		this.acks = acks;
		this.theRF = theRF;
		this.theState = State.WAITING;
		this.seqs = new  HashMap<>();
		rand = new Random();
		this.output = output;
		this.control = control;
		this.fudge = fudge;
		this.ourMAC = ourMAC;
		this.stat = stat;
	}
	
	/**
	 * A method to return what we really consider the clock time, accounts for how far ahead how beacons have been
	 * 
	 * @return a long representing the clock time
	 */
	private long getClock() {
		return theRF.clock() + fudge;
	}


	/**
	 * A method to set the wait time in slots for a packet's collision window. There is an option to force the 
	 * collision window to be the max size it can be.
	 * 
	 * @param resets how many times we've tried to send already
	 * @return the number of slots we should wait
	 */
	private int setWaitTime(int resets) {
		int max = (int)(Math.pow(2,resets))-1;
		if(max>RF.aCWmax) {
			max = RF.aCWmax;
		}
		if(max<RF.aCWmin) {
			max = RF.aCWmin;
		}
		if (control[1] == -1) output.println("Starting collision window at [0..." + max + "]");
		if(control[2]!=0) {
			if (control[1] == -1) output.println("The choosen window is "+max+" slots.");
			return max;
		}else {
			int actual = rand.nextInt(max);	
			if (control[1] == -1) output.println("The choosen window is "+actual+" slots.");
			return actual;
		}
	}

	
	/**
	 * This method waits until a 50ms boundary, to be called any time we need to get to a boundary.
	 */
	private void waitToBoundary() {
		try {
			long number = getClock();
			Thread.sleep(50-(number%50));
		} catch (InterruptedException e) {
			this.stat[0] = 2;
			e.printStackTrace();
		}
		
	}
	
	/**
	* The lontToBytes method uses the ByteBuffer class to break a long into its 8 respective
	* bytes and returns them as an array of bytes. In run, we use it to generate the time
	* bytes for a packet.
	* 
	* @param l - a long
	* @return bytes - an array of the 8 bytes that made up the long
	*/
	private byte[] longToBytes(long l) {
		ByteBuffer bytes = ByteBuffer.allocate(8);
		bytes.order(ByteOrder.BIG_ENDIAN); // most significant byte placed at [0]
		bytes.putLong(l);
		return bytes.array();
	}
	

	/**
	 * The big boy. Does all of the waiting and sending and ack waiting. All of the fun things that
	 * make an 802.11 live in here. The switch statement encompasses the cases at which we can arrive 
	 * based on a situation. From every state there are other options to loop through to execute the sending
	 * of a packet over an RF layer.
	 * 
	 */
	@Override
	public void run() {

		int difs = RF.aSIFSTime + (2*RF.aSlotTime);
		boolean used = true;
		int resets = 1;
		int numWaitSlots = 0;
		long timeoutAt = 0;
		long startTime = 0;
		long endTime = 0;
		long lastBeacon = 0;
		long idleTime = 0;

		while(true) {
			try {
				switch(theState) {	
				case WAITING:
					//waiting for packet
					if(control[3] > 0 && getClock()-lastBeacon>=(control[3]*1000)) {
						
						// make a beacon with our clock+processing time as the data
						lastBeacon = getClock();
						used = theRF.inUse();
						byte[] bytes = longToBytes(lastBeacon + 2400);	
						packToSend = new Packet("Beacon", false, (short)0, (short)-1, this.ourMAC, bytes, 18);//new beacon packet with clock+processing as data
						
						theState = State.HASDATA;		//used has been set to see if we were busy when first tried
						if(control[1] == -1) output.println("Sending a beacon...");
						waitToBoundary();
						try {
							Thread.sleep(difs);
						} catch (InterruptedException e) {
							this.stat[0] = 2;
							e.printStackTrace();
						}
						waitToBoundary();

					}else {
						if(packets.peek() != null) {	
							used = this.theRF.inUse();	
							packToSend = packets.poll();

							//Check Seq Num
							//if dest already used, update seq num to what is in hashmap + 1 and update hashmap
							if (seqs.containsKey(packToSend.getDestAddr())) {
								packToSend.setSeq((short) (seqs.get(packToSend.getDestAddr())+1));
								seqs.replace(packToSend.getDestAddr(), (short) (seqs.get(packToSend.getDestAddr())+1)); 
							}
							//else put new dest addr into hashmap and pair with default seq num (0)
							else {
								seqs.put(packToSend.getDestAddr(), packToSend.getSeq());
							}

							theState = State.HASDATA;		
							waitToBoundary();
							try {
								Thread.sleep(difs);
							} catch (InterruptedException e) {
								this.stat[0] = 2;
								e.printStackTrace();
							}
							waitToBoundary();
						}
					}
					break;
				case HASDATA:	//has packet and waited DIFS, check if idle and if idle when started
					if (control[1] == -1) output.println("Idle waited for " + (getClock() - idleTime));
					resets = 1;
					if(used||this.theRF.inUse()) {
						if(control[1] == -1) output.println("Gonna have to do some waiting...");
						numWaitSlots = setWaitTime(resets); //for each waitSlot, sleep a slot time
						theState = State.BACKOFF;
					}else {
						this.theRF.transmit(packToSend.getPacket());
						endTime = getClock();
						if(control[1] == -1) output.println("Transmitting DATA after DIFS+SLOTs wait at " + getClock());
						if(packToSend.getDestAddr()==-1) {
							idleTime = getClock();
							theState = State.WAITING;
						}else {
							timeoutAt = (getClock()+	RF.aSIFSTime + 6*RF.aSlotTime);
							theState = State.ACKWAIT;
							if(control[1] == -1) output.println("Moving to ACKWAIT after sending DATA");
						}
					}
					break;
				case BACKOFF:
					if(control[1] == -1) output.println("I'm being patient, waiting my collision window...");
					for(int i = 0;i<numWaitSlots;i++) {
						if(this.theRF.inUse()) {
							waitToBoundary();
							Thread.sleep(difs);
							waitToBoundary();
							i--;	//didn't wait a slot this iteration so don't break the loop early
						}else {
							waitToBoundary();
							Thread.sleep(RF.aSlotTime);
							waitToBoundary();
						}
						//otherwise count down 1 by 1 slots

						//at the end, send if idle, if busy then cut the window back down
					}//if we exit the loop then we've waited all our slots and been cautious with difs and such
					if(this.theRF.inUse()) {//if we've counted down and its still used then we cut our window
						resets = 1;
						numWaitSlots = setWaitTime(resets);
						if(control[1] == -1) output.println("Jeez its busy I'll cut my window down");
					}else {
						this.theRF.transmit(packToSend.getPacket());//end if its idle after all our waiting
						if(packToSend.getDestAddr()==-1) {
							idleTime = getClock();
							theState = State.WAITING;
							if(control[1] == -1) output.println("Sending, wish me luck... I spy a broadcast packet");
						}else {
							timeoutAt = (getClock() +	RF.aSIFSTime + 6*RF.aSlotTime);			
							theState = State.ACKWAIT;
						}
						if(control[1] == -1) output.println("Sending, wish me luck");
					}
					break;
					
					
				case ACKWAIT://see if we get an ack back for what we finally sent, if we don't then we need to increase exp backoff
					if(acks.isEmpty() && getClock()>=timeoutAt){
						if(resets-1>=RF.dot11RetryLimit) {
							this.stat[0] = 5; // Last packet failed to deliver successfully
							idleTime = getClock();
							theState = State.WAITING;
							if(control[1] == -1) output.println("Moving to WAITING after exceeding retry limit");
						}else {
							//set retransmission bit
							packToSend.setResend();
							resets++;
							numWaitSlots = setWaitTime(resets);
							theState = State.BACKOFF;//check to see if we've maxed our retries??
							if(control[1] == -1) output.println("Ack timer expired at " + getClock());
						}
						//see if we've waited our max time to expect an ack
						//if we have then numWaitSlots = setWaitTime(resets+1) and go to BACKOFF

					}else {	//cool we got acked for the thing we wanted to
						if (!acks.isEmpty()) {
							if(acks.peek().getSeq() == packToSend.getSeq()&&acks.peek().getSrcAddr() == packToSend.getDestAddr()) {
								acks.take();
								this.stat[0] = 4; // ACK was received so packet was delivered successfully
								idleTime = getClock();
								theState = State.WAITING;
								if(control[1] == -1) output.println("Got a valid ACK: " + acks.toString());
		
							}else {
								while(!acks.isEmpty()) {
									if(acks.peek().getSeq() == packToSend.getSeq()&&acks.peek().getSrcAddr() == packToSend.getDestAddr()) {
										acks.take();
										this.stat[0] = 4; // ACK was received so packet was delivered successfully
										idleTime = getClock();
										theState = State.WAITING;
										if(control[1] == -1) output.println("Got a valid ACK: " + acks.toString());	//looking for our ack if theres a few in the queue
										break;
									}else {
										acks.take();			//clearing the queue
									}
								}
							}
						}
					}
					break;
				}

			}
			catch (Exception e) {
				this.stat[0] = 2;
				e.printStackTrace();
			}

			try {
				Thread.sleep(5);
			}
			catch (InterruptedException e) {
				this.stat[0] = 2;
				e.printStackTrace();
			}
		}
	}
}
