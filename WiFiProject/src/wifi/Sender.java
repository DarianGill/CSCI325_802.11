package wifi;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import java.util.Random;


public class Sender implements Runnable {


	private State theState;
	private RF theRF;
	private ArrayBlockingQueue<Packet> packets;
	private ArrayBlockingQueue<Packet> acks;
	private HashMap<Short, Integer> seqs;
	private Packet packToSend;
	private Random rand;
	private boolean DEBUG;

	enum State{
		WAITING, HASDATA, BACKOFF, ACKWAIT		//add more cases here as we get there
	}

	//take in a queue and manipulate that queue in the send method, will that hold for this to pull from??
	public Sender(RF theRF, ArrayBlockingQueue<Packet> packets, ArrayBlockingQueue<Packet> acks) {///take in RF so it can send, a queue that will be manipulated w/ data, what else?
		this.packets = packets;
		this.acks = acks;
		this.theRF = theRF;
		this.theState = State.WAITING;
		this.seqs = new  HashMap<>();
		rand = new Random();
		DEBUG = true;
	}


	private int setWaitTime(int resets) {//be careful about when to call this and actually get a new wait time
		//int expand = RF.aCWMin;
		//need aCWMin < something*slotTime < aCWMax
		int multiplier = resets*2;
		int max = (RF.aCWmin*multiplier)+1;
		//should start at CWMin and double until hits CWMAX
		if(max>RF.aCWmax) {
			max = RF.aCWmax;
		}
		return (rand.nextInt(max)/RF.aSlotTime);	//returning number of slots to wait
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
		int timeoutAt = 0;

		//some kind of looping mechanism here??
		while(true) {
			try {
				switch(theState) {	//the state is an integer corresponding to the case to enter
				case WAITING:
					//waiting for packet
					if(packets.peek() != null) {	//need to see if channel is busy right here to see if sending after difs
						used = this.theRF.inUse();	//see if channel is busy at get data
						packToSend = packets.poll();

						theState = State.HASDATA;		//used has been set to see if we were busy when first tried
						if(DEBUG) System.out.println("Got a packet to send...");
						try {
							Thread.sleep(difs);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}	
					}
					break;
				case HASDATA:	//has packet and waited DIFS, check if idle and if idle when started
					//just to see the format
					if(used||this.theRF.inUse()) {
						if(DEBUG) System.out.println("Gonna have to do some waiting...");
						//do some exponential backoff type stuff
						numWaitSlots = setWaitTime(resets);//for each waitSlot, sleep a slot time
						theState = State.BACKOFF;
					}else {
						if(DEBUG) System.out.println("Gonna send, fingers crossed...");
						this.theRF.transmit(packToSend.getPacket());
						timeoutAt = (int) (System.currentTimeMillis()+	RF.aSIFSTime + RF.aSlotTime + difs);
						theState = State.ACKWAIT;		//eventually want to have a loop where once the network gets busy we wait DIFS and then count down
					}
					break;
				case BACKOFF:
					if(DEBUG) System.out.println("I'm being patient, waiting my collision window...");
					for(int i = 0;i<numWaitSlots;i++) {
						if(this.theRF.inUse()) {//if busy pause and wait difs
							Thread.sleep(difs);
							i--;	//didn't wait a slot this iteration so don't break the loop early
						}else {
							Thread.sleep(RF.aSlotTime);
						}
						//otherwise count down 1 by 1 slots

						//at the end, send if idle, if busy then cut the window back down
					}//if we exit the loop then we've waited all our slots and been cautious with difs and such
					if(this.theRF.inUse()) {//if we've counted down and its still used then we cut our window
						resets = 1;
						numWaitSlots = setWaitTime(resets);
						if(DEBUG) System.out.println("Jeez its busy I'll cut my window down");
					}else {
						this.theRF.transmit(packToSend.getPacket());//end if its idle after all our waiting
						timeoutAt = (int) (System.currentTimeMillis()+	RF.aSIFSTime + RF.aSlotTime + difs);			//will eventually determine this real value in checkpoint 4 //how long in millis to wait to timeout; //SIFS+ACKtransmissionTime+RF.aSlotTime
						theState = State.ACKWAIT;
						if(DEBUG) System.out.println("Sending, wish me luck");
					}
					break;
					//what to do if we waited backoff and still busy, GO BACK TO THE BEGINNNING WAIT DIFS ONLY AND TRY IT
					//check if its empty while waiting backoff??		//should implement pausing and count down slot by slot (have method for this)
				case ACKWAIT://see if we get an ack back for what we finally sent, if we don't then we need to increase exp backoff
					if(acks.isEmpty() && System.currentTimeMillis()>=timeoutAt){
						if(resets+1==RF.dot11RetryLimit) {
							theState = State.WAITING;
							if(DEBUG) System.out.println("We tried too many times, better luck next time");
						}else {
							//set retransmission bit
							packToSend.setResend();
							resets++;
							numWaitSlots = setWaitTime(resets);
							theState = State.BACKOFF;//check to see if we've maxed our retries??
							if(DEBUG) System.out.println("Timed out waiting for an ACK, but we'll try again");
						}
						//see if we've waited our max time to expect an ack
						//if we have then numWaitSlots = setWaitTime(resets+1) and go to BACKOFF
						//is there a max number of retries??

					}else {	//cool we got acked for the thing we wanted to 
						if(acks.peek().getSeq() == packToSend.getSeq()&&acks.peek().getSrcAddr() == packToSend.getDestAddr()) {
							acks.take();
							theState = State.WAITING;
							if(DEBUG) System.out.println("YAY we got an ACK, see ya soon");
						}else {
							while(!acks.isEmpty()) {
								if(acks.peek().getSeq() == packToSend.getSeq()&&acks.peek().getSrcAddr() == packToSend.getDestAddr()) {
									acks.take();
									theState = State.WAITING;
									if(DEBUG) System.out.println("YAY we got an ACK, see ya soon");	//looking for our ack if theres a few in the queue
									break;
								}else {
									acks.take();			//clearing the queue
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
				Thread.sleep(20);			//wait some after each loop so we're not busy waiting
			} 
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}

