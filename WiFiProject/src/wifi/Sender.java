package wifi;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import java.util.Random;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


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
	private Integer stat;

	enum State{
		WAITING, HASDATA, BACKOFF, ACKWAIT		//add more cases here as we get there
	}

	//take in a queue and manipulate that queue in the send method, will that hold for this to pull from??
	public Sender(RF theRF, short ourMAC, ArrayBlockingQueue<Packet> packets, ArrayBlockingQueue<Packet> acks, PrintWriter output, int[] control, Long fudge, Integer stat) {///take in RF so it can send, a queue that will be manipulated w/ data, what else?
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


	private int setWaitTime(int resets) {//be careful about when to call this and actually get a new wait time
		int max = (int)(Math.pow(2,resets))-1;
		if(max>RF.aCWmax) {
			max = RF.aCWmax;
		}
		if(max<RF.aCWmin) {
			max = RF.aCWmin;
		}
		System.out.println("The max our window can be is "+max+" slots.");
		if (control[1] == -1) output.println("Starting collision window at [0..." + max + "]");
		if(control[2]!=0) {
			System.out.println("The actual window is "+max+" slots.");
			return max;
		}else {
			int actual = rand.nextInt(max);	//returning number of slots to wait
			System.out.println("The actual window is "+actual+" slots.");
			return actual;
		}
	}

	
	private void waitToBoundary() {
		try {
			long number = theRF.clock();
			Thread.sleep(50-(number%50));
			//System.out.println("This is the boundary: "+ theRF.clock()+"this is when we got called " +number+ " this is the amt we waited "+(50-(number%50)));
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
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
	

	@Override
	public void run() {
		// TODO Auto-generated method stub
		//loop through switch statement to determine where in flowchart we are, do different stuff in each section of the switch statement
		//call packet class and make packet
		//take in new data somehow
		//eventually send the data
		int difs = RF.aSIFSTime + (2*RF.aSlotTime);
		boolean used = true;
		int resets = 1;
		int numWaitSlots = 0;
		long timeoutAt = 0;
		long startTime = 0;
		long endTime = 0;
		long lastBeacon = 0;

		//some kind of looping mechanism here??
		while(true) {
			try {
				switch(theState) {	//the state is an integer corresponding to the case to enter
				case WAITING:
					//waiting for packet
					if(theRF.clock()-lastBeacon>=(control[3]*1000)) {
						// do beacon stuff
						// make a beacon with our clock+processing time as the data
						byte[] bytes = longToBytes(theRF.clock());	//NEED TO ADD PROCESSING TIME HERE
						packToSend = new Packet("Beacon", false, (short)0, (short)-1, this.ourMAC, bytes, 18);//new beacon packet with clock+processing as data
						
						theState = State.HASDATA;		//used has been set to see if we were busy when first tried
						if(control[1] == -1) output.println("Sending a beacon...");
						waitToBoundary();
						//System.out.println("This is the boundary: "+ theRF.clock());
						try {
							Thread.sleep(difs);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						waitToBoundary();

					}else {
						if(packets.peek() != null) {	//need to see if channel is busy right here to see if sending after difs
							used = this.theRF.inUse();	//see if channel is busy at get data
							packToSend = packets.poll();

							//Check Seq Num
							//if dest already used, update seq num to what is in hashmap + 1 and update hashmap
							if (seqs.containsKey(packToSend.getDestAddr())) {
								packToSend.setSeq((short) (seqs.get(packToSend.getDestAddr())+1));
								seqs.replace(packToSend.getDestAddr(), (short) (seqs.get(packToSend.getDestAddr())+1)); //Wrap Around?
							}
							//else put new dest addr into hashmap and pair with default seq num (0)
							else {
								seqs.put(packToSend.getDestAddr(), packToSend.getSeq());
							}

							theState = State.HASDATA;		//used has been set to see if we were busy when first tried
							if(control[1] == -1) output.println("Got a packet to send...");
							waitToBoundary();
							//System.out.println("This is the boundary: "+ theRF.clock());
							try {
								Thread.sleep(difs);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							waitToBoundary();
							//System.out.println("This is the boundary: "+ theRF.clock());
						}
					}
					break;
				case HASDATA:	//has packet and waited DIFS, check if idle and if idle when started
					//just to see the format
					if(used||this.theRF.inUse()) {
						if(control[1] == -1) output.println("Gonna have to do some waiting...");
						//do some exponential backoff type stuff
						numWaitSlots = setWaitTime(resets);//for each waitSlot, sleep a slot time
						theState = State.BACKOFF;
					}else {
						this.theRF.transmit(packToSend.getPacket());
						if(control[1] == -1) output.println("Transmitting DATA after DIFS+SLOTs wait at " + theRF.clock());
						if(packToSend.getDestAddr()==-1) {
							theState = State.WAITING;
							// if(control[1] == -1) output.println("Sending, wish me luck... I spy a broadcast packet");
						}else {
							startTime = System.currentTimeMillis();
							timeoutAt = (theRF.clock()+	RF.aSIFSTime + 6*RF.aSlotTime);
							// System.out.println("Timeout amount: " + timeoutAt);
							theState = State.ACKWAIT;		//eventually want to have a loop where once the network gets busy we wait DIFS and then count down
							if(control[1] == -1) output.println("Moving to ACKWAIT after sending DATA");
						}
					}
					break;
				case BACKOFF:
					if(control[1] == -1) output.println("I'm being patient, waiting my collision window...");
					for(int i = 0;i<numWaitSlots;i++) {
						if(this.theRF.inUse()) {//if busy pause and wait difs
							waitToBoundary();
							//System.out.println("This is the boundary: "+ theRF.clock());
							Thread.sleep(difs);
							waitToBoundary();
							//System.out.println("This is the boundary: "+ theRF.clock());
							i--;	//didn't wait a slot this iteration so don't break the loop early
						}else {
							waitToBoundary();
							//System.out.println("This is the boundary: "+ theRF.clock());
							Thread.sleep(RF.aSlotTime);
							waitToBoundary();
							//System.out.println("This is the boundary: "+ theRF.clock());
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
							theState = State.WAITING;
							if(control[1] == -1) output.println("Sending, wish me luck... I spy a broadcast packet");
						}else {
							timeoutAt = (theRF.clock() +	RF.aSIFSTime + 6*RF.aSlotTime);			//will eventually determine this real value in checkpoint 4 //how long in millis to wait to timeout; //SIFS+ACKtransmissionTime+RF.aSlotTime
							theState = State.ACKWAIT;
						}
						if(control[1] == -1) output.println("Sending, wish me luck");
					}
					break;
					//what to do if we waited backoff and still busy, GO BACK TO THE BEGINNNING WAIT DIFS ONLY AND TRY IT
					//check if its empty while waiting backoff??		//should implement pausing and count down slot by slot (have method for this)
				case ACKWAIT://see if we get an ack back for what we finally sent, if we don't then we need to increase exp backoff
					//System.out.println("Timeout amount: " + timeoutAt + "\tSystem time: " + theRF.clock());
					//System.out.println("Acks size: " + acks.toString());
					if(acks.isEmpty() && theRF.clock()>=timeoutAt){
						if(resets-1>=RF.dot11RetryLimit) {
							theState = State.WAITING;
							if(control[1] == -1) output.println("Moving to WAITING after exceeding retry limit");
						}else {
							//set retransmission bit
							packToSend.setResend();
							resets++;
							numWaitSlots = setWaitTime(resets);
							theState = State.BACKOFF;//check to see if we've maxed our retries??
							if(control[1] == -1) output.println("Ack timer expired at " + theRF.clock());
						}
						//see if we've waited our max time to expect an ack
						//if we have then numWaitSlots = setWaitTime(resets+1) and go to BACKOFF
						//is there a max number of retries??

					}else {	//cool we got acked for the thing we wanted to
						if (!acks.isEmpty()) {
							if(acks.peek().getSeq() == packToSend.getSeq()&&acks.peek().getSrcAddr() == packToSend.getDestAddr()) {
								// endTime = System.currentTimeMillis();
								// if(control[1] == -1) output.println("Packet seq#: " + packToSend.getSeq());
								// if(control[1] == -1) output.println("ACK seq#: " + acks.peek().getSeq());
								// if(control[1] == -1) output.println("SEQ#: Hashmap " + seqs.toString());
								acks.take();
								theState = State.WAITING;
								// if(control[1] == -1) output.println("Transmit time: " + ((endTime - startTime)/RF.aSlotTime) + " slots.");
								if(control[1] == -1) output.println("Got a valid ACK: " + acks.toString());
		
							}else {
								while(!acks.isEmpty()) {
									if(acks.peek().getSeq() == packToSend.getSeq()&&acks.peek().getSrcAddr() == packToSend.getDestAddr()) {
										acks.take();
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
					//resets++; //if we don't get an ack back in the timeout time
					//}

					//increment resends	//only time backoff is increased is if we don't hear back
				}

			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(5);			//wait some after each loop so we're not busy waiting
			}
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
