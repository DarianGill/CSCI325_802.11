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

	enum State{
		WAITING, HASDATA		//add more cases here as we get there
	}

	//take in a queue and manipulate that queue in the send method, will that hold for this to pull from??
	public Sender(RF theRF, ArrayBlockingQueue<Packet> packets, ArrayBlockingQueue<Packet> acks) {///take in RF so it can send, a queue that will be manipulated w/ data, what else?
		this.packets = packets;
		this.acks = acks;
		this.theRF = theRF;
		this.theState = State.WAITING;
		this.seqs = new  HashMap<>();
		rand = new Random();
	}

	private int setWaitTime(int resends) {
		int expand = RF.aCWMin;
		//need aCWMin < something*slotTime < aCWMax
		int multiplier = (resends*2)+1;
		expand += mulitplier*RF.aSlotTime
		if(expand>RF.aCWMax) {
			expand = RF.aCWMax;
		}
		
		return rand.nextInt(expand);
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

		//some kind of looping mechanism here??
		while(true) {
			try {
				switch(theState) {	//the state is an integer corresponding to the case to enter
				case WAITING:
					//waiting for packet
					if(packets.peek() != null) {	//need to see if channel is busy right here to see if sending after difs
						used = this.theRF.inUse();	//see if channel is busy at get data
						packToSend = packets.poll();
						
						theState = State.HASDATA;		//channel not busy when we first looked
						
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
						//do some exponential backoff type stuff
					}else {
						this.theRF.transmit(packToSend.getPacket());
						theState = State.WAITING;
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			Thread.sleep(20);			//wait sifs after each loop so we're not busy waiting
		} 
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

